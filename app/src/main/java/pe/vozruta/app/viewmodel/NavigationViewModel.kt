package pe.vozruta.app.viewmodel

import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.vozruta.app.maps.Destination
import pe.vozruta.app.maps.StreetRoute
import pe.vozruta.app.maps.StreetRouting
import pe.vozruta.app.maps.TravelMode
import pe.vozruta.app.maps.projectOnRoute
import pe.vozruta.app.model.Geo
import kotlin.math.ceil
import kotlin.math.max

data class LiveFix(val point: Geo, val accuracy: Float, val speed: Float, val atMillis: Long) {
    fun usable(now: Long = SystemClock.elapsedRealtime()) = accuracy in 0f..60f && now - atMillis in 0..18_000
}

data class NavigationState(
    val destination: Destination? = null,
    val mode: TravelMode = TravelMode.WALK,
    val tracking: Boolean = false,
    val fix: LiveFix? = null,
    val route: StreetRoute? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val remaining: Int? = null,
    val remainingMinutes: Int? = null,
    val instruction: String? = null,
    val maneuverDistance: Int? = null,
    val offRoute: Boolean = false,
    val arrived: Boolean = false,
    val spoken: Announcement? = null
)

class NavigationViewModel : ViewModel() {
    private val service = StreetRouting()
    private val mutable = MutableStateFlow(NavigationState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var requestedAt: Long? = null
    private var offRouteCount = 0
    private var announcedIndex = -1
    private var spokenId = 0L

    fun destination(value: Destination) {
        if (state.value.destination == value) return
        clearRoute()
        mutable.update { it.copy(destination = value) }
        calculateIfReady()
    }

    fun mode(value: TravelMode) {
        if (state.value.mode == value) return
        clearRoute()
        mutable.update { it.copy(mode = value) }
        calculateIfReady()
    }

    fun activate() {
        if (state.value.arrived) clearRoute()
        mutable.update { it.copy(tracking = true, message = null) }
        calculateIfReady()
    }

    fun stop() {
        clearRoute()
        mutable.update { it.copy(tracking = false, fix = null, spoken = null) }
    }

    fun notice(text: String) = mutable.update { it.copy(message = text) }

    fun invalidateFix() = mutable.update { it.copy(fix = null) }

    fun clearSpeech(id: Long) = mutable.update { if (it.spoken?.id == id) it.copy(spoken = null) else it }

    fun onLocation(location: Location) {
        if (!state.value.tracking) return
        val fix = LiveFix(Geo(location.latitude, location.longitude),
            if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
            if (location.hasSpeed()) location.speed else 0f, location.elapsedRealtimeNanos / 1_000_000L)
        val previous = state.value.fix
        if (previous != null && previous.atMillis >= fix.atMillis) return
        mutable.update { it.copy(fix = fix) }
        if (!fix.usable()) return
        val current = state.value
        if (current.route == null) {
            if (current.message == null || requestedAt == null) calculateIfReady()
        } else updateProgress(fix)
    }

    fun retry() = calculateIfReady(force = true)

    private fun clearRoute() {
        request?.cancel()
        requestedAt = null
        offRouteCount = 0
        announcedIndex = -1
        mutable.update { it.copy(route = null, loading = false, message = null, remaining = null,
            remainingMinutes = null, instruction = null, maneuverDistance = null, offRoute = false, arrived = false, spoken = null) }
    }

    private fun calculateIfReady(force: Boolean = false) {
        val current = state.value
        val destination = current.destination ?: return
        val fix = current.fix?.takeIf { it.usable() } ?: return
        if (!current.tracking || current.loading || current.arrived) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = requestedAt?.let { now - it }
        // One in-flight request. Leave time for several fixes before retrying or rerouting.
        if (elapsed != null && elapsed < if (force) 2_000 else 25_000) return
        if (fix.point.metersTo(destination.point) <= 20 && fix.accuracy <= 20) {
            finish()
            return
        }
        requestedAt = now
        mutable.update { it.copy(loading = true, message = null) }
        request = viewModelScope.launch {
            try {
                val route = withContext(Dispatchers.IO) { service.route(fix.point, destination.point, current.mode) }
                offRouteCount = 0
                announcedIndex = -1
                mutable.update { it.copy(route = route, loading = false, offRoute = false, message = null) }
                state.value.fix?.takeIf { it.usable() }?.let(::updateProgress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(loading = false, message = error.message ?: "No se pudo calcular la ruta.") }
            }
        }
    }

    private fun updateProgress(fix: LiveFix) {
        val current = state.value
        if (current.arrived) return
        val route = current.route ?: return
        val projection = projectOnRoute(fix.point, route.points) ?: return
        val progress = (projection.along / projection.length * route.meters).coerceIn(0.0, route.meters)
        val remaining = route.meters - progress
        val off = projection.distance > max(50.0, fix.accuracy * 1.6)
        offRouteCount = if (off) offRouteCount + 1 else 0
        if (off) {
            mutable.update { it.copy(offRoute = true, instruction = null, remaining = null, remainingMinutes = null, maneuverDistance = null) }
            if (offRouteCount >= 3) calculateIfReady()
            return
        }
        val endpoint = route.points.last()
        if (remaining < 35 && fix.accuracy <= 25 && fix.point.metersTo(endpoint) < 27) {
            finish()
            return
        }
        val sameStepTolerance = if (current.mode == TravelMode.WALK) 8 else 14
        val index = route.maneuvers.indexOfFirst { it.atMeters > progress + sameStepTolerance }
        val next = route.maneuvers.getOrNull(index)
        val ahead = next?.let { (it.atMeters - progress).toInt().coerceAtLeast(0) }
        val minutes = ceil(route.seconds * (remaining / route.meters.coerceAtLeast(1.0)) / 60).toInt()
        mutable.update { it.copy(offRoute = false, remaining = remaining.toInt(), remainingMinutes = minutes,
            instruction = next?.text ?: "Continúa hasta el punto de llegada", maneuverDistance = ahead) }
        val announceWithin = if (current.mode == TravelMode.WALK) 35 else max(75, (fix.speed * 7).toInt()).coerceAtMost(200)
        if (next != null && ahead != null && ahead <= announceWithin && index > announcedIndex) {
            announcedIndex = index
            speak("En unos $ahead metros. ${next.text}.")
        }
    }

    private fun speak(text: String) = mutable.update { it.copy(spoken = Announcement(++spokenId, text)) }

    private fun finish() {
        mutable.update { it.copy(arrived = true, tracking = false, loading = false, offRoute = false,
            instruction = "Has llegado al final del recorrido", remaining = 0, remainingMinutes = 0, maneuverDistance = null) }
        speak("Has llegado al final del recorrido.")
    }
}

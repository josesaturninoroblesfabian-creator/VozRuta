package pe.vozruta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import pe.vozruta.app.maps.Boarding
import pe.vozruta.app.maps.TransitPlan
import pe.vozruta.app.maps.TransitService
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.PilotTrip
import pe.vozruta.app.model.Profile
import pe.vozruta.app.model.Stop
import pe.vozruta.app.model.distanceLabel
import pe.vozruta.app.model.normalize
import pe.vozruta.app.model.pointAlong
import pe.vozruta.app.simulation3d.BusSimulation
import java.util.UUID
import kotlin.math.ceil

enum class Screen(val title: String) { HOME("Inicio"), TRIP("Viaje"), BUS("Bus 3D"), OPERATOR("Central") }
data class Announcement(val id: Long, val text: String)
data class Guide(val stop: Stop, val detail: String, val walking: TransitPlan? = null)
data class JourneyState(
    val screen: Screen = Screen.HOME,
    val profile: Profile = Profile.GENERAL,
    val location: Geo? = null,
    val trip: PilotTrip? = null,
    val guide: Guide? = null,
    val plans: List<TransitPlan> = emptyList(),
    val selected: Int = 0,
    val simulation: BusSimulation = BusSimulation(),
    val demoRequest: Boolean = false,
    val pending: Boolean = false,
    val live: Boarding? = null,
    val queue: List<Boarding> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val suggestedDestination: String? = null,
    val speech: Announcement? = null,
    val voiceEnabled: Boolean = true
)

class JourneyViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("vozruta", 0)
    private val service = TransitService()
    val connected get() = service.isConfigured
    val operatorConnected get() = service.operatorConfigured
    private val mutable = MutableStateFlow(JourneyState(
        profile = Profile.entries.firstOrNull { it.name == prefs.getString("profile", null) } ?: Profile.GENERAL,
        voiceEnabled = prefs.getBoolean("voice", true)
    ))
    val state = mutable.asStateFlow()
    private var speechId = 0L
    private var search: Job? = null
    private var riderPoll: Job? = null
    private var operatorPoll: Job? = null
    private var requestKey: String? = prefs.getString("requestKey", null)
    private var requestFingerprint: String? = prefs.getString("requestFingerprint", null)
    private var active = false
    private val roads by lazy {
        val json = JSONArray(application.assets.open("pilot-roads.json").bufferedReader().use { it.readText() })
        List(json.length()) { leg ->
            val points = json.getJSONArray(leg)
            List(points.length()) { i ->
                val coordinate = points.getJSONArray(i)
                Geo(coordinate.getDouble(0), coordinate.getDouble(1))
            }
        }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                val before = state.value
                if (!active || !before.simulation.running) continue
                val next = before.simulation.tick(0.1f)
                mutable.update { it.copy(simulation = next) }
                if (before.simulation.stage != next.stage && next.announcements) {
                    announce("Simulación. ${next.stage.messageFor(before.profile)}")
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) {
            if (connected && requestKey != null) watchBoarding()
            updateOperator()
        } else {
            riderPoll?.cancel()
            operatorPoll?.cancel()
        }
    }

    fun navigate(screen: Screen) {
        mutable.update { it.copy(screen = screen, message = null) }
        updateOperator()
    }

    fun clearMessage() = mutable.update { it.copy(message = null) }

    fun notice(message: String) = mutable.update { it.copy(message = message) }

    fun profile(profile: Profile) {
        prefs.edit().putString("profile", profile.name).apply()
        mutable.update { it.copy(profile = profile) }
    }

    fun voice(enabled: Boolean) {
        prefs.edit().putBoolean("voice", enabled).apply()
        mutable.update { it.copy(voiceEnabled = enabled, speech = null) }
    }

    fun announce(text: String) {
        if (state.value.voiceEnabled) mutable.update { it.copy(speech = Announcement(++speechId, text)) }
    }

    fun replay(text: String) = mutable.update { it.copy(speech = Announcement(++speechId, text)) }

    fun location(geo: Geo?) {
        mutable.update { it.copy(location = geo) }
        if (geo == null) notice("No se pudo obtener la ubicación. Puedes ingresar el punto de partida.")
    }

    fun command(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.contains("paradero") && normalized.contains("cercan")) return true
        val destination = Pilot.stop(text)
        if (destination != null) {
            pilot(destination)
        } else {
            val target = text.trim().replace(Regex("^(quiero ir a|llévame a|ir(?: a| al)?)\\s+", RegexOption.IGNORE_CASE), "")
            mutable.update { it.copy(suggestedDestination = target.take(220)) }
        }
        return false
    }

    fun clearSuggestedDestination() = mutable.update { it.copy(suggestedDestination = null) }

    fun pilot(destination: Stop, board: Stop? = null) {
        search?.cancel()
        val trip = Pilot.trip(destination, state.value.location, board)
        mutable.update {
            it.copy(screen = Screen.TRIP, trip = trip, guide = null, plans = emptyList(), selected = 0,
                loading = false, simulation = BusSimulation(announcements = it.simulation.announcements),
                demoRequest = false, message = null)
        }
        announce(describeTrip(trip))
    }

    fun describeTrip(trip: PilotTrip) = "Viaje de demostración en bus inclusivo. Sube en ${trip.board.name} a las ${trip.departure()}. Baja en ${trip.exit.name}. Tarifa de ejemplo ${trip.fare}."

    fun pilotLine(trip: PilotTrip?): List<Geo> {
        val stops = trip?.stops ?: Pilot.stops
        return stops.zipWithNext().flatMap { (a, b) ->
            val from = Pilot.stops.indexOf(a)
            val to = Pilot.stops.indexOf(b)
            val leg = roads[minOf(from, to)]
            if (from < to) leg else leg.asReversed()
        }
    }

    fun nearest(location: Geo) {
        search?.cancel()
        val stop = Pilot.nearest(location)
        if (stop == null) {
            mutable.update { it.copy(location = location, loading = false) }
            notice("Tu ubicación está fuera del corredor piloto. Busca transporte en «Explorar otras ciudades».")
            announce("Tu ubicación está fuera del corredor piloto.")
            return
        }
        val distance = location.distanceLabel(stop.geo)
        val direction = location.directionTo(stop.geo)
        val minutes = ceil(location.metersTo(stop.geo) / 65).toInt().coerceAtLeast(1)
        val detail = "Referencia: $distance hacia el $direction en línea recta. Unos $minutes minutos al ritmo de referencia, antes de añadir desvíos y cruces. Confirma las condiciones de la vía antes de avanzar."
        mutable.update {
            it.copy(location = location, screen = Screen.TRIP, trip = null, plans = emptyList(), guide = Guide(stop, detail),
                loading = connected, message = null)
        }
        announce("Referencia cercana: ${stop.name}. $detail")
        if (connected) search = viewModelScope.launch {
            try {
                val walking = withContext(Dispatchers.IO) { service.find(location.coordinate, stop.geo.coordinate, "WALK").firstOrNull() }
                mutable.update { it.copy(guide = Guide(stop, detail, walking)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                notice("No se recibió un recorrido peatonal. Puedes consultar el punto de referencia.")
            } finally {
                if (isActive) mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun global(origin: String, target: String) {
        search?.cancel()
        mutable.update {
            it.copy(screen = Screen.TRIP, trip = null, guide = null, plans = emptyList(), selected = 0,
                loading = true, message = null)
        }
        search = viewModelScope.launch {
            try {
                val plans = withContext(Dispatchers.IO) { service.find(origin, target) }
                mutable.update { it.copy(plans = plans) }
                if (plans.isEmpty()) {
                    notice("La fuente no encontró opciones de transporte público para este trayecto.")
                    announce("No se encontraron opciones de transporte público para este trayecto.")
                } else announce(describePlan(plans.first()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                notice(error.message ?: "No se pudo consultar el transporte.")
            } finally {
                if (isActive) mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun describePlan(plan: TransitPlan) = "Viaje de ${plan.minutes} minutos. ${plan.fare?.let { "Tarifa informada $it." } ?: "Tarifa sin datos."} " +
        plan.steps.joinToString(". ") { "${it.title}. ${it.detail}" }

    fun selectPlan(index: Int) {
        mutable.update { it.copy(selected = index) }
        announce(describePlan(state.value.plans[index]))
    }

    fun toggleSimulation() {
        mutable.update {
            val current = it.simulation
            val next = if (current.stage == BusStage.ARRIVED) BusSimulation(running = true, speed = current.speed, announcements = current.announcements)
                else current.copy(running = !current.running)
            it.copy(simulation = next)
        }
        val current = state.value
        if (current.simulation.running && current.simulation.announcements) {
            announce("Simulación. ${current.simulation.stage.messageFor(current.profile)}")
        }
    }

    fun simulationRamp() {
        mutable.update { it.copy(simulation = it.simulation.copy(stage = BusStage.RAMP, elapsed = 0f, running = true)) }
        if (state.value.simulation.announcements) announce("Simulación. ${BusStage.RAMP.message}")
    }

    fun simulationSpeed() = mutable.update { it.copy(simulation = it.simulation.copy(speed = if (it.simulation.speed == 1) 2 else 1)) }

    fun simulationVoice(enabled: Boolean) {
        mutable.update { it.copy(simulation = it.simulation.copy(announcements = enabled)) }
    }

    fun nextDemoStage() {
        val before = state.value
        val stage = before.simulation.stage
        val next = BusStage.entries[(stage.ordinal + 1) % BusStage.entries.size]
        mutable.update { it.copy(simulation = it.simulation.copy(stage = next, elapsed = 0f, running = false)) }
        if (before.simulation.announcements) announce("Simulación. ${next.messageFor(before.profile)}")
    }

    fun request(trip: PilotTrip) {
        val before = state.value
        if (before.pending) return
        if (!connected) {
            mutable.update { it.copy(demoRequest = true, screen = Screen.OPERATOR, message = null,
                simulation = it.simulation.copy(stage = BusStage.ROUTE, elapsed = 0f, running = false)) }
            announce("Solicitud de demostración registrada en la central local. ${before.profile.request}.")
            return
        }
        val time = trip.departure()
        val fingerprint = "${trip.board.id}|${trip.exit.id}|${before.profile.name}"
        if (requestKey != null && requestFingerprint != fingerprint && before.live?.stage != BusStage.ARRIVED) {
            notice("Ya hay una solicitud pendiente. Espera la actualización de la central antes de solicitar otro viaje.")
            return
        }
        val existing = requestKey
        val previous = before.live
        if (existing != null && previous != null && previous.stage != BusStage.ARRIVED) {
            notice("Esta solicitud ya está registrada en la central.")
            return
        }
        val id = existing?.takeUnless { before.live?.stage == BusStage.ARRIVED } ?: UUID.randomUUID().toString()
        requestKey = id
        requestFingerprint = fingerprint
        prefs.edit().putString("requestKey", id).putString("requestFingerprint", fingerprint).apply()
        mutable.update { it.copy(pending = true, message = null) }
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { service.request(id, trip, before.profile, time) }
                mutable.update { it.copy(live = item) }
                announce("La central recibió tu solicitud de abordaje en ${item.board}.")
                if (active) watchBoarding()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                notice("Sin confirmación de la central. Puedes reintentar la misma solicitud.")
            } finally {
                mutable.update { it.copy(pending = false) }
            }
        }
    }

    private fun watchBoarding() {
        riderPoll?.cancel()
        val id = requestKey ?: return
        riderPoll = viewModelScope.launch {
            while (isActive) {
                try {
                    val item = withContext(Dispatchers.IO) { service.status(id) }
                    val previous = state.value.live
                    if (previous != item) {
                        mutable.update { it.copy(live = item) }
                        if (previous?.stage != item.stage && item.stage != BusStage.ROUTE) {
                            announce("Aviso de la central de pruebas. ${item.stage.message}")
                        }
                    }
                    if (item.stage == BusStage.ARRIVED) break
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the last confirmed status during a brief disconnect.
                }
                delay(2_500)
            }
        }
    }

    private fun updateOperator() {
        operatorPoll?.cancel()
        if (!active || !operatorConnected || state.value.screen != Screen.OPERATOR) return
        operatorPoll = viewModelScope.launch {
            while (isActive) {
                try {
                    val queue = withContext(Dispatchers.IO) { service.queue() }
                    mutable.update { it.copy(queue = queue) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    notice("La central dejó de responder. Reintentando conexión.")
                }
                delay(2_500)
            }
        }
    }

    fun advance(item: Boarding) {
        if (item.stage == BusStage.ARRIVED || state.value.pending) return
        val stage = BusStage.entries[item.stage.ordinal + 1]
        mutable.update { it.copy(pending = true) }
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) { service.advance(item, stage) }
                mutable.update { it.copy(queue = it.queue.map { row -> if (row.id == updated.id) updated else row }) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                notice(error.message ?: "No se confirmó el cambio de estado.")
            } finally {
                mutable.update { it.copy(pending = false) }
            }
        }
    }
}

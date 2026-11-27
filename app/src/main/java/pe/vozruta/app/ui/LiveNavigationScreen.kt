package pe.vozruta.app.ui

import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.SystemClock
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pe.vozruta.app.maps.Destination
import pe.vozruta.app.maps.TravelMode
import pe.vozruta.app.maps.parseCoordinates
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.viewmodel.NavigationViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LiveNavigationScreen(model: NavigationViewModel, requestLocation: () -> Unit, voiceEnabled: Boolean,
    speak: (String) -> Unit, transit: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var follow by rememberSaveable { mutableStateOf(true) }
    var center by rememberSaveable { mutableIntStateOf(0) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var choosingMode by rememberSaveable { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val fix = state.fix?.takeIf { clock - it.atMillis <= 18_000 }
    val precise = fix?.usable(clock.coerceAtLeast(fix.atMillis)) == true
    val endpoint = state.route?.points?.lastOrNull()
    val gap = state.destination?.point?.let { destination -> endpoint?.metersTo(destination)?.roundToInt() } ?: 0
    val mapContent: @Composable () -> Unit = {
        val pins = listOfNotNull(state.destination, endpoint?.takeIf { gap > 25 }?.let { Destination("Final de la ruta", it) })
        StreetMap(state.route?.points.orEmpty(), fix?.point, pins, follow = follow,
            recenter = center, pick = { model.destination(Destination("Punto en el mapa", it)) }, moved = { follow = false })
    }
    LaunchedEffect(state.tracking) {
        while (state.tracking) {
            clock = SystemClock.elapsedRealtime()
            delay(2_000)
        }
    }
    LaunchedEffect(state.spoken?.id) {
        state.spoken?.let {
            if (voiceEnabled) speak(it.text)
            model.clearSpeech(it.id)
        }
    }
    if (state.tracking) LiveLocation(model)

    Section("Mapa y navegación", state.destination?.let { "Destino · ${it.name}" }
        ?: "Elige un destino para seguir el recorrido con tu GPS.")
    state.message?.let { Text(it, color = Warning, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    when {
        state.arrived -> Text("Llegaste al final del recorrido.", color = Accent, style = MaterialTheme.typography.titleMedium)
        state.tracking && !precise -> Text(if (fix == null) "Buscando señal de GPS…" else "La señal es imprecisa. Espera una ubicación más precisa.", color = Warning)
        state.offRoute -> Text(if (state.loading) "Actualizando recorrido…" else "Fuera de la ruta. Comprobando tu recorrido.", color = Warning)
        state.loading -> Text("Calculando recorrido…", color = Ash)
    }
    if (state.instruction != null && (precise || state.arrived) && !state.offRoute) {
        PanelCard {
            Text(state.maneuverDistance?.let { "En $it m" } ?: "Indicaciones del viaje", color = Accent)
            Text(state.instruction.orEmpty(), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            val remaining = state.remaining
            if (remaining != null) Text("$remaining m restantes · ${state.remainingMinutes ?: 0} min aprox.", color = Ash)
        }
    }
    if (state.tracking || state.arrived) mapContent()
    if (gap > 25) Text("La ruta termina a unos $gap m en línea recta del destino elegido.", color = Ash)
    PanelCard {
        if (!state.tracking) {
            Text("La guía usa el GPS mientras este mapa está abierto. Envía el origen y el destino al proveedor para calcular la ruta.", color = Ash)
            Primary(if (state.arrived) "Volver a activar GPS" else "Activar GPS y seguir", {
                follow = true
                requestLocation()
            }, Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Primary("Centrar GPS", { follow = true; center++ }, Modifier.weight(1f), enabled = fix != null)
                Secondary("Detener seguimiento", model::stop, Modifier.weight(1f))
            }
            if (state.message != null && precise && state.destination != null) {
                Secondary("Reintentar ruta", model::retry, Modifier.fillMaxWidth())
            }
        }
        Secondary("Elegir destino", { choosing = true }, Modifier.fillMaxWidth())
        Secondary("Modo de viaje · ${state.mode.label}", { choosingMode = true }, Modifier.fillMaxWidth())
    }
    if (!state.tracking && !state.arrived) mapContent()
    Text("Mantén pulsado en el mapa para marcar un destino.", color = Ash)
    PanelCard {
        if (precise && !state.offRoute) state.instruction?.let { instruction ->
            Secondary("Escuchar indicación", {
                val distance = state.maneuverDistance?.let { "En $it metros. " }.orEmpty()
                speak(distance + instruction)
            }, Modifier.fillMaxWidth())
        }
        if (state.mode == TravelMode.WALK) Text("Los datos de calles no confirman veredas, cruces ni accesibilidad.", color = Ash)
        if (state.mode == TravelMode.PASSENGER) Text("Sigue tu ubicación sobre una ruta vial estimada. Para horarios y líneas, consulta transporte público.", color = Ash)
        Secondary("Consultar transporte público", transit, Modifier.fillMaxWidth())
    }
    if (choosing) DestinationDialog({ choosing = false }) {
        model.destination(it)
        choosing = false
        center++
    }
    if (choosingMode) AlertDialog(onDismissRequest = { choosingMode = false },
        title = { Text("Modo de viaje") },
        text = {
            Column(Modifier.selectableGroup()) {
                TravelMode.entries.forEach { mode ->
                    val selected = state.mode == mode
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected, role = Role.RadioButton) {
                            model.mode(mode)
                            choosingMode = false
                        }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, null)
                        Text(mode.label, Modifier.padding(start = 10.dp))
                    }
                }
            }
        }, confirmButton = { Secondary("Cerrar", { choosingMode = false }) })
}

@SuppressLint("MissingPermission")
@Composable private fun LiveLocation(model: NavigationViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current.applicationContext
    LaunchedEffect(model, lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_500)
                .setMinUpdateIntervalMillis(1_000).setMaxUpdateAgeMillis(4_000).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(model::onLocation)
                }
            }
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener { model.notice("Activa la ubicación de Android y concede acceso preciso.") }
                awaitCancellation()
            } catch (_: SecurityException) {
                model.notice("Concede permiso de ubicación para activar el seguimiento.")
            } finally {
                client.removeLocationUpdates(callback)
                model.invalidateFix()
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable private fun DestinationDialog(close: () -> Unit, pick: (Destination) -> Unit) {
    val context = LocalContext.current.applicationContext
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<Destination>?>(null) }
    LaunchedEffect(searching) {
        val name = searching ?: return@LaunchedEffect
        matches = withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(context, Locale.forLanguageTag("es-PE")).getFromLocationName(name, 5).orEmpty()
                    .filter { it.hasLatitude() && it.hasLongitude() }
                    .map { address -> Destination(address.getAddressLine(0) ?: name, Geo(address.latitude, address.longitude)) }
            }.getOrDefault(emptyList())
        }
        searching = null
    }
    AlertDialog(onDismissRequest = close, title = { Text("Destino de navegación") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(query, { query = it.take(160); matches = null }, label = { Text("Dirección y ciudad o latitud, longitud") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = searching == null)
            Primary(if (searching == null) "Buscar destino" else "Buscando…", {
                val point = parseCoordinates(query)
                if (point != null) pick(Destination("Destino manual", point)) else searching = query.trim()
            }, Modifier.fillMaxWidth(), query.isNotBlank() && searching == null)
            if (matches?.isEmpty() == true) Text("No se encontraron direcciones. Prueba con la ciudad y el país o marca el destino en el mapa.", color = Warning)
            matches?.forEach { item -> Secondary(item.name, { pick(item) }, Modifier.fillMaxWidth()) }
            Text("Destinos de Huánuco–Ambo", style = MaterialTheme.typography.titleMedium)
            (Pilot.stops + Pilot.cityStops).distinctBy { it.id }.forEach { stop ->
                Secondary(stop.name, { pick(Destination(stop.name, stop.geo)) }, Modifier.fillMaxWidth())
            }
        }
    }, confirmButton = { Secondary("Cerrar", close) })
}

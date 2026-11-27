package pe.vozruta.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import pe.vozruta.app.BuildConfig
import pe.vozruta.app.maps.TransitPlan
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.PilotTrip
import pe.vozruta.app.model.Stop
import pe.vozruta.app.model.pointAlong
import pe.vozruta.app.viewmodel.Guide
import pe.vozruta.app.viewmodel.JourneyState
import pe.vozruta.app.viewmodel.JourneyViewModel
import pe.vozruta.app.viewmodel.Screen

@Composable internal fun TripScreen(
    state: JourneyState,
    model: JourneyViewModel,
    locate: (Boolean) -> Unit,
    maps: (String, String, String) -> Unit,
    global: () -> Unit,
    stopsDialog: () -> Unit,
    boardDialog: () -> Unit
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val trip = state.trip
    val guide = state.guide
    val plan = state.plans.getOrNull(state.selected)
    val pilot = trip != null || (plan == null && guide == null && !state.loading)
    Section(
        heading = when {
            trip != null -> trip.exit.name
            guide != null -> "Paradero cercano"
            plan != null -> "Opciones de transporte"
            else -> "Planifica tu viaje"
        },
        subtitle = when {
            trip != null -> "Corredor Huánuco–Ambo · Unidad de demostración"
            guide != null -> "Referencia del corredor Huánuco–Ambo"
            plan != null -> "Datos de transporte de Google Maps"
            else -> "Elige un destino del piloto o busca en otra ciudad."
        }
    )

    state.live?.let { item ->
        PanelCard {
            Tag("CENTRAL CONECTADA")
            Text("${item.board} → ${item.exit}", style = MaterialTheme.typography.titleMedium)
            Text(if (item.stage == BusStage.ROUTE) "Solicitud recibida" else item.stage.title, color = Accent)
            Text("Horario de demostración · ${item.time}", color = Ash)
        }
    }
    if (state.loading) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text("Consultando rutas…")
        }
    }
    if (trip != null) {
        PilotSummary(trip, model, state, { confirm = true }, boardDialog, maps)
    }
    if (guide != null) GuideDetails(guide, model, state, maps)
    if (plan != null) {
        state.plans.forEachIndexed { index, option ->
            if (state.plans.size > 1) {
                Secondary("${if (index == state.selected) "Seleccionada · " else ""}Opción ${index + 1} · ${option.minutes} min",
                    { model.selectPlan(index) }, Modifier.fillMaxWidth())
            }
        }
        PanelCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("Duración estimada", "${plan.minutes} min", Modifier.weight(1f))
                Fact("Tarifa informada", plan.fare ?: "Sin datos", Modifier.weight(1f))
            }
            Text("La accesibilidad de vehículos, cruces y paraderos requiere confirmación local.", color = Warning)
            Primary("Escuchar itinerario", { model.replay(model.describePlan(plan)) }, Modifier.fillMaxWidth())
        }
    }
    if (pilot || guide != null || BuildConfig.MAPS_ENABLED && plan?.points?.isNotEmpty() == true) {
        val line = when {
            plan != null -> plan.points
            guide != null -> if (BuildConfig.MAPS_ENABLED) guide.walking?.points.orEmpty() else emptyList()
            else -> remember(trip) { model.pilotLine(trip) }
        }
        val stops = when {
            guide != null -> listOf(guide.stop)
            pilot -> trip?.stops ?: Pilot.stops
            else -> emptyList()
        }
        val simulation = state.simulation
        val bus = if (pilot) remember(line, trip, simulation) {
            if (simulation.stage.ordinal <= BusStage.APPROACH.ordinal) {
                val board = trip?.board ?: Pilot.stops.first()
                val index = Pilot.stops.indexOf(board)
                val opposite = if (index > 0) Pilot.stops[index - 1] else Pilot.stops[1]
                val incoming = model.pilotLine(PilotTrip(opposite, board, listOf(opposite, board), "", 0)).takeLast(6)
                val progress = if (simulation.stage == BusStage.ROUTE) simulation.fraction * .55f
                    else .55f + simulation.fraction * .45f
                pointAlong(incoming, progress)
            } else pointAlong(line, simulation.routeProgress)
        } else null
        RouteMap(line, stops, state.location, bus, pilot, { locate(false) })
    }
    if (plan != null) Steps(plan, model)
    if (trip != null) {
        PanelCard {
            Text("Paradas del recorrido", style = MaterialTheme.typography.titleMedium)
            trip.stops.forEach { stop ->
                Text(when (stop) {
                    trip.board -> "Subida · ${stop.name}"
                    trip.exit -> "Bajada · ${stop.name}"
                    else -> stop.name
                }, color = Ash)
            }
        }
    }
    PanelCard {
        Primary("Elegir destino del piloto", stopsDialog, Modifier.fillMaxWidth())
        Secondary("Explorar otras ciudades", global, Modifier.fillMaxWidth())
        Secondary("Buscar paradero cercano", { locate(true) }, Modifier.fillMaxWidth())
    }
    if (confirm && trip != null) {
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text("Confirmar solicitud") },
            text = { Text(if (model.connected) "Enviarás a la central de pruebas el paradero ${trip.board.name}, el destino ${trip.exit.name}, el horario y el apoyo: ${state.profile.request}."
                else "Prueba la coordinación para ${trip.board.name} → ${trip.exit.name}. La simulación se verá en la central de esta app.") },
            confirmButton = { Primary(if (model.connected) "Enviar solicitud" else "Simular solicitud", {
                confirm = false
                model.request(trip)
            }) },
            dismissButton = { TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
    }
}

@Composable private fun PilotSummary(trip: PilotTrip, model: JourneyViewModel, state: JourneyState, confirm: () -> Unit, chooseBoard: () -> Unit,
    maps: (String, String, String) -> Unit) {
    PanelCard {
        Tag("BUS INCLUSIVO · DEMOSTRACIÓN")
        Text("Subida · ${trip.board.name}", style = MaterialTheme.typography.titleMedium)
        Text("Bajada · ${trip.exit.name}", color = Ash)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Próximo paso simulado", trip.departure(), Modifier.weight(1f))
            Fact("Tarifa de ejemplo", trip.fare, Modifier.weight(1f))
        }
        Text("${trip.minutes} min de ejemplo · Piso bajo, rampa y asistente", color = Ash)
        Text("Puntos de referencia. Los cruces, veredas y condiciones de abordaje requieren revisión en campo.", color = Warning)
        Primary(if (state.pending) "Enviando…" else "Solicitar abordaje inclusivo", confirm, Modifier.fillMaxWidth(), !state.pending)
        Secondary("Cambiar punto de subida", chooseBoard, Modifier.fillMaxWidth())
        Secondary("Escuchar este viaje", { model.replay(model.describeTrip(trip)) }, Modifier.fillMaxWidth())
        Secondary("Camino al punto de subida", { maps(state.location?.coordinate.orEmpty(), trip.board.geo.coordinate, "walking") }, Modifier.fillMaxWidth())
        Secondary("Ver bus y simulación", { model.navigate(Screen.BUS) }, Modifier.fillMaxWidth())
    }
}

@Composable private fun GuideDetails(guide: Guide, model: JourneyViewModel, state: JourneyState, maps: (String, String, String) -> Unit) {
    PanelCard {
        Text(guide.stop.name, style = MaterialTheme.typography.titleMedium)
        Text(guide.detail, color = Ash)
        if (guide.walking != null) {
            Text("Vía peatonal de Google · ${guide.walking.meters} m · ${guide.walking.minutes} min")
            Steps(guide.walking, model)
        }
        Primary("Escuchar guía", { model.replay(guide.walking?.let(model::describePlan) ?: "${guide.stop.name}. ${guide.detail}") }, Modifier.fillMaxWidth())
        Secondary("Abrir indicaciones a pie", { maps(state.location?.coordinate.orEmpty(), guide.stop.geo.coordinate, "walking") }, Modifier.fillMaxWidth())
    }
}

@Composable private fun Steps(plan: TransitPlan, model: JourneyViewModel) {
    if (plan.steps.isEmpty()) return
    PanelCard {
        Text("Paso a paso", style = MaterialTheme.typography.titleMedium)
        plan.steps.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(color = Line)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleMedium)
                Text(step.detail, color = Ash)
                Secondary("Escuchar paso ${index + 1}", { model.replay("${step.title}. ${step.detail}") })
            }
        }
    }
}

@Composable internal fun RouteMap(
    points: List<Geo>, stops: List<Stop>, user: Geo?, bus: Geo?, pilot: Boolean, locate: () -> Unit
) {
    val usefulUser = user?.takeIf { !pilot || Pilot.nearest(it) != null }
    val mapPoints = remember(points, stops) { (points + stops.map { it.geo }).distinct() }
    var center by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    PanelCard {
        Text(if (BuildConfig.MAPS_ENABLED) "Mapa del trayecto" else "Esquema del trayecto",
            style = MaterialTheme.typography.titleMedium)
        if (BuildConfig.MAPS_ENABLED && mapPoints.isNotEmpty()) {
            val camera = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(mapPoints.first().latLng, 13f)
            }
            LaunchedEffect(loaded) {
                if (!loaded) {
                    kotlinx.coroutines.delay(9_000)
                    timedOut = true
                }
            }
            LaunchedEffect(loaded, mapPoints, center, usefulUser) {
                if (!loaded) return@LaunchedEffect
                val all = (mapPoints + listOfNotNull(usefulUser)).distinct()
                camera.move(if (all.size == 1) CameraUpdateFactory.newLatLngZoom(all.first().latLng, 15f)
                    else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply {
                        all.forEach { include(it.latLng) }
                    }.build(), 34))
            }
            GoogleMap(Modifier.fillMaxWidth().height(285.dp).clip(RoundedCornerShape(13.dp)),
                cameraPositionState = camera, uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
                contentDescription = "Mapa del trayecto. Las paradas y el itinerario están disponibles como texto.",
                onMapLoaded = { loaded = true; timedOut = false }) {
                if (points.size >= 2) Polyline(points.map { it.latLng }, color = androidx.compose.ui.graphics.Color(0xFF2E7718), width = 12f)
                stops.forEach { stop ->
                    Marker(rememberUpdatedMarkerState(stop.geo.latLng), title = stop.name, snippet = if (pilot) "Referencia del piloto" else stop.area)
                }
                usefulUser?.let { geo ->
                    Marker(rememberUpdatedMarkerState(geo.latLng), title = "Tu ubicación",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                }
                bus?.let { geo ->
                    Marker(rememberUpdatedMarkerState(geo.latLng), title = "Autobús simulado",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                }
            }
            if (timedOut) Text("El mapa aún no se ha cargado. Revisa la conexión y la configuración de Google Maps.", color = Warning)
            Secondary("Centrar recorrido", { center++ }, Modifier.fillMaxWidth())
        } else if (mapPoints.isNotEmpty()) {
            Schematic(points, stops, usefulUser, bus)
            Text(if (pilot) "Trazado de referencia · © OpenStreetMap contributors" else "Vista sin mapa base", color = Ash)
        }
        Secondary("Obtener mi ubicación", locate, Modifier.fillMaxWidth())
    }
}

@Composable private fun Schematic(points: List<Geo>, stops: List<Stop>, user: Geo?, bus: Geo?) {
    val all = points + stops.map { it.geo } + listOfNotNull(user)
    if (all.isEmpty()) return
    Canvas(Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(13.dp)).background(Background)
        .semantics { contentDescription = "Esquema del trayecto. Consulta el listado de paradas para leer sus nombres." }) {
        val minX = all.minOf { it.lng }
        val maxY = all.maxOf { it.lat }
        val westEast = (all.maxOf { it.lng } - minX).coerceAtLeast(.00012)
        val northSouth = (maxY - all.minOf { it.lat }).coerceAtLeast(.00012)
        val margin = 20.dp.toPx()
        val scale = minOf((size.width - margin * 2) / westEast, (size.height - margin * 2) / northSouth)
        val left = (size.width - westEast * scale) / 2
        val top = (size.height - northSouth * scale) / 2
        fun position(geo: Geo) = Offset((left + (geo.lng - minX) * scale).toFloat(),
            (top + (maxY - geo.lat) * scale).toFloat())
        val interval = 22.dp.toPx()
        for (x in 1..(size.width / interval).toInt()) for (y in 1..(size.height / interval).toInt()) {
            drawCircle(Line, 1.dp.toPx(), Offset(x * interval, y * interval))
        }
        if (points.size > 1) {
            val path = Path().apply {
                val first = position(points.first())
                moveTo(first.x, first.y)
                points.drop(1).forEach { point -> position(point).let { lineTo(it.x, it.y) } }
            }
            drawPath(path, ColorPath, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        stops.forEach { stop ->
            drawCircle(Background, 5.dp.toPx(), position(stop.geo))
            drawCircle(Accent, 4.dp.toPx(), position(stop.geo), style = Stroke(1.5.dp.toPx()))
        }
        user?.let { drawCircle(UserDot, 5.dp.toPx(), position(it)) }
        bus?.let {
            val offset = position(it)
            val clamped = Offset(offset.x.coerceIn(margin, size.width - margin), offset.y.coerceIn(margin, size.height - margin))
            drawCircle(Accent, 7.dp.toPx(), clamped)
            drawCircle(Ink, 3.dp.toPx(), clamped)
        }
    }
}
private val ColorPath = androidx.compose.ui.graphics.Color(0xFF425121)
private val UserDot = androidx.compose.ui.graphics.Color(0xFF83D3FD)
private val Geo.latLng get() = LatLng(lat, lng)

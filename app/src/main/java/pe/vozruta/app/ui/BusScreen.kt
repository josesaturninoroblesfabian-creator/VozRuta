package pe.vozruta.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.pointAlong
import pe.vozruta.app.simulation3d.BusScene
import pe.vozruta.app.simulation3d.SceneCamera
import pe.vozruta.app.viewmodel.JourneyState
import pe.vozruta.app.viewmodel.JourneyViewModel

@Composable internal fun BusScreen(state: JourneyState, model: JourneyViewModel, locate: () -> Unit) {
    val sim = state.simulation
    val trip = state.trip
    var camera by rememberSaveable { mutableStateOf(SceneCamera.EXTERIOR) }
    var automatic by rememberSaveable { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val shown = if (automatic) when (sim.stage) {
        BusStage.RAMP, BusStage.ARRIVED -> SceneCamera.RAMP
        BusStage.ASSIST -> if (sim.fraction > .64f) SceneCamera.INTERIOR else SceneCamera.RAMP
        else -> SceneCamera.EXTERIOR
    } else camera
    val label = when (trip?.cityRoute) { 1 -> "R1 HOSPITAL"; 2 -> "R2 UNHEVAL"; else -> "HUÁNUCO / AMBO" }
    Section("Autobús inclusivo", "Unidad TI–04 · $label")
    PanelCard {
        Tag("SIMULACIÓN 3D")
        if (!failed) BusScene(sim, state.profile, shown, label) { failed = true }
        else Text("El dispositivo no pudo dibujar la escena 3D. Puedes continuar con los estados y avisos.", color = Warning)
        Text(sim.stage.title, style = MaterialTheme.typography.titleMedium, color = Accent,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(sim.stage.messageFor(state.profile), color = Ash)
        Text("Velocidad simulada · ${sim.speedKmh} km/h", color = Ash)
        trip?.let {
            val label = if (sim.stage.ordinal < BusStage.TRAVEL.ordinal) "Subida · ${it.board.name}"
                else "Bajada · ${it.exit.name}"
            Text(label, color = Ash)
        }
        LinearProgressIndicator(progress = { sim.routeProgress }, modifier = Modifier.fillMaxWidth(),
            color = Accent, trackColor = Raised)
    }
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Primary(when {
                sim.running -> "Pausar"
                sim.stage == BusStage.ARRIVED && sim.fraction >= 1f -> "Reiniciar"
                sim.elapsed > 0 -> "Continuar"
                else -> "Iniciar"
            }, model::toggleSimulation, Modifier.weight(1f))
            Secondary("Velocidad ${sim.speed}x", model::simulationSpeed, Modifier.weight(1f))
        }
        Secondary("Simular llegada y rampa", {
            camera = SceneCamera.RAMP
            model.simulationRamp()
        }, Modifier.fillMaxWidth())
        ToggleRow("Voz interna del bus", sim.announcements, model::simulationVoice)
        Secondary("Escuchar esta etapa", { model.replay("Simulación. ${sim.stage.messageFor(state.profile)}", bus = true) }, Modifier.fillMaxWidth())
    }
    PanelCard {
        Text("Cámara", style = MaterialTheme.typography.titleMedium)
        ToggleRow("Cámara automática", automatic) { automatic = it }
        Column(Modifier.selectableGroup()) {
            SceneCamera.entries.forEach { view ->
                val selected = view == shown
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected, role = Role.RadioButton) { automatic = false; camera = view },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, onClick = null)
                    Text(view.title, Modifier.padding(start = 10.dp))
                }
            }
        }
    }
    val line = remember(trip) { model.pilotLine(trip) }
    RouteMap(line, trip?.stops ?: Pilot.stops, state.location,
        remember(line, sim.routeProgress) { pointAlong(line, sim.routeProgress) }, true, locate)
}

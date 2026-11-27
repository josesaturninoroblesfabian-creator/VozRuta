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
    var camera by rememberSaveable { mutableStateOf(SceneCamera.EXTERIOR) }
    var failed by remember { mutableStateOf(false) }
    Section("Autobús inclusivo", "Unidad VR–01 · Huánuco–Ambo")
    PanelCard {
        Tag("SIMULACIÓN 3D")
        if (!failed) BusScene(sim, state.profile, camera) { failed = true }
        else Text("El dispositivo no pudo dibujar la escena 3D. Puedes continuar con los estados y avisos.", color = Warning)
        Text(sim.stage.title, style = MaterialTheme.typography.titleMedium, color = Accent,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(sim.stage.messageFor(state.profile), color = Ash)
        LinearProgressIndicator(progress = { sim.routeProgress }, modifier = Modifier.fillMaxWidth(),
            color = Accent, trackColor = Raised)
    }
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Primary(when {
                sim.running -> "Pausar"
                sim.stage == BusStage.ARRIVED -> "Reiniciar"
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
        Secondary("Escuchar esta etapa", { model.replay("Simulación. ${sim.stage.messageFor(state.profile)}") }, Modifier.fillMaxWidth())
    }
    PanelCard {
        Text("Cámara", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            SceneCamera.entries.forEach { view ->
                val selected = view == camera
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected, role = Role.RadioButton) { camera = view },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, onClick = null)
                    Text(view.title, Modifier.padding(start = 10.dp))
                }
            }
        }
    }
    val trip = state.trip
    val line = remember(trip) { model.pilotLine(trip) }
    RouteMap(line, trip?.stops ?: Pilot.stops, state.location,
        remember(line, sim.routeProgress) { pointAlong(line, sim.routeProgress) }, true, locate)
}

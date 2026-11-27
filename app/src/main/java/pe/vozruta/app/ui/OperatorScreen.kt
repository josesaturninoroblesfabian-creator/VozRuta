package pe.vozruta.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.viewmodel.JourneyState
import pe.vozruta.app.viewmodel.JourneyViewModel
import pe.vozruta.app.viewmodel.Screen

@Composable internal fun OperatorScreen(state: JourneyState, model: JourneyViewModel) {
    Section("Central de asistencia", "TransInclusivo Huánuco · Operación propuesta")

    if (model.operatorConnected) {
        PanelCard {
            Tag("BANDEJA CONECTADA · PRUEBAS")
            Text("La tripulación confirma cada etapa después de verificarla en la unidad.", color = Ash)
            if (state.queue.isEmpty()) Text("Aún no hay solicitudes en la bandeja.")
        }
        state.queue.forEach { item ->
            PanelCard {
                Text("${item.board} → ${item.exit}", style = MaterialTheme.typography.titleMedium)
                Text("${item.time} · ${item.profile}", color = Ash)
                Text(item.stage.title, color = Accent)
                if (item.stage != BusStage.ARRIVED) {
                    val next = BusStage.entries[item.stage.ordinal + 1]
                    Primary("Confirmar: ${next.title}", { model.advance(item) }, Modifier.fillMaxWidth(), !state.pending)
                }
            }
        }
    }
    PanelCard {
        Tag("SIMULACIÓN LOCAL")
        Text("Unidad VR–01", style = MaterialTheme.typography.headlineMedium)
        Text(state.simulation.stage.title, color = Accent, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        val trip = state.trip
        if (state.demoRequest && trip != null) {
            Text("Solicitud local · ${trip.board.name} → ${trip.exit.name}")
            Text("${trip.departure()} · ${state.profile.request}", color = Ash)
        }
        Text(state.simulation.stage.messageFor(state.profile), color = Ash)
        Primary("Avanzar etapa de la demo", model::nextDemoStage, Modifier.fillMaxWidth())
        Secondary("Abrir simulador 3D", { model.navigate(Screen.BUS) }, Modifier.fillMaxWidth())
    }
    PanelCard {
        Text("Tripulación a bordo", style = MaterialTheme.typography.titleMedium)
        Text("Chofer · detiene y asegura la unidad antes del abordaje.", color = Ash)
        Text("Asistente · confirma el apoyo, guía al pasajero y revisa el anclaje antes de salir.", color = Ash)
    }
    PanelCard {
        Text("Equipamiento propuesto", style = MaterialTheme.typography.titleMedium)
        Text("Piso bajo, rampa, anclaje para silla, asiento preferente, pasamanos de alto contraste y señalética táctil.", color = Ash)
    }
}

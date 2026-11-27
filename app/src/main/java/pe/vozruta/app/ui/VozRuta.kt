package pe.vozruta.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.Profile
import pe.vozruta.app.model.Stop
import pe.vozruta.app.viewmodel.JourneyState
import pe.vozruta.app.viewmodel.JourneyViewModel
import pe.vozruta.app.viewmodel.Screen
import pe.vozruta.app.viewmodel.NavigationViewModel
import pe.vozruta.app.maps.Destination
import pe.vozruta.app.maps.TravelMode

private enum class Prompt { SETTINGS, COMMAND, GLOBAL, STOPS, BOARD }

@Composable
fun VozRuta(
    model: JourneyViewModel,
    navigator: NavigationViewModel,
    requestNavigation: () -> Unit,
    recognize: () -> Unit,
    command: (String) -> Unit,
    locate: (Boolean) -> Unit,
    openMaps: (String, String, String) -> Unit,
    speak: (String, Boolean) -> Unit,
    stopSpeaking: () -> Unit
) = Theme {
    val state by model.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf<Prompt?>(null) }
    var globalSeed by rememberSaveable { mutableStateOf("") }
    val openGlobal: () -> Unit = { globalSeed = ""; prompt = Prompt.GLOBAL }
    LaunchedEffect(state.suggestedDestination) {
        state.suggestedDestination?.let {
            globalSeed = it
            prompt = Prompt.GLOBAL
            model.clearSuggestedDestination()
        }
    }
    var consumed by remember { mutableLongStateOf(0L) }
    val event = state.speech
    LaunchedEffect(event?.id) {
        if (event != null && consumed != event.id) {
            consumed = event.id
            speak(event.text, event.bus)
        }
    }
    val selectScreen: (Screen) -> Unit = {
        if (state.screen == Screen.NAVIGATION && it != Screen.NAVIGATION) stopSpeaking()
        model.navigate(it)
    }
    BackHandler(state.screen != Screen.HOME) { selectScreen(Screen.HOME) }

    Scaffold(
        topBar = { BrandBar { prompt = Prompt.SETTINGS } },
        bottomBar = { BottomBar(state.screen, selectScreen) },
        containerColor = Background,
        contentColor = Snow,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) { insets ->
        key(state.screen) {
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                state.message?.let { message ->
                    Surface(color = Raised, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(message, color = Warning, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = model::clearMessage, modifier = Modifier.heightIn(min = 56.dp)) { Text("Entendido") }
                        }
                    }
                }
                when (state.screen) {
                    Screen.HOME -> Home(state, recognize, command, { selectScreen(Screen.NAVIGATION) }) {
                        if (it == Prompt.GLOBAL) openGlobal() else prompt = it
                    }
                    Screen.TRIP -> TripScreen(state, model, locate, openGlobal,
                        { prompt = Prompt.STOPS }, { prompt = Prompt.BOARD }) { stop, mode ->
                        navigator.mode(mode)
                        navigator.destination(Destination(stop.name, stop.geo))
                        selectScreen(Screen.NAVIGATION)
                    }
                    Screen.NAVIGATION -> LiveNavigationScreen(navigator, requestNavigation, state.voiceEnabled, { speak(it, false) }, openGlobal)
                    Screen.BUS -> BusScreen(state, model) { locate(false) }
                    Screen.OPERATOR -> OperatorScreen(state, model)
                }
            }
        }
    }
    when (prompt) {
        Prompt.SETTINGS -> ProfileDialog(state, model, { prompt = null }, stopSpeaking)
        Prompt.COMMAND -> CommandDialog({ prompt = null }) { prompt = null; command(it) }
        Prompt.GLOBAL -> GlobalDialog(state, model.connected, globalSeed, { prompt = null }, { locate(false) }, external = { from, to ->
            prompt = null
            openMaps(from, to, "transit")
        }) { from, to ->
            prompt = null
            if (model.connected) model.global(from, to) else openMaps(from, to, "transit")
        }
        Prompt.STOPS -> StopsDialog("Destinos del piloto", Pilot.stops + Pilot.cityStops, { prompt = null }) { stop ->
            prompt = null
            model.pilot(stop)
        }
        Prompt.BOARD -> {
            state.trip?.let { trip ->
                StopsDialog("Punto de subida", Pilot.network(trip.cityRoute).filter { it != trip.exit }, { prompt = null }) { stop ->
                    prompt = null
                    model.pilot(trip.exit, stop, trip.cityRoute)
                }
            }
        }
        null -> Unit
    }
}

@Composable private fun BrandBar(settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Accent, shape = RoundedCornerShape(13.dp)) {
            RouteMark(Modifier.size(42.dp).padding(10.dp), Ink)
        }
        Spacer(Modifier.width(10.dp))
        Text("VozRuta", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-1.1).sp)
        OutlinedButton(onClick = settings, modifier = Modifier.heightIn(min = 56.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
            Text("Accesibilidad", color = Snow, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun BottomBar(current: Screen, select: (Screen) -> Unit) {
    NavigationBar(containerColor = Color(0xFF161E20), contentColor = Snow,
        windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) {
        Screen.entries.forEach { screen ->
            val image = when (screen) {
                Screen.HOME -> Icons.Default.Home
                Screen.TRIP -> Icons.Default.Place
                Screen.NAVIGATION -> Icons.Default.Place
                Screen.BUS -> Icons.Default.PlayArrow
                Screen.OPERATOR -> Icons.AutoMirrored.Filled.List
            }
            NavigationBarItem(selected = current == screen, onClick = { select(screen) },
                icon = { Icon(image, contentDescription = null) },
                label = { Text(screen.title, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, selectedTextColor = Accent,
                    indicatorColor = Accent, unselectedIconColor = Ash, unselectedTextColor = Ash))
        }
    }
}

@Composable private fun Home(state: JourneyState, recognize: () -> Unit, command: (String) -> Unit, map: () -> Unit, prompt: (Prompt) -> Unit) {
    Surface(color = Color(0xFF233332), shape = RoundedCornerShape(27.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF253935), Color(0xFF1C2828))))) {
            MapArtwork(Modifier.matchParentSize())
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Tag("PILOTO HUÁNUCO–AMBO", dark = true)
                Text("¿A dónde\nvamos?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                Text("Planea tu viaje y elige el apoyo que necesitas.", color = Snow, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Primary("Hablar con VozRuta", recognize, Modifier.fillMaxWidth(), mic = true)
                Secondary("Escribir comando", { prompt(Prompt.COMMAND) }, Modifier.fillMaxWidth())
                Secondary("Abrir mapa y GPS", map, Modifier.fillMaxWidth())
            }
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Destinos rápidos", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { prompt(Prompt.STOPS) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("Ver todos")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Shortcut("UNHEVAL", "Universidad", Modifier.weight(1f)) { command("Ir a UNHEVAL") }
            Shortcut("Amarilis", "Paucarbamba", Modifier.weight(1f)) { command("Ir a Amarilis") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Shortcut("Hospital", "Huánuco", Modifier.weight(1f)) { command("Ir al Hospital") }
            Shortcut("Paradero cercano", "Usar ubicación", Modifier.weight(1f)) { command("Paradero cercano") }
        }
    }

    Surface(onClick = { prompt(Prompt.GLOBAL) }, color = Panel, shape = Shape, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explorar otras ciudades", style = MaterialTheme.typography.titleMedium)
                Text("Consulta transporte público donde haya cobertura.", color = Ash, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.Place, contentDescription = null, tint = Accent)
        }
    }
    if (state.profile != Profile.GENERAL) {
        Text("Apoyo activo · ${state.profile.title}", color = Accent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun Shortcut(title: String, subtitle: String, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier.heightIn(min = 78.dp), color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Place, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
            Column {
                Text(title, color = Snow, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                Text(subtitle, color = Ash, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun MapArtwork(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height * .70f
        for (index in 0..9) {
            val d = index * 12.dp.toPx()
            val line = Path().apply {
                moveTo(w * .56f + d * .57f, -18f)
                cubicTo(w * .45f + d, h * .30f, w * .72f + d, h * .32f, w * .61f + d, h * .51f)
                cubicTo(w * .56f + d, h * .69f, w * .86f + d, h * .54f, w * .82f + d, h * .93f)
            }
            drawPath(line, Color(0xFF4B675A), alpha = .38f, style = Stroke(1.dp.toPx()))
        }
        val head = Offset(w * .84f, h * .16f)
        val stop = Offset(w * .73f, h * .49f)
        val tail = Offset(w * .94f, h * .65f)
        val path = Path().apply {
            moveTo(head.x, head.y)
            cubicTo(w * .69f, h * .29f, w * .80f, h * .41f, stop.x, stop.y)
            cubicTo(w * .67f, h * .65f, w * .82f, h * .60f, tail.x, tail.y)
        }
        drawPath(path, Accent, alpha = .36f, style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        listOf(head, stop, tail).forEach {
            drawCircle(Ink, 7.dp.toPx(), it)
            drawCircle(Accent, 4.dp.toPx(), it)
        }
    }
}

@Composable private fun ProfileDialog(state: JourneyState, model: JourneyViewModel, close: () -> Unit, stopSpeaking: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Accesibilidad") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Elige el apoyo que quieres solicitar.", style = MaterialTheme.typography.bodyMedium)
            Profile.entries.forEach { profile ->
                val selected = state.profile == profile
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .selectable(selected, role = Role.RadioButton) { model.profile(profile) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, onClick = null)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(profile.title, style = MaterialTheme.typography.titleMedium)
                        Text(profile.detail, color = Ash)
                    }
                }
            }
            ToggleRow("Avisos hablados", state.voiceEnabled) {
                model.voice(it)
                if (!it) stopSpeaking()
            }
        }
    }, confirmButton = { Primary("Listo", close) })
}

@Composable private fun CommandDialog(close: () -> Unit, run: (String) -> Unit) {
    var command by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Escribir comando") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Prueba «Ir a UNHEVAL», «Ir a Amarilis», «Ir al Hospital» o «Paradero cercano».")
            OutlinedTextField(command, { command = it.take(140) }, Modifier.fillMaxWidth(), label = { Text("Comando") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (command.isNotBlank()) run(command) }))
        }
    }, confirmButton = { Primary("Ejecutar", { run(command) }, enabled = command.isNotBlank()) },
        dismissButton = { TextButton(close, Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
}

@Composable private fun GlobalDialog(
    state: JourneyState, connected: Boolean, seed: String, close: () -> Unit, locate: () -> Unit,
    external: (String, String) -> Unit, search: (String, String) -> Unit
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable(seed) { mutableStateOf(seed) }
    var useGps by rememberSaveable { mutableStateOf(false) }
    val from = if (useGps) state.location?.coordinate.orEmpty() else origin.trim()
    AlertDialog(onDismissRequest = close, title = { Text("Buscar transporte") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Escribe lugares o direcciones con su ciudad y país.")
            OutlinedTextField(if (useGps) state.location?.let { "Ubicación actual" }.orEmpty() else origin,
                { origin = it.take(220) }, Modifier.fillMaxWidth(), enabled = !useGps, label = { Text("Origen") }, singleLine = true)
            Secondary(if (useGps && state.location != null) "Escribir otro origen" else "Usar mi ubicación", {
                if (useGps && state.location != null) useGps = false else { useGps = true; locate() }
            }, Modifier.fillMaxWidth())
            if (useGps && state.location == null) {
                TextButton(onClick = { useGps = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Ingresar origen manualmente") }
            }
            OutlinedTextField(destination, { destination = it.take(220) }, Modifier.fillMaxWidth(),
                label = { Text("Destino") }, singleLine = true)
            if (!connected) Text("La búsqueda se abrirá en Google Maps.")
            else Secondary("Abrir en Google Maps", { external(from, destination.trim()) }, Modifier.fillMaxWidth(),
                enabled = destination.isNotBlank() && (!useGps || from.isNotBlank()))
        }
    }, confirmButton = { Primary("Buscar", { search(from, destination.trim()) },
        enabled = destination.isNotBlank() && (from.isNotBlank() || !connected && !useGps)) },
        dismissButton = { TextButton(close, Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
}

@Composable private fun StopsDialog(title: String, stops: List<Stop>, close: () -> Unit, pick: (Stop) -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stops.forEach { stop ->
                Shortcut(stop.name, stop.area, Modifier.fillMaxWidth()) { pick(stop) }
            }
        }
    }, confirmButton = { TextButton(close, Modifier.heightIn(min = 56.dp)) { Text("Cerrar") } })
}

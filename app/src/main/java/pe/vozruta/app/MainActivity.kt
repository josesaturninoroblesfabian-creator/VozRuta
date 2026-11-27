package pe.vozruta.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.normalize
import pe.vozruta.app.ui.VozRuta
import pe.vozruta.app.viewmodel.JourneyViewModel
import pe.vozruta.app.voice.Voice
import pe.vozruta.app.voice.voiceIntent

// The activity uses ActivityResultRegistry directly and hosts no fragments.
@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private val model by viewModels<JourneyViewModel>()
    private lateinit var voice: Voice
    private var locationRequest: CancellationTokenSource? = null
    private var nearestRequested = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) locate(nearestRequested)
        else model.notice("Permite la ubicación para buscar desde donde estás. También puedes ingresarla manualmente.")
    }
    private val recognizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val options = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val known = options.firstOrNull { option ->
            val command = normalize(option)
            Pilot.stop(option) != null || ("paradero" in command && "cercan" in command)
        }
        val text = known ?: options.firstOrNull()
        if (text.isNullOrBlank()) model.notice("No se reconoció un comando. Prueba de nuevo o escríbelo.")
        else command(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(SystemBarStyle.dark(transparent), SystemBarStyle.dark(transparent))
        voice = Voice(this).also { speaker ->
            speaker.unavailable = {
                model.notice("La voz en español no está disponible. Revisa el motor de síntesis de Android.")
            }
        }
        setContent {
            VozRuta(model, ::recognize, ::command, ::locate, ::openMaps,
                speak = voice::say, stopSpeaking = voice::stop)
        }
    }

    override fun onStart() {
        super.onStart()
        model.setActive(true)
    }

    override fun onStop() {
        model.setActive(false)
        voice.stop()
        super.onStop()
    }

    override fun onDestroy() {
        locationRequest?.cancel()
        voice.close()
        super.onDestroy()
    }

    private fun recognize() {
        voice.stop()
        try {
            recognizer.launch(voiceIntent())
        } catch (_: ActivityNotFoundException) {
            model.notice("Este dispositivo no tiene reconocimiento de voz instalado. Usa «Escribir comando».")
        }
    }

    private fun command(text: String) {
        if (model.command(text)) locate(true)
    }

    private fun locate(nearest: Boolean) {
        nearestRequested = nearest
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        locationRequest?.cancel()
        val request = CancellationTokenSource().also { locationRequest = it }
        val options = CurrentLocationRequest.Builder().setMaxUpdateAgeMillis(15_000).setDurationMillis(12_000)
            .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY).build()
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(options, request.token)
            .addOnSuccessListener(this) { location ->
                if (request.token.isCancellationRequested) return@addOnSuccessListener
                val geo = location?.let { Geo(it.latitude, it.longitude) }
                if (nearest && geo != null) model.nearest(geo) else model.location(geo)
            }.addOnFailureListener(this) { if (!request.token.isCancellationRequested) model.location(null) }
    }

    private fun openMaps(origin: String, target: String, mode: String) {
        val uri = Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", target)
            .appendQueryParameter("travelmode", mode)
        if (origin.isNotBlank()) uri.appendQueryParameter("origin", origin)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri.build()))
        } catch (_: ActivityNotFoundException) {
            model.notice("No hay una aplicación disponible para abrir indicaciones.")
        }
    }
}

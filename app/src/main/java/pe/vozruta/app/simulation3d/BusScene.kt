package pe.vozruta.app.simulation3d

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.json.JSONObject
import pe.vozruta.app.model.Profile
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class SceneCamera(val title: String) { EXTERIOR("Exterior"), RAMP("Rampa y abordaje"), INTERIOR("Interior") }

@Composable
fun BusScene(simulation: BusSimulation, profile: Profile, camera: SceneCamera, routeLabel: String, onFailure: () -> Unit) {
    var ready by remember { mutableStateOf<WebView?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val inputs by rememberUpdatedState(listOf(simulation.stage.ordinal, simulation.fraction, camera.ordinal, profile.ordinal))
    val failure by rememberUpdatedState(onFailure)
    BoxWithConstraints(Modifier.fillMaxWidth().height(238.dp).clip(RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        val width = 520
        val height = (width * maxHeight.value / maxWidth.value.coerceAtLeast(1f)).roundToInt().coerceIn(100, 900)
        LaunchedEffect(Unit) {
            delay(20_000)
            if (bitmap == null) failure()
        }
        LaunchedEffect(ready, height, routeLabel) {
            val web = ready ?: return@LaunchedEffect
            // Keep one frame in flight. Compose presents WebGL frames independently of the WebView compositor.
            snapshotFlow { inputs }.conflate().collect { values ->
                val program = "window.renderBusFrame?.(${values.joinToString(",")},$width,$height,${JSONObject.quote(routeLabel)}) ?? null"
                val result = web.evaluate(program)
                val frame = withContext(Dispatchers.Default) { decodeFrame(result) }
                if (frame != null) bitmap = frame else failure()
            }
        }
        AndroidView(
            factory = { context ->
                val assets = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                WebView(context).apply {
                    isFocusable = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                android.util.Log.w("VozRutaEscena", message.message())
                                failure()
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                            if (request.url.host == "appassets.androidplatform.net" && request.url.scheme == "https") {
                                assets.shouldInterceptRequest(request.url) ?: denied()
                            } else denied()

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                        override fun onPageFinished(view: WebView, url: String) {
                            if (url == SCENE_URL) ready = view
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) failure()
                        }

                        private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403,
                            "Forbidden", emptyMap(), "".byteInputStream())
                    }
                    loadUrl(SCENE_URL)
                }
            },
            modifier = Modifier.size(1.dp),
            onReset = null,
            onRelease = { it.stopLoading(); it.destroy() },
            update = {}
        )
        bitmap?.let {
            Image(it, contentDescription = "Vista ${camera.title.lowercase()} del modelo tridimensional del autobús.",
                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        } ?: CircularProgressIndicator(Modifier.size(28.dp))
    }
}

private const val SCENE_URL = "https://appassets.androidplatform.net/assets/scene.html"

private suspend fun WebView.evaluate(program: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(program) { result ->
        if (continuation.isActive) continuation.resume(result)
    }
}

private fun decodeFrame(result: String?): ImageBitmap? {
    if (result == null || result == "null") return null
    return runCatching {
        val image = JSONTokener(result).nextValue() as? String ?: return null
        val jpeg = Base64.decode(image.substringAfter("data:image/jpeg;base64,", ""), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
    }.getOrNull()
}

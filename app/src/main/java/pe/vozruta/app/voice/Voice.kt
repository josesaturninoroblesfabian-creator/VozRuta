package pe.vozruta.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Di «ruta uno», «paradero cercano» o «ayuda»")
}

class Voice(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var waiting: Pair<String, Boolean>? = null
    private var guideVoice: android.speech.tts.Voice? = null
    private var busVoice: android.speech.tts.Voice? = null
    var unavailable: (() -> Unit)? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (!closed && status == TextToSpeech.SUCCESS) {
                val tts = engine
                val peru = Locale.forLanguageTag("es-PE")
                val spain = Locale.forLanguageTag("es-ES")
                ready = tts != null && (tts.setLanguage(peru) >= TextToSpeech.LANG_AVAILABLE ||
                    tts.setLanguage(spain) >= TextToSpeech.LANG_AVAILABLE)
                if (ready) {
                    guideVoice = tts?.voice
                    busVoice = tts?.voices?.filter { it.locale.language == "es" && !it.isNetworkConnectionRequired &&
                        it != guideVoice && "notInstalled" !in it.features }
                        ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "PE" }
                            .thenByDescending { it.quality }.thenBy { it.name })?.firstOrNull()
                    tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    waiting?.let { (text, bus) -> say(text, bus) }
                    waiting = null
                }
            }
            if (!closed && !ready) {
                unavailable?.invoke()
                waiting = null
            }
        }
    }

    fun say(text: String, bus: Boolean) {
        if (closed) return
        if (!ready) {
            waiting = text to bus
            return
        }
        (if (bus) busVoice ?: guideVoice else guideVoice)?.let { engine?.setVoice(it) }
        engine?.setPitch(if (bus) .87f else 1f)
        engine?.setSpeechRate(if (bus) .90f else 1f)
        val limit = TextToSpeech.getMaxSpeechInputLength() - 20
        var remainder = text
        var queue = TextToSpeech.QUEUE_FLUSH
        while (remainder.isNotBlank()) {
            val cut = if (remainder.length <= limit) remainder.length
                else remainder.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
            val result = engine?.speak(remainder.take(cut), queue, null, "vozruta-${System.nanoTime()}")
            if (result == TextToSpeech.ERROR) {
                unavailable?.invoke()
                return
            }
            remainder = remainder.drop(cut).trimStart()
            queue = TextToSpeech.QUEUE_ADD
        }
    }

    fun stop() {
        waiting = null
        engine?.stop()
    }

    fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
    }
}

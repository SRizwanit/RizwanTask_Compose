package com.srizwan.task.screens.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The phone's own TTS engine — React `speechSynthesis` + `SpeechSynthesisUtterance` (4-জ-২).
 * [hasTTS] = React `hasTTS` (false when the phone has no engine). The engine starts at once; a [speak] before it is ready waits for it.
 * All callbacks arrive on the main thread. Error codes follow React: `language-unavailable`, `synthesis-failed`, `not-allowed`.
 */
class TtsDevice(context: Context, private val onVoicesChanged: () -> Unit) {
    private val appCtx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    /** 0 = starting, 1 = ready, 2 = failed (no engine). */
    private var st = 0
    private val waiting = mutableListOf<() -> Unit>()
    private var seq = 0
    private var cur: Callbacks? = null
    private var curId = ""

    /** What one spoken piece reports (React `u.onstart`, `u.onboundary`, `u.onend`, `u.onerror`). */
    class Callbacks(
        val onStart: () -> Unit,
        /** React `ev.charIndex` — start of the word being spoken (only engines that report it). */
        val onBoundary: (Int) -> Unit,
        val onEnd: () -> Unit,
        /** React `e.error`. */
        val onError: (String) -> Unit
    )

    /** React `hasTTS`. */
    val hasTTS: Boolean get() = st != 2

    init {
        try {
            tts = TextToSpeech(appCtx) { code ->
                main.post {
                    if (code == TextToSpeech.SUCCESS && tts != null) { st = 1; attach(); onVoicesChanged() } else st = 2
                    val w = waiting.toList(); waiting.clear(); w.forEach { it() }
                }
            }
        } catch (_: Exception) { st = 2 }
    }

    private fun attach() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { main.post { if (utteranceId == curId) cur?.onStart?.invoke() } }
            override fun onDone(utteranceId: String) { main.post { if (utteranceId == curId) cur?.onEnd?.invoke() } }
            @Deprecated("Deprecated in Java")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String) { main.post { if (utteranceId == curId) cur?.onError?.invoke("synthesis-failed") } }
            override fun onError(utteranceId: String, errorCode: Int) {
                val e = when (errorCode) {
                    TextToSpeech.ERROR_NOT_INSTALLED_YET -> "language-unavailable"
                    TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT -> "network"
                    else -> "synthesis-failed"
                }
                main.post { if (utteranceId == curId) cur?.onError?.invoke(e) }
            }
            // a stopped piece is silent: the controller already moved on (React 'canceled' / 'interrupted' are ignored)
            override fun onStop(utteranceId: String, interrupted: Boolean) {}
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { if (utteranceId == curId) cur?.onBoundary?.invoke(start) }
            }
        })
    }

    /** React `speechSynthesis.getVoices()` — empty until the engine is ready. */
    fun voices(): List<TtsVoice> {
        if (st != 1) return emptyList()
        return try {
            tts?.voices.orEmpty().map { TtsVoice(it.name, it.name, it.locale.toLanguageTag()) }.sortedBy { it.lang + it.name }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * React `speechSynthesis.speak(u)` — read [text]; [voice] (if any) and its language, else [lang] (`bn-BD` ...); [rate] 0.5..2.
     * Replaces whatever is being spoken. Returns the utterance id (the controller keeps it in `u`).
     */
    fun speak(text: String, voice: TtsVoice?, lang: String, rate: Double, cb: Callbacks): String {
        val id = "u" + (++seq)
        cur = cb; curId = id
        val go = {
            if (id == curId) {
                val t = tts
                if (st != 1 || t == null) cb.onError("not-allowed") else try {
                    var ok = true
                    val v = if (voice != null) t.voices.orEmpty().firstOrNull { it.name == voice.uri } else null
                    if (v != null) t.voice = v
                    else {
                        val r = t.setLanguage(Locale.forLanguageTag(voice?.lang ?: lang))
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) ok = false
                    }
                    if (!ok) cb.onError("language-unavailable") else {
                        t.setSpeechRate(rate.toFloat())
                        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) cb.onError("synthesis-failed")
                    }
                } catch (_: Exception) { cb.onError("synthesis-failed") }
            }
        }
        when (st) { 0 -> waiting.add(go); 1 -> go(); else -> main.post { if (id == curId) cb.onError("not-allowed") } }
        return id
    }

    /** React `speechSynthesis.cancel()` — silence at once; the piece in progress reports nothing more. */
    fun cancel() {
        cur = null; curId = ""
        try { tts?.stop() } catch (_: Exception) { }
    }

    /** Free the engine (page left). */
    fun shutdown() {
        cancel(); waiting.clear()
        try { tts?.shutdown() } catch (_: Exception) { }
        tts = null
    }
}

package com.srizwan.task.screens.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.srizwan.task.core.ViewUtils
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** React `ttsState`: `'idle' | 'playing' | 'paused'`. */
enum class TtsState { Idle, Playing, Paused }

/**
 * One voice of the phone's own engine (React `SpeechSynthesisVoice`): [uri] = `voiceURI`, [name], [lang] (e.g. `bn-BD`).
 * The list is filled by the engine step (4-জ-২) through [TtsController.voiceSource].
 */
class TtsVoice(val uri: String, val name: String, val lang: String)

/**
 * Text highlight while reading (React `onHighlight({ a, b, wa, wb })`): [a]..[b] = the sentence (chunk) in the text,
 * [wa]..[wb] = the current word (-1 = no word known). The drawing of it comes in 4-জ-৩.
 */
class TtsHighlight(val a: Int, val b: Int, val wa: Int, val wb: Int)

/** React `XENG[eng]` as `speak` needs it: [short] = short service name, [langs] = languages it can read (`bn/ar/en`). Filled by 4-ঝ-২. */
class TtsXInfo(val short: String, val langs: Set<String>)

/**
 * What the controller needs from the page (React `new TtsController({ getCfg, getText, getSel, canPlay, setCaret, scrollRatio, ... })`).
 * `getVoices` / `refreshVoices` / `onHighlight` / `onState` are not here — the voices and the highlight live in [TtsController] itself
 * (compose state), and `ttsState` is [TtsController.state].
 */
class TtsDeps(
    /** For the temp files of the online audio (`cacheDir`). */
    val appCtx: Context,
    /** For the timers (React `setTimeout`). */
    val scope: CoroutineScope,
    /** `getCfg()` — latest config. */
    val getCfg: () -> JSONObject,
    /** `getText()` — text of the editor. */
    val getText: () -> String,
    /** `getSel()` — selection / cursor of the editor (start, end). */
    val getSel: () -> Pair<Int, Int>,
    /** `canPlay()` — a page is open and its text is ready. */
    val canPlay: () -> Boolean,
    /** `setCaret(pos)` — put the editor cursor (no keyboard opens). */
    val setCaret: (Int) -> Unit,
    /** `scrollRatio(r)` — scroll the editor to this part (0..1) of the text. */
    val scrollRatio: (Float) -> Unit,
    /** `toast(msg, err, ms)`. */
    val toast: (String, Boolean, Long?) -> Unit
)

/**
 * Reading aloud (TTS) state machine — React `view/TtsController.js`. This step (4-জ-১) is the frame and the state:
 * `_set`, `pick`, `rate`, `stop`, `finish`, `pauseHere` (+ the small helpers they call). The parts that read aloud come later and
 * plug into the hooks marked below: phone engine (4-জ-২), highlight (4-জ-৩), start/click (4-জ-৪), Google audio (4-ঝ-১), extra services (4-ঝ-২),
 * the service picker dialog [ask] (4-ঝ-৩, [TtsPicker] / [TtsDialog]).
 *
 * State: [TtsState.Idle] -> [TtsState.Playing] <-> [TtsState.Paused]. When a service fails and the reading stops ([reselect]),
 * the next "play" asks for a service again. Call everything on the main thread (the Android engine callbacks must hop to it first).
 */
class TtsController(private val d: TtsDeps) {
    private companion object {
        const val G_RETRY = 3                              // React `G_RETRY`
        val G_WAIT = longArrayOf(1500, 4000, 9000)         // React `G_WAIT` (ms between retries)
    }

    // ---------------- state (React `this.*`) ----------------
    /** React `this.id` — bumped by every stop/pause so that late answers of an older run are ignored. */
    var id = 0; private set
    /** Pieces to read (React `this.chunks`) and the one being read ([i]); [start] = text position the reading began from. */
    var chunks: List<ViewUtils.TtsChunk> = emptyList()
    var i = 0
    var start = 0
    /** React `this.state` (compose state — the buttons of the page read it; React `setTtsState`). */
    var state by mutableStateOf(TtsState.Idle); private set
    /** React `this.u` — the phone-engine utterance in progress (its id), null = none (4-জ-২). */
    var u: String? = null
    /** React `this.ga` — the Google audio playing now; `this.cache` — pieces fetched ahead (by piece number). */
    var ga: TtsAudio? = null
    val cache = mutableMapOf<Int, TtsAudio>()
    /** React `this.xc` — extra-service audio per piece (fetching / fetched). */
    val xc = mutableMapOf<Int, TtsAudio>()
    /** React `forceG` — fall back to Google audio for the rest of the run; `noG` — Google audio is not usable; `retry` — tries per chunk; `warned` — messages shown once. */
    var forceG = false
    var noG = false
    val retry = mutableMapOf<Int, Int>()
    val warned = mutableMapOf<String, Boolean>()
    /** React `this.wd` — the watchdog timer. */
    var wd: Job? = null
    /** React `this.stale` — the text changed while reading, positions no longer match (4-জ-৩). */
    var stale = false
    /** React `this.reselect` — the next "play" must ask for a service first. */
    var reselect = false

    // ---------------- voices + highlight (React `voices` state, `hlStore`) ----------------
    /** React `voices` — the phone's voice list. */
    var voices by mutableStateOf<List<TtsVoice>>(emptyList()); private set
    /** React `hlStore` value — what is highlighted now, null = nothing (the editor draws it, 4-জ-৩). */
    var hl by mutableStateOf<TtsHighlight?>(null); private set

    // ---------------- hooks filled by later steps ----------------
    /** The phone engine's voices (4-জ-২: `TextToSpeech.getVoices()`; React `speechSynthesis.getVoices()`; empty when there is no engine). */
    var voiceSource: () -> List<TtsVoice> = { device?.voices().orEmpty() }
    /** React `pickVoice(voices, cfg, l)` — the voice for language [l] (`bn/ar/en`) from the list (4-জ-২). */
    var pickVoice: (List<TtsVoice>, JSONObject, String) -> TtsVoice? = TtsEngines::pickVoice
    /** React `cancelDev()` — silence the phone engine (4-জ-২; React: `if (hasTTS) speechSynthesis.cancel()`). */
    var onCancelDev: () -> Unit = { device?.cancel() }
    /** React `ask(cb)` — open the service picker and call `cb` after a choice. The page sets it to [TtsPicker.ask] (4-ঝ-৩); without a page there is no dialog: go on at once. */
    var ask: (() -> Unit) -> Unit = { cb -> cb() }

    /** The phone engine (React `speechSynthesis`); set by the page (4-জ-২). */
    var device: TtsDevice? = null
    /** React `hasTTS`. */
    val hasTTS: Boolean get() = device?.hasTTS == true
    /** React `XENG[eng]` — null when [eng] is not an extra service. */
    var xInfo: (String) -> TtsXInfo? = { id -> TtsServices.xeng(id)?.let { TtsXInfo(it.short, it.langs) } }
    /** React `tsMeta(id).name` — display name of a service for the start toast; null = unknown id ([TtsEngines.tsMeta], 4-ঝ-৩). */
    var engineName: (String) -> String? = { TtsEngines.tsMeta(it)?.name }

    // ---------------- small helpers ----------------
    /** React `_set(s)`. */
    fun set(s: TtsState) { state = s }

    /** React `cancelDev()`. */
    fun cancelDev() = onCancelDev()

    /** React `pick(l)` — voice for language [l]. */
    fun pick(l: String): TtsVoice? = pickVoice(voices, d.getCfg(), l)

    /** React `rate()` — `ttsRate` of the config as a number kept between 0.5 and 2 (not a number or 0 = 1). */
    fun rate(): Double {
        val n = when (val v = d.getCfg().opt("ttsRate")) {
            is Number -> v.toDouble()
            is String -> Regex("^\\s*[+-]?(\\d+\\.?\\d*|\\.\\d+)").find(v)?.value?.trim()?.toDoubleOrNull() ?: 0.0   // parseFloat("1.5x") = 1.5
            else -> 0.0
        }
        return (if (n.isNaN() || n == 0.0) 1.0 else n).coerceIn(0.5, 2.0)
    }

    /** React `setRate()` — speed change takes effect at once on the Google audio. */
    fun setRate() { ga?.setRate(rate()) }

    /** React `resetForce()` — after an engine/voice change the fallback starts again from the first service. */
    fun resetForce() { forceG = false }

    /** React `refreshVoices()` — read the voice list again (page start, and when the engine reports a change — 4-জ-২). */
    fun refreshVoices() {
        voices = try { voiceSource() } catch (_: Exception) { emptyList() }
    }

    /** React `hlClear()`. */
    fun hlClear() { hl = null }

    /** React `onHighlight(h)` — `hlShow` (4-জ-৩) sets it through here. */
    fun setHighlight(h: TtsHighlight?) { hl = h }

    /** React `clearTimeout(this.wd)`. */
    fun clearWd() { wd?.cancel(); wd = null }

    /** React `clearG()` — the Google audio (and the prefetched pieces) is released; then the extra-service audio. */
    fun clearG() {
        killAudio(ga); ga = null
        cache.values.toList().forEach { killAudio(it) }
        cache.clear()
        xc.keys.toList().forEach { dropX(it) }
    }

    /** React `killAudio(a)` — stop one audio and free it. */
    fun killAudio(a: TtsAudio?) { a?.kill() }

    // ---------------- highlight while reading (4-জ-৩) ----------------
    /**
     * React `hlShow(a, b, wa, wb)` — highlight sentence [a]..[b] and word [wa]..[wb] (-1 = no word). False (nothing shown) when the text
     * changed meanwhile ([stale]) or the range no longer fits the text. The editor draws [hl] with [ttsHighlightTransformation].
     */
    fun hlShow(a: Int, b: Int, wa: Int, wb: Int): Boolean {
        val t = d.getText()
        if (stale || b <= a || b > t.length) return false      // text changed: no more highlight
        setHighlight(TtsHighlight(a, b, wa, wb))
        return true
    }

    /**
     * React `markStale()` — the text was edited while reading: the positions no longer match, so the highlight is removed
     * (the next "play" works again). The editor calls it on every user edit ([ViewPageState.onTtsStale]).
     */
    fun markStale() { if (state != TtsState.Idle && !stale) { stale = true; hlClear() } }

    /** React `follow(c)` — on a new piece: cursor goes there, the sentence is highlighted; if it cannot be, the editor scrolls near it (no keyboard opens). */
    fun follow(c: ViewUtils.TtsChunk) {
        d.setCaret(c.off)
        if (hlShow(c.off, c.end, -1, -1)) return
        d.scrollRatio(c.off.toFloat() / maxOf(1, d.getText().length))
    }

    // ---------------- stop / finish / pause ----------------
    /** React `stop()` — everything quiet, back to idle (also on page change, leaving, the stop button). */
    fun stop() {
        reselect = false
        id++
        clearWd()
        cancelDev()
        clearG()
        u = null
        hlClear()
        set(TtsState.Idle)
    }

    /** React `finish()` — the whole text was read: cursor goes back to where the reading began. */
    fun finish() {
        i = 0; u = null; clearG(); hlClear(); set(TtsState.Idle)
        d.setCaret(start)
        d.toast("পড়া শেষ", false, null)
    }

    /**
     * React `pauseHere(i, msg, re = true)` — stop because of a failure: "play" continues from this piece
     * ([re] = true: asks for a service first).
     */
    fun pauseHere(i: Int, msg: String, re: Boolean = true) {
        id++; clearWd(); cancelDev(); clearG()
        this.i = i; reselect = re; set(TtsState.Paused)
        d.toast(msg, true, 9000)
    }

    // ---------------- Google online audio (4-ঝ-১) ----------------
    /**
     * React `gAudio(i, bust)` — the audio object of piece [i] (made once, kept in [cache]; it starts loading at once, which is how the next piece is prefetched).
     * [bust] = add a time stamp to the address (after a failure, so no bad answer is reused).
     */
    fun gAudio(i: Int, bust: Boolean = false): TtsAudio? {
        cache[i]?.let { return it }
        val c = chunks.getOrNull(i) ?: return null
        val url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" + (TtsEngines.G_LANG[c.lang] ?: "bn") +
            "&total=1&idx=0&textlen=" + c.t.length + "&q=" + java.net.URLEncoder.encode(c.t, "UTF-8").replace("+", "%20") +
            (if (bust) "&_=" + System.currentTimeMillis() else "")
        val a = TtsAudio(d.appCtx, d.scope) { TtsAudio.fetch("Google", url) }
        a.preload()
        cache[i] = a
        return a
    }

    /**
     * React `playG(i, id, bust)` — read piece [i] with Google audio. On failure: 3 retries (waiting longer each time) -> then the phone engine
     * (in "auto" mode, if it has a voice) -> otherwise stops at this piece ("play" continues from here).
     */
    fun playG(i: Int, id: Int, bust: Boolean = false) {
        if (id != this.id) return
        val c = chunks.getOrNull(i)
        val a = gAudio(i, bust)
        if (c == null || a == null) { stop(); return }
        ga = a
        a.setRate(rate())
        var settled = false
        fun fail(why: String) {
            if (id != this.id || settled) return
            settled = true; clearWd()
            killAudio(a); cache.remove(i); if (ga === a) ga = null
            val n = (retry[i] ?: 0) + 1
            retry[i] = n
            if (n <= G_RETRY) {
                d.toast("Google অডিও আটকেছে ($why) — আবার চেষ্টা $n/$G_RETRY…", false, G_WAIT[n - 1])
                wd = d.scope.launch { delay(G_WAIT[n - 1]); playG(i, id, true) }
                return
            }
            if (d.getCfg().optString("ttsEngine", "").ifEmpty { "auto" } == "auto" && hasTTS && pick(c.lang) != null) {
                noG = true
                d.toast("Google অনলাইন অডিও বারবার ব্যর্থ — বাকিটা ফোনের TTS-এ চলছে", false, 4000)
                speak(i, id); return
            }
            pauseHere(i, "Google অডিও পাওয়া যাচ্ছে না ($why) — সার্ভিসটি সাময়িক ব্লক/সীমিত হতে পারে। কিছুক্ষণ পরে বা অন্য সার্ভিস বেছে নিয়ে \"চালান\" চাপুন — এই জায়গা থেকেই চলবে। (স্থায়ী সমাধান: সেটিংসে \"শুধু ফোনের TTS\" + ফোনে বাংলা ভয়েস ডেটা)")
        }
        a.play(TtsAudio.Cb(
            onStart = { clearWd(); if (id == this.id) follow(c) },
            onEnd = { if (id == this.id) { cache.remove(i); speak(i + 1, id) } },
            onError = { fail(it) }
        ))
        clearWd()
        wd = d.scope.launch { delay(15000); fail("সাড়া আসেনি") }   // network hangs
        if (i + 1 < chunks.size) gAudio(i + 1)                       // next piece is loaded ahead
    }

    // ---------------- extra services (4-ঝ-২) ----------------
    /** React `dropX(i)` — cancel and delete the fetched / being-fetched audio of piece [i]. */
    fun dropX(i: Int) { xc.remove(i)?.let { killAudio(it) } }

    /** React `xFetch(i)` — start fetching the audio of piece [i] (the one already started, if any). */
    fun xFetch(i: Int): TtsAudio? {
        xc[i]?.let { return it }
        val c = chunks.getOrNull(i) ?: return null
        val cfg = d.getCfg()
        val eng = cfg.optString("ttsEngine", "")
        val a = TtsAudio(d.appCtx, d.scope) { TtsServices.xSrc(cfg, eng, c) }
        a.preload()
        xc[i] = a
        return a
    }

    /**
     * React `playX(i, id, bust)` — read piece [i] with the chosen extra service. On failure: 3 retries (waiting longer each time) -> then stops
     * at this piece; the next "play" asks for a service again ([pauseHere] with `re = true`).
     */
    fun playX(i: Int, id: Int, bust: Boolean = false) {
        if (id != this.id) return
        val eng = d.getCfg().optString("ttsEngine", "")
        val m = TtsServices.xeng(eng)
        val c = chunks.getOrNull(i)
        if (m == null || c == null) { stop(); return }
        if (bust) dropX(i)
        val a = xFetch(i)
        if (a == null) { stop(); return }
        var settled = false
        fun fail(why: String) {
            if (id != this.id || settled) return
            settled = true; clearWd()
            if (ga === a) ga = null
            dropX(i)
            val n = (retry[i] ?: 0) + 1
            retry[i] = n
            if (n <= G_RETRY) {
                d.toast(m.short + " আটকেছে ($why) — আবার চেষ্টা $n/$G_RETRY…", false, G_WAIT[n - 1])
                wd = d.scope.launch { delay(G_WAIT[n - 1]); playX(i, id, true) }
                return
            }
            pauseHere(i, m.short + " থেকে অডিও পাওয়া যাচ্ছে না ($why)। \"চালান\" চাপলে অন্য সার্ভিস বেছে নিতে পারবেন — এই জায়গা থেকেই চলবে।")
        }
        clearWd()
        wd = d.scope.launch { delay(20000); fail("সাড়া আসেনি") }
        ga = a
        a.setRate(rate())
        a.play(TtsAudio.Cb(
            onStart = { clearWd(); if (id == this.id) follow(c) },
            onEnd = { if (id == this.id) { dropX(i); speak(i + 1, id) } },
            onError = { fail(it) }
        ))
        if (i + 1 < chunks.size) xFetch(i + 1)         // next piece is fetched ahead
    }

    // ---------------- reading with the phone's engine ----------------
    /**
     * React `speak(i, id)` — read piece [i] (run [id] — a stop/pause makes older runs quit). Picks the service: an extra service when chosen,
     * else the phone's engine (with a voice for the language) or Google. In "auto" mode a failing / silent phone engine falls back to Google.
     */
    fun speak(i: Int, id: Int) {
        if (id != this.id) return
        if (i >= chunks.size) { finish(); return }
        this.i = i
        val c = chunks[i]
        val cfg = d.getCfg()
        var eng = cfg.optString("ttsEngine", "").ifEmpty { "auto" }
        val x = xInfo(eng)
        if (x != null) {                              // extra service (Edge / Pollinations / server / Watson / Fish)
            if (c.lang in x.langs) { playX(i, id); return }
            if (warned["x" + c.lang] != true) {       // this service cannot read this language: only this piece goes to the phone engine / Google
                warned["x" + c.lang] = true
                val n = TtsEngines.TTS_NAME[c.lang] ?: c.lang
                d.toast(x.short + "-এ " + n + " নেই — " + n + " অংশ ফোনের TTS/Google-এ পড়া হচ্ছে", false, 4500)
            }
            eng = "auto"
        }
        val mode = eng
        val v = if (hasTTS) pick(c.lang) else null
        val wantG = forceG || !hasTTS || v == null
        val useG = mode == "google" || (mode == "auto" && wantG && !(noG && hasTTS && v != null))
        if (useG) { playG(i, id); return }
        val dev = device
        if (!hasTTS || dev == null) { stop(); d.toast("এই ফোনে টেক্সট-টু-স্পিচ ইঞ্জিন নেই — সেটিংসে ইঞ্জিন \"স্বয়ংক্রিয়\" বা \"Google অনলাইন\" করুন", true, 6000); return }

        fun fallbackToG(why: String): Boolean {
            if (id != this.id || mode != "auto") return false
            forceG = true; clearWd(); cancelDev()
            d.toast("ফোনের TTS $why — Google অনলাইন ভয়েস ব্যবহার হচ্ছে", false, 3500)
            playG(i, id)
            return true
        }
        if (v == null && voices.isNotEmpty() && warned[c.lang] != true) {
            warned[c.lang] = true
            d.toast((TtsEngines.TTS_NAME[c.lang] ?: c.lang) + " ভয়েস পাওয়া যায়নি — ফোনের টেক্সট-টু-স্পিচ সেটিংসে ভয়েস ডেটা ডাউনলোড করুন", true, 6000)
        }
        var started = false
        u = dev.speak(c.t, v, TtsEngines.TTS_LANG[c.lang] ?: "bn-BD", rate(), TtsDevice.Callbacks(
            onStart = { started = true; clearWd(); if (id == this.id) follow(c) },
            onBoundary = { ci ->                      // word boundary (not every engine sends it): the current word is highlighted darker
                if (id == this.id && !stale) {
                    var wd: ViewUtils.TtsWord? = null
                    for (w in c.w) { if (w.p <= ci) wd = w else break }
                    if (wd != null) hlShow(c.off, c.end, wd.o, wd.o + wd.l)
                }
            },
            onEnd = { clearWd(); if (id == this.id) speak(i + 1, id) },
            onError = { e ->
                if (id == this.id && !fallbackToG("সাড়া দেয়নি ($e)")) {
                    stop()
                    d.toast("পড়তে সমস্যা: " + e + (if (e == "language-unavailable") " (এই ভাষার ভয়েস নেই)" else ""), true, null)
                }
            }
        ))
        if (mode == "auto") {                         // some phones stay silent without any error: no sign of life in 4 s -> Google
            clearWd()
            wd = d.scope.launch { delay(4000); if (id == this.id && !started) fallbackToG("সাড়া দিচ্ছে না") }
        }
    }

    // ---------------- start / pause / play ----------------
    /** React `startFrom(i)` — (re)start reading from piece [i] as a new run. */
    fun startFrom(i: Int) {
        id++
        val run = id
        retry.clear(); noG = false                // a new start / play: Google is tried again from the beginning
        clearWd()
        cancelDev()
        set(TtsState.Playing)
        d.scope.launch { delay(80); speak(i, run) }   // speaking right after a cancel is swallowed by some engines
    }

    /**
     * React `click()` — the play / pause button. Playing -> pause (stops; "play" restarts this piece from its beginning, because a real pause
     * is unreliable on Android); paused -> continue (after a failure it asks for a service first); idle -> read the selection,
     * or from the cursor to the end (cursor at the end = from the start).
     */
    fun click() {
        if (!d.canPlay()) return
        if (state == TtsState.Playing) {
            id++
            clearWd()
            cancelDev()
            killAudio(ga); ga = null; cache.remove(i)   // the piece starts from its beginning on "play"
            dropX(i)
            set(TtsState.Paused)
            return
        }
        if (state == TtsState.Paused) {
            if (!reselect) { startFrom(i); return }                   // normal pause: go on with the same service
            ask { reselect = false; startFrom(i) }                    // stopped by a failure: choose a service again
            return
        }
        val text = d.getText()
        if (text.isBlank()) { d.toast("পড়ার মতো টেক্সট নেই", true, null); return }
        var (a, b) = d.getSel()
        if (a > b) { val t = a; a = b; b = t }
        a = a.coerceIn(0, text.length); b = b.coerceIn(0, text.length)
        if (a == b) { b = text.length; if (a >= b - 1) a = 0 }        // no selection: cursor to the end (at the end: from the start)
        refreshVoices(); warned.clear()
        chunks = ViewUtils.buildChunks(text, a, b)
        stale = false
        if (chunks.isEmpty()) { d.toast("পড়ার মতো টেক্সট নেই", true, null); return }
        start = a
        val langs = chunks.map { TtsEngines.TTS_NAME[it.lang] ?: it.lang }.distinct().joinToString(" + ")
        ask {                                                          // reading starts only after a service is chosen
            val name = engineName(d.getCfg().optString("ttsEngine", ""))
            d.toast("শোনা শুরু • ভাষা: " + langs + (if (name != null) " • $name" else ""), false, null)
            startFrom(0)
        }
    }
}

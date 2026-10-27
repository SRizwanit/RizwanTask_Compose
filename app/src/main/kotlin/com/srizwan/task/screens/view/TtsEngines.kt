package com.srizwan.task.screens.view

import com.srizwan.task.core.str
import org.json.JSONObject

/** React `tsMeta(id)` — what the service picker shows for one service: [name], [desc] and [tags] (text, kind `ok/warn/err`). */
class TtsMeta(val name: String, val desc: String, val tags: List<Pair<String, String>>)

/**
 * React `view/ttsEngines.js` (4-জ-২ part): language codes of the phone engine and the voice list helpers;
 * (4-ঝ-৩ part): the picker data — `TS_BASE`, `TS_ORDER`, `tsMeta`, `tsValidate`.
 * The extra services (`XENG`, Edge/Watson/Fish ...) are in [TtsServices] (4-ঝ-২).
 */
object TtsEngines {
    /** React `TTS_LANG` — language code for the phone's TTS. */
    val TTS_LANG = mapOf("bn" to "bn-BD", "ar" to "ar-SA", "en" to "en-US")
    /** React `G_LANG` — language code for Google (4-ঝ-১). */
    val G_LANG = mapOf("bn" to "bn", "ar" to "ar", "en" to "en")
    /** React `TTS_NAME`. */
    val TTS_NAME = mapOf("bn" to "বাংলা", "ar" to "আরবি", "en" to "ইংরেজি")

    /** React `vLang(v)` — `bn_BD` / `bn-BD` -> `bn-bd`. */
    fun vLang(v: TtsVoice): String = v.lang.replace('_', '-').lowercase()

    /** React `voicesFor(voices, l)` — voices of language [l] (`bn/ar/en`). */
    fun voicesFor(voices: List<TtsVoice>, l: String): List<TtsVoice> = voices.filter { vLang(it).startsWith(l) }

    /** React `pickVoice(voices, cfg, l)` — the voice chosen in settings (`cfg.tts[l]`), else the exact-region one (`bn-BD`), else the first of that language. */
    fun pickVoice(voices: List<TtsVoice>, cfg: JSONObject, l: String): TtsVoice? {
        val want = cfg.optJSONObject("tts")?.optString(l, "").orEmpty()
        if (want.isNotEmpty()) voices.firstOrNull { it.uri == want || it.name == want }?.let { return it }
        val list = voicesFor(voices, l)
        val exact = TTS_LANG[l]?.lowercase()
        return list.firstOrNull { vLang(it) == exact } ?: list.firstOrNull()
    }

    /** One option of the voice dropdown (React `{ value, label }`). */
    class VoiceOption(val value: String, val label: String)

    /** React `voiceOptions(voices, l)` — dropdown options; the first one "" = automatic. */
    fun voiceOptions(voices: List<TtsVoice>, l: String): List<VoiceOption> {
        val list = voicesFor(voices, l)
        val first = VoiceOption("", if (list.isNotEmpty()) "স্বয়ংক্রিয়" else "এই ডিভাইসে " + (TTS_NAME[l] ?: l) + " ভয়েস দেখা যাচ্ছে না")
        return listOf(first) + list.map { VoiceOption(it.uri.ifEmpty { it.name }, it.name + " (" + it.lang + ")") }
    }

    /** React `voiceValue(opts, want)` — the current value if it is in the list, else "" (= automatic). */
    fun voiceValue(opts: List<VoiceOption>, want: String): String = if (opts.any { it.value == want }) want else ""

    // ---------------- service picker data (4-ঝ-৩) ----------------

    /** React `TS_BASE` — the three built-in choices (the extra services come from [TtsServices.XENG]). */
    val TS_BASE: Map<String, TtsMeta> = linkedMapOf(
        "auto" to TtsMeta(
            "স্বয়ংক্রিয় (প্রস্তাবিত)", "ফোনের TTS আগে; ভয়েস না থাকলে বা সাড়া না দিলে Google অনলাইন।",
            listOf("কী লাগে না" to "ok")
        ),
        "device" to TtsMeta(
            "ফোনের TTS", "ডিভাইসের নিজস্ব ইঞ্জিন — অফলাইনে চলে, আনলিমিটেড।",
            listOf("অফলাইন" to "ok", "কী লাগে না" to "ok", "ভয়েস ডিভাইসের ওপর নির্ভর" to "warn")
        ),
        "google" to TtsMeta(
            "Google অনলাইন", "Google Translate-এর অডিও — ভাষা নিজে চেনা হয়, ভয়েস বাছা যায় না।",
            listOf("ইন্টারনেট লাগে" to "warn", "অনানুষ্ঠানিক — সীমিত হতে পারে" to "warn")
        )
    )

    /** React `TS_ORDER` — the order of the cards in the picker. */
    val TS_ORDER: List<String> = listOf("auto", "device", "google", "edge", "poll", "server", "watson", "fish")

    /** React `tsMeta(id)` — null when [id] is unknown. */
    fun tsMeta(id: String): TtsMeta? = TS_BASE[id] ?: TtsServices.xeng(id)?.let { TtsMeta(it.name, it.desc, it.tags) }

    /**
     * React `tsValidate(id, cfg)` — is the config of the chosen service complete before "শুনুন" is pressed? Returns the error message ("" = fine).
     * [hasTTS] = the phone has a TTS engine (React `hasTTS`).
     */
    fun tsValidate(id: String, cfg: JSONObject, hasTTS: Boolean): String {
        val x = cfg.optJSONObject("ttsx")?.optJSONObject(id) ?: JSONObject()
        fun has(k: String) = !x.str(k).isNullOrEmpty()
        if (id == "device" && !hasTTS) return "এই ফোনে টেক্সট-টু-স্পিচ ইঞ্জিন নেই — অন্য সার্ভিস বাছুন"
        if (id == "watson" && !(has("url") && has("key"))) return "IBM Watson-এর Service URL ও API Key দিন"
        if (id == "fish" && !has("key")) return "Fish Audio-র API Key দিন"
        if (id == "poll" && !x.str("tpl").orEmpty().contains("{text}")) return "URL টেমপ্লেটে {text} থাকতে হবে"
        if (id == "server" && !x.str("url").orEmpty().contains("{text}")) return "URL টেমপ্লেটে {text} থাকতে হবে"
        return ""
    }
}

package com.srizwan.task.core

import com.srizwan.task.screens.admin.GhAccounts
import com.srizwan.task.screens.admin.isDefaultPrompt
import org.json.JSONObject

/**
 * React `view/config.js` -> Kotlin.
 *
 * The saved config is kept as a [JSONObject] on purpose: React spreads `{ ...DEFAULTS, ...saved }`, so unknown keys
 * written by older/newer versions survive a load/save round trip. Values that React kept in `localStorage` live in [Prefs]
 * (same key names). The extension-storage mirror (`mirror`) is browser-only and is not ported.
 *
 * Inputs for [loadCfg]: [book] = the `books` row, [settings] = the `app_settings` row (both as received from Supabase).
 */
object ViewConfig {

    /** JS `(v || '').trim().replace(/^\/+|\/+$/g, '')` */
    fun trimSl(v: Any?): String = ViewUtils.trimSl(v)

    /* ---------- Extra listening services: defaults (key/voice/URL stay on the device, never in the repo) ---------- */
    fun ttsxDef(): JSONObject = JSONObject()
        .put("edge", jsonOf("bn" to "bn-BD-NabanitaNeural", "ar" to "ar-SA-ZariyahNeural", "en" to "en-US-AriaNeural", "ver" to ""))
        .put("poll", jsonOf("voice" to "nova", "tpl" to "https://text.pollinations.ai/{text}?model=openai-audio&voice={voice}"))
        .put("server", jsonOf("url" to "http://localhost:5000/?text={text}"))
        .put("watson", jsonOf("url" to "", "key" to "", "en" to "en-US_MichaelV3Voice", "ar" to "ar-MS_OmarVoice"))
        .put("fish", jsonOf("key" to "", "model" to "s1", "ref" to ""))

    private fun emptyTts(): JSONObject = jsonOf("bn" to "", "ar" to "", "en" to "")

    fun defaults(): JSONObject = jsonOf(
        "repo" to "SunniPedia/pdftojpg", "branch" to "", "token" to "",
        "pagesDir" to "pages", "textsDir" to "texts", "proofFile" to "proofed.json", "book" to "", "proofDir" to "proofed",
        "fs" to 20, "swap" to false, "zoom" to 100, "last" to "", "view" to null, "autoHead" to false,
        "ttsRate" to 1, "ttsEngine" to "auto", "tts" to emptyTts()
    )

    /* ---------- API (Gemini) defaults ---------- */
    val API_NAME: Map<Int, String> = mapOf(1 to "এপি আই - ১", 2 to "এপি আই - ২")

    /** Sub-title + default prompt for API 1 (OCR) and API 2 (proofing). Text is verbatim from React. */
    class ApiDef(val sub: String, val prompt: String)

    val API_DEF: Map<Int, ApiDef> = mapOf(
        1 to ApiDef(
            sub = "ছবি থেকে টেক্সট (OCR)",
            prompt = listOf(
                "এই ছবিটি একটি কিতাবের পেইজ। ছবির সব লেখা হুবহু টেক্সট আকারে তুলে দাও।",
                "নিয়ম:",
                "১) আরবি অংশ আরবি অক্ষরে, বাংলা অংশ বাংলা অক্ষরে লিখবে; এক শব্দে দুই লিপি মেশাবে না।",
                "২) আরবির হরকত/ইরাব ছবিতে থাকলে রাখবে।",
                "৩) পেইজ নম্বর, পাদটীকা ও মার্জিনের লেখাও যেমন আছে তেমন রাখবে।",
                "৪) নিজে থেকে কিছু যোগ, বাদ বা সংশোধন করবে না; পড়া না গেলে [অস্পষ্ট] লিখবে।",
                "৫) কোনো ভূমিকা, মন্তব্য বা markdown চিহ্ন (**, #, -) ব্যবহার করবে না; শুধু টেক্সট দেবে।"
            ).joinToString("\n")
        ),
        2 to ApiDef(
            sub = "টেক্সট প্রুফিং",
            prompt = listOf(
                "নিচে একটি কিতাবের পেইজের ছবি (থাকলে) এবং সেই ছবি থেকে OCR করা টেক্সট দেওয়া আছে। ছবির সাথে মিলিয়ে টেক্সটের ভুল সংশোধন করো: বানান, যুক্তাক্ষর, আরবি হরকত, বাদ পড়া বা বাড়তি শব্দ, ভুল লাইন ভাঙা।",
                "নিয়ম:",
                "১) অর্থ, ভাষা বা শব্দচয়ন বদলাবে না; নিজের থেকে কিছু যোগ করবে না।",
                "২) আরবি অংশ আরবিতে, বাংলা অংশ বাংলায় থাকবে; লিপি মেশাবে না।",
                "৩) অনুচ্ছেদ ও লাইনের কাঠামো যতটা সম্ভব আগের মতো রাখবে।",
                "৪) লাইনের শুরুতে হেডিং চিহ্ন হিসেবে 1 (স্পেস ছাড়া) থাকলে সেটা যেমন আছে তেমনই রাখবে।",
                "৫) markdown চিহ্ন ব্যবহার করবে না।",
                "৬) কোনো মন্তব্য বা ব্যাখ্যা ছাড়া শুধু সংশোধিত পূর্ণ টেক্সট দেবে।"
            ).joinToString("\n")
        )
    )

    val TTS_ENGINES: List<String> = listOf("auto", "device", "google", "edge", "poll", "server", "watson", "fish")

    /** Storage keys: separate per user + book. */
    class Keys(val CK: String, val DK: String)

    fun keysFor(uid: String, bookId: String): Keys {
        val sfx = ":$uid:$bookId"
        return Keys(CK = "kitab_proof_cfg_v1$sfx", DK = "kitab_proof_drafts_v1$sfx")
    }

    /** `{ ...base, ...over }` for one level (null/non-object `over` leaves base untouched). */
    private fun spread(base: JSONObject, over: Any?): JSONObject {
        val out = JSONObject(base.toString())
        if (over is JSONObject) for (k in over.keys()) out.put(k, over.opt(k))
        return out
    }

    /** JS truthiness of a string field: "" / missing / null -> "" (whitespace-only is still truthy, as in JS). */
    private fun JSONObject.truthyStr(key: String): String = str(key).orEmpty()

    /**
     * Saved config (from [Prefs], key [ck]) + repo / token / book / Gemini values coming from Supabase.
     * [book] = books row; [settings] = app_settings row (may be null).
     */
    fun loadCfg(ck: String, book: JSONObject, settings: JSONObject?): JSONObject {
        var cfg = defaults()
        try {
            val saved = JSONObject(Prefs.getString(ck, null)?.takeIf { it.isNotEmpty() } ?: "{}")
            cfg = spread(cfg, saved)
        } catch (_: Exception) { /* ignore: bad JSON -> defaults */ }

        if (cfg.str("repo").isNullOrEmpty()) cfg.put("repo", defaults().getString("repo"))
        if (cfg.opt("tts") !is JSONObject) cfg.put("tts", emptyTts())
        if (cfg.str("ttsEngine") !in TTS_ENGINES) cfg.put("ttsEngine", "auto")

        run {   // extra services config: defaults fill in anything missing from older saves
            val o = cfg.opt("ttsx") as? JSONObject
            val def = ttsxDef()
            val merged = JSONObject()
            for (k in def.keys()) merged.put(k, spread(def.getJSONObject(k), o?.opt(k)))
            cfg.put("ttsx", merged)
        }

        // Saved values of API 1 and 2 (older saves without this slot get defaults)
        for (n in 1..2) {
            val base = jsonOf(
                "key" to "", "model" to "gemini-3.5-flash-lite", "temp" to 0,
                "prompt" to API_DEF.getValue(n).prompt, "sendImg" to true, "auto" to true
            )
            cfg.put("api$n", spread(base, cfg.opt("api$n")))
        }
        // One-time migration: previously saved old default model (gemini-3.5-flash) becomes the new default flash-lite
        if (cfg.str("apiMig1").let { it == null || it == "" || it == "0" || it == "false" }) {
            for (n in 1..2) {
                val a = cfg.getJSONObject("api$n")
                if (a.str("model") == "gemini-3.5-flash") cfg.put("api$n", spread(a, jsonOf("model" to "gemini-3.5-flash-lite")))
            }
            cfg.put("apiMig1", 1)
        }

        // ---------- Config from Supabase (repo, token, book, Gemini) ----------
        val b = book
        val st = settings ?: JSONObject()
        // Blank on the book -> admin default (app_settings); blank there too -> SunniPedia/pdftojpg
        // Update_admin 5-খ: the book's chosen GitHub account (books.gh_account; none / deleted = Default) gives token / Username / Repo:
        // book's own value -> account's value -> admin default -> SunniPedia/pdftojpg
        val gh = GhAccounts.resolve(st, b)
        cfg.put("repo", "${gh.username}/${gh.repo}")
        cfg.put("branch", (b.truthyStr("branch").ifEmpty { st.truthyStr("branch") }).trim())
        cfg.put("book", trimSl(b.truthyStr("folder").ifEmpty { b.str("name").orEmpty() }))   // -> <book>_pages, <book>_texts, <book>_proofed.json
        if (st.truthyStr("pages_dir").isNotEmpty()) cfg.put("pagesDir", trimSl(st.str("pages_dir")))
        if (st.truthyStr("texts_dir").isNotEmpty()) cfg.put("textsDir", trimSl(st.str("texts_dir")))
        if (st.truthyStr("proof_file").isNotEmpty()) cfg.put("proofFile", st.str("proof_file").orEmpty().trim().replace(Regex("^/+"), ""))
        if (st.truthyStr("proof_dir").isNotEmpty()) cfg.put("proofDir", trimSl(st.str("proof_dir")))
        if (gh.token.isNotEmpty()) cfg.put("token", gh.token)   // the account's token (Default account = app_settings.github_token)
        for (n in 1..2) {
            val c = JSONObject(cfg.getJSONObject("api$n").toString())
            if (st.truthyStr("gemini_api_key").isNotEmpty()) c.put("key", st.str("gemini_api_key"))
            if (st.truthyStr("gemini_model").isNotEmpty()) c.put("model", st.str("gemini_model"))
            val t = st.str("gemini_temp")
            if (t != null && t != "") c.put("temp", t.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0)
            // Update_admin 5-খ: the book's own prompt (books.prompt_ocr / prompt_proof) wins; blank / "Default" = the admin Settings prompt
            val bp = if (n == 1) b.truthyStr("prompt_ocr") else b.truthyStr("prompt_proof")
            val p = if (!isDefaultPrompt(bp)) bp.trim() else if (n == 1) st.truthyStr("prompt_ocr") else st.truthyStr("prompt_proof")
            if (p.isNotEmpty()) c.put("prompt", p)
            cfg.put("api$n", c)
        }
        return cfg
    }

    /** Saved drafts map { "<repo>[@book]/<stem>": text } (same JSON shape as React). */
    fun loadDrafts(dk: String): JSONObject = try {
        JSONObject(Prefs.getString(dk, null)?.takeIf { it.isNotEmpty() } ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun saveCfgTo(ck: String, cfg: JSONObject) {
        val v = try { cfg.toString() } catch (_: Exception) { return }
        try { Prefs.putString(ck, v) } catch (_: Exception) { /* ignore */ }
        // React also mirrored to the browser-extension storage here; not applicable on Android.
    }

    /**
     * Book prefix + folders.
     * Real folder/file name = book name + "_" + base name (e.g. bukhari_pages, bukhari_texts, bukhari_proofed.json).
     * With a blank book name it is pages / texts / proofed.json as before. One repo can hold several books.
     */
    class RepoPaths(
        val pagesDir: String, val textsDir: String, val proofDirName: String, val proofName: String,
        /** e.g. proofed/bukhari_proofed.json */
        val proofPath: String,
        /** old location: repo root */
        val legacyProofPath: String
    )

    fun repoPaths(cfg: JSONObject): RepoPaths {
        val b = trimSl(cfg.str("book"))
        val pre = if (b.isNotEmpty()) "${b}_" else ""
        val pagesDir = pre + trimSl(cfg.str("pagesDir").takeUnless { it.isNullOrEmpty() } ?: "pages")
        val textsDir = pre + trimSl(cfg.str("textsDir").takeUnless { it.isNullOrEmpty() } ?: "texts")
        val proofDirName = if (cfg.opt("proofDir") is String) trimSl(cfg.getString("proofDir")) else "proofed"
        val proofName = pre + (cfg.str("proofFile").takeUnless { it.isNullOrEmpty() } ?: "proofed.json").trim().replace(Regex("^/+"), "")
        return RepoPaths(
            pagesDir = pagesDir, textsDir = textsDir, proofDirName = proofDirName, proofName = proofName,
            proofPath = (if (proofDirName.isNotEmpty()) "$proofDirName/" else "") + proofName,
            legacyProofPath = proofName
        )
    }

    fun connSig(cfg: JSONObject): String {
        val p = repoPaths(cfg)
        return listOf(cfg.str("repo").orEmpty(), cfg.str("branch").orEmpty(), p.pagesDir, p.textsDir, p.proofPath).joinToString("|")
    }

    /** Drafts are separate per book (blank name = the old key). */
    fun draftKey(cfg: JSONObject, stem: String): String {
        val b = trimSl(cfg.str("book"))
        return cfg.str("repo").orEmpty() + (if (b.isNotEmpty()) "@$b" else "") + "/" + stem
    }
}

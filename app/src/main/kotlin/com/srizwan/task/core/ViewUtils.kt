package com.srizwan.task.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.Collator
import java.util.Locale

/**
 * React `view/utils.js` -> Kotlin (pure helpers).
 *
 * Not ported here:
 *  - `isHeading` / `markHeadings` (auto heading) -> here below (step 4-cha-2, uses [AR_RE]).
 *  - `copyPlain`  -> existing `copyText(ctx, text)` in Intents.kt.
 *  - `openTab`    -> existing `openUrl(ctx, url)` in Intents.kt.
 *  - `encU`       -> existing `enc()` in Json.kt.
 *
 * Local storage (`view/storage.js`) is covered by [Prefs]: `localStorage` -> SharedPreferences, same key names.
 * `KP_EXT`, `mirror`, `flushMirror`, `restoreFromExt`, `installFlushListeners` are browser-extension only and are
 * dropped; `requestPersist` is [ViewStorage.requestPersist] (no-op, SharedPreferences is never evicted).
 */
object ViewStorage {
    /** navigator.storage.persist() — not needed on Android (app data is not cleared by the system like browser storage). */
    fun requestPersist() { /* no-op */ }
}

object ViewUtils {

    /** JS `\s` (incl. NBSP, BOM, unicode spaces) as a regex character-class body. Java's `\s` is ASCII-only. */
    private const val WS = "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff"
    private val NON_WS = Regex("[^$WS]+")
    private val WS_RUN = Regex("[$WS]+")

    /* ---------- Base64 (UTF-8 text / raw bytes) ---------- */
    fun toB64(str: String): String = Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** Whitespace inside the base64 (GitHub API wraps lines) is ignored. */
    fun fromB64(b64: String): String =
        String(Base64.decode(b64.replace(Regex("\\s"), ""), Base64.DEFAULT), Charsets.UTF_8)

    fun bytesToB64(buf: ByteArray): String = Base64.encodeToString(buf, Base64.NO_WRAP)

    /* ---------- Paths / numbers / strings ---------- */
    /** Each path segment percent-encoded, "/" kept. */
    fun epath(p: String): String = p.split("/").joinToString("/") { enc(it) }

    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    private val NAT_PART = Regex("\\d+|\\D+")

    /** localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }) — "page2" < "page10", case/accents ignored. */
    fun natCmp(a: String, b: String): Int {
        val pa = NAT_PART.findAll(a).map { it.value }.toList()
        val pb = NAT_PART.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]; val y = pb[i]
            val xd = x[0].isAsciiDigit(); val yd = y[0].isAsciiDigit()
            val c = when {
                xd && yd -> BigInteger(x).compareTo(BigInteger(y))
                xd -> -1          // digits sort before letters
                yd -> 1
                else -> collator.compare(x, y)
            }
            if (c != 0) return c
        }
        return pa.size.compareTo(pb.size)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    /** Last number in a file stem ("page_012" -> 12); null when there is none (JS NaN). */
    fun numOf(stem: String): Long? = Regex("\\d+").findAll(stem).lastOrNull()?.value?.toLongOrNull()

    /** ASCII digits -> Bengali digits. */
    fun bnNum(n: Any?): String = n.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

    /** `(v || '').trim().replace(/^\/+|\/+$/g, '')` */
    fun trimSl(v: Any?): String {
        val s = if (v == null || v == org.json.JSONObject.NULL) "" else v.toString()
        return s.trim().replace(Regex("^/+|/+$"), "")
    }

    /** "https://github.com/owner/repo.git" -> "owner/repo" */
    fun normRepo(v: String): String = v.trim()
        .replace(Regex("^https?://github\\.com/", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\.git$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^/+|/+$"), "")

    /** textarea turns \r\n into \n by itself; comparisons always use this normal form. */
    fun normNl(v: Any?): String = (v?.toString() ?: "").replace(Regex("\r\n?"), "\n")

    /** setTimeout promise -> suspend delay (cancelled with the coroutine; replaces the AbortSignal). */
    suspend fun sleep(ms: Long) = delay(ms)

    val MIME_BY_EXT: Map<String, String> = mapOf("jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp")

    fun extOf(name: String): String = name.substringAfterLast('.').lowercase()

    /** toPngBlob: any image bytes -> PNG bytes (returned as is when already PNG). Throws when it cannot decode. */
    fun toPngBytes(bytes: ByteArray): ByteArray {
        val isPng = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        if (isPng) return bytes
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalStateException("PNG বানানো যায়নি")
        val out = ByteArrayOutputStream()
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IllegalStateException("PNG বানানো যায়নি")
        return out.toByteArray()
    }

    /* ---------- Language detection ---------- */
    /** Arabic / Persian letters */
    val AR_RE = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")

    // ---------------- auto heading (React `isHeading`, `markHeadings`) ----------------
    /** At least one letter (not only digits / signs). */
    private val BN_LET = Regex("[\\u0985-\\u09B9\\u09CE\\u09DC-\\u09DF A-Za-z]")
    /** The previous sentence has ended. */
    private val SENT_RE = Regex("[।.?!][\"”’'\\)\\]]*\$")
    private val KEY_RE = Regex("^(দলিল|অধ্যায়|পরিচ্ছেদ|অনুচ্ছেদ|ভূমিকা|মুখবন্ধ|সূচিপত্র|উপসংহার|পর্ব|প্রশ্ন|উত্তর|প্রসঙ্গ|বিষয়)(?![\\u0980-\\u09FF])")
    private val HEAD_1 = Regex("^1[\\u0980-\\u09FF]")
    private val HEAD_END = Regex("[।.?!,;][\"”’'\\)\\]]*\$")
    private val DASH_END = Regex("[–—\\-]\$")
    private val ANY_DIGIT = Regex("[\\d\\u09E6-\\u09EF]")
    private val WS_SPLIT = Regex("\\s+")

    /** React `isHeading(line, prev, first)` — does [line] look like a heading (short, no sentence end, after a finished sentence / blank line / at the top)? */
    fun isHeading(line: String, prev: String?, first: Boolean): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 80) return false
        if (AR_RE.containsMatchIn(t) || !BN_LET.containsMatchIn(t)) return false
        if (HEAD_1.containsMatchIn(t)) return false                       // already marked
        if (HEAD_END.containsMatchIn(t)) return false
        if (KEY_RE.containsMatchIn(t)) return true
        if (t.length > 50 || t.split(WS_SPLIT).size > 8) return false
        if (DASH_END.containsMatchIn(t) && !ANY_DIGIT.containsMatchIn(t)) return false
        if (t.endsWith(":") && t.split(WS_SPLIT).size > 4) return false
        val p = (prev ?: "").trim()
        return first || p.isEmpty() || SENT_RE.containsMatchIn(p)
    }

    class Heads(val text: String, val n: Int)

    /** React `markHeadings(text)` — a `1` (no space) before every heading line; returns the new text and how many were marked. */
    fun markHeadings(text: String): Heads {
        val lines = text.split(Regex("\r?\n"))
        var n = 0
        val out = lines.mapIndexed { i, ln ->
            if (isHeading(ln, if (i > 0) lines[i - 1] else "", i == 0)) { n++; "1" + ln.trim() } else ln
        }
        return Heads(out.joinToString("\n"), n)
    }
    private val BN_RE = Regex("[\\u0980-\\u09FF]")
    private val EN_RE = Regex("[A-Za-z]")

    /* ---------- Listening (TTS): language detection + chunking ---------- */
    /** Max characters per chunk (Google limit is about 200). */
    const val TTS_MAX = 180
    private val TTS_SENT = Regex("[।.?!۔؟][\"”’')\\]]*$")      // sentence end
    private val HEAD_MARK = Regex("^1(?=[\\u0980-\\u09FF])")

    fun wordLang(w: String): String? = when {
        AR_RE.containsMatchIn(w) -> "ar"
        BN_RE.containsMatchIn(w) -> "bn"
        EN_RE.containsMatchIn(w) -> "en"
        else -> null                                           // digits / symbols: stay in the previous language
    }

    /** One word of a chunk: p = start in the chunk text, o = start in the original text, l = length in the original. */
    class TtsWord(val p: Int, val o: Int, val l: Int)

    /** t = chunk text, lang = bn/ar/en, off = start in the original text, end = end in the original. */
    class TtsChunk(var t: String, val lang: String, val off: Int) {
        var end: Int = 0
        val w: MutableList<TtsWord> = mutableListOf()
    }

    /** Split text by language into chunks; each chunk's `off` is its start position in the original text. */
    fun buildChunks(text: String, from: Int = 0, to: Int = text.length): List<TtsChunk> {
        val seg = text.substring(from, to)
        val out = mutableListOf<TtsChunk>()
        var c: TtsChunk? = null
        var last = "bn"; var lastEnd = 0; var inHead = false; var first = true
        val paraGap = Regex("\\n[$WS]*\\n")
        fun fl() { c?.let { if (it.t.isNotBlank()) out.add(it) }; c = null }
        for (m in NON_WS.findAll(seg)) {
            val gap = seg.substring(lastEnd, m.range.first)
            lastEnd = m.range.last + 1
            val nl = first || gap.contains('\n'); first = false
            var w = m.value; var head = false; var skip = 0
            if (nl && HEAD_MARK.containsMatchIn(w)) { w = w.substring(1); head = true; skip = 1 }   // heading mark "1" is not read aloud
            if (nl && inHead) { fl(); inHead = false }
            if (paraGap.containsMatchIn(gap)) fl()                  // blank line = paragraph end
            if (head) { fl(); inHead = true }
            if (w.isEmpty()) continue
            val cur = c
            val lang = wordLang(w) ?: (cur?.lang ?: last)
            if (cur != null && (cur.lang != lang || cur.t.length + w.length + 1 > TTS_MAX)) fl()
            if (c == null) c = TtsChunk("", lang, from + m.range.first)
            val ch = c!!
            ch.t += (if (ch.t.isNotEmpty()) " " else "") + w
            ch.w.add(TtsWord(ch.t.length - w.length, from + m.range.first + skip, w.length))
            ch.end = from + m.range.last + 1
            last = lang
            if (TTS_SENT.containsMatchIn(w)) fl()
        }
        fl()
        return out
    }

    /** Warnings for foreign-script letters / markdown (only reports, never changes the text). */
    fun checkText(text: String): List<String> {
        val p = mutableListOf<String>()
        if (Regex("[\\u0900-\\u0963\\u0966-\\u097F]").containsMatchIn(text)) p.add("দেবনাগরী অক্ষর")   // danda U+0964/0965 is used in Bengali too
        for (tok in text.split(WS_RUN)) {
            if (BN_RE.containsMatchIn(tok) && AR_RE.containsMatchIn(tok)) { p.add("বাংলা-আরবি মিশ্র শব্দ: $tok"); break; }
        }
        if (Regex("\\*\\*|^\\s{0,3}#{1,6}\\s|^\\s*[-*]\\s", RegexOption.MULTILINE).containsMatchIn(text)) p.add("markdown চিহ্ন")
        return p
    }
}

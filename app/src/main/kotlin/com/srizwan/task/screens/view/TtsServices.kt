package com.srizwan.task.screens.view

import android.util.Base64
import com.srizwan.task.core.ViewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One input of a service's settings form (React `XENG[id].fields[n]`): [t] = `sel` (choice), `text`, `pw` (hidden). [opts] = (value, label) pairs for `sel`. */
class TtsXField(val k: String, val t: String, val label: String, val opts: List<Pair<String, String>> = emptyList(), val ph: String = "")

/** React `XENG[id]` — an extra listening service: names, description, tags (text, kind `ok/warn/err`), languages, form fields, hint. The picker dialog (4-ঝ-৩) shows them. */
class TtsXEng(
    val short: String, val name: String, val desc: String,
    val tags: List<Pair<String, String>>, val langs: Set<String>,
    val fields: List<TtsXField>, val hint: String
)

/**
 * React `view/ttsEngines.js` — the extra services (Edge TTS, Pollinations, own server/Piper, IBM Watson, Fish Audio) and how their audio is fetched (4-ঝ-২).
 * On Android the audio always comes as bytes ([xSrc]); [TtsAudio] plays them.
 */
object TtsServices {
    private const val EDGE_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val EDGE_VER = "1-143.0.3650.75"

    private fun vop(vararg a: String): List<Pair<String, String>> = a.map { it to it }
    private fun vop(vararg a: Pair<String, String>): List<Pair<String, String>> = a.toList()

    val XENG: Map<String, TtsXEng> = linkedMapOf(
        "edge" to TtsXEng(
            short = "Edge TTS", name = "Edge TTS (Microsoft)",
            desc = "Microsoft Edge-এর অনলাইন ভয়েস — ফ্রি, কী লাগে না; বাংলা/আরবি/ইংরেজি তিনটিই।",
            tags = listOf("ফ্রি" to "ok", "কী লাগে না" to "ok", "বাংলা আছে" to "ok", "অনানুষ্ঠানিক" to "warn"),
            langs = setOf("bn", "ar", "en"),
            fields = listOf(
                TtsXField("bn", "sel", "ভয়েস — বাংলা", vop("bn-BD-NabanitaNeural" to "Nabanita (বাংলাদেশ, নারী)", "bn-BD-PradeepNeural" to "Pradeep (বাংলাদেশ, পুরুষ)", "bn-IN-TanishaaNeural" to "Tanishaa (ভারত, নারী)", "bn-IN-BashkarNeural" to "Bashkar (ভারত, পুরুষ)")),
                TtsXField("ar", "sel", "ভয়েস — আরবি", vop("ar-SA-ZariyahNeural" to "Zariyah (সৌদি, নারী)", "ar-SA-HamedNeural" to "Hamed (সৌদি, পুরুষ)", "ar-EG-SalmaNeural" to "Salma (মিশর, নারী)", "ar-EG-ShakirNeural" to "Shakir (মিশর, পুরুষ)")),
                TtsXField("en", "sel", "ভয়েস — ইংরেজি", vop("en-US-AriaNeural" to "Aria (US, নারী)", "en-US-GuyNeural" to "Guy (US, পুরুষ)", "en-US-JennyNeural" to "Jenny (US, নারী)", "en-GB-SoniaNeural" to "Sonia (UK, নারী)")),
                TtsXField("ver", "text", "Sec-MS-GEC-Version (উন্নত; ফাঁকা = ডিফল্ট)", ph = EDGE_VER)
            ),
            hint = "এটি Microsoft-এর প্রকাশ্য API নয়, অনানুষ্ঠানিক সংযোগ — Microsoft বদলালে বা ব্লক করলে ব্যর্থ হতে পারে। তখন ওপরের \"Sec-MS-GEC-Version\" নতুন সংস্করণে বদলে দেখুন, নইলে অন্য সার্ভিস বাছুন।"
        ),
        "poll" to TtsXEng(
            short = "Pollinations", name = "Pollinations",
            desc = "ফ্রি AI ভয়েস API — সাইনআপ/কী ছাড়াই; URL-এ টেক্সট দিলে অডিও আসে।",
            tags = listOf("ফ্রি" to "ok", "কী লাগে না" to "ok", "বাংলার মান নিজে পরখ করুন" to "warn"),
            langs = setOf("bn", "ar", "en"),
            fields = listOf(
                TtsXField("voice", "sel", "ভয়েস", vop("alloy", "echo", "fable", "onyx", "nova", "shimmer")),
                TtsXField("tpl", "text", "URL টেমপ্লেট", ph = "https://text.pollinations.ai/{text}?model=openai-audio&voice={voice}")
            ),
            hint = "{text} = পড়ার টেক্সট, {voice} = ভয়েস। কখনো টেক্সট না পড়ে নিজে থেকে উত্তর দিলে {text}-এর আগে একটি নির্দেশ জুড়ে পরখ করুন, যেমন: https://text.pollinations.ai/Read%20aloud%3A%20{text}?model=openai-audio&voice={voice}"
        ),
        "server" to TtsXEng(
            short = "নিজস্ব সার্ভার", name = "Piper / নিজস্ব সার্ভার (URL)",
            desc = "নিজের কম্পিউটার/সার্ভারে চালানো যেকোনো TTS (যেমন Piper) — আনলিমিটেড ও প্রাইভেট।",
            tags = listOf("আনলিমিটেড" to "ok", "নিজে সেটআপ লাগে" to "warn"),
            langs = setOf("bn", "ar", "en"),
            fields = listOf(TtsXField("url", "text", "URL টেমপ্লেট (GET → অডিও)", ph = "http://localhost:5000/?text={text}")),
            hint = "{text} = টেক্সট, {lang} = bn/ar/en। উদাহরণ (Piper HTTP সার্ভার): http://localhost:5000/?text={text}। HTTPS পেইজ থেকে http সার্ভার ব্রাউজার ব্লক করে (শুধু localhost বাদে), আর ফোনে localhost মানে ফোনটিই। Piper-এর বাংলা ভয়েস মডেল আছে কিনা আগে যাচাই করুন।"
        ),
        "watson" to TtsXEng(
            short = "IBM Watson", name = "IBM Watson TTS",
            desc = "IBM Cloud-এর ফ্রি (লাইট) প্ল্যানে সীমিত অক্ষর; আরবি ও ইংরেজি আছে।",
            tags = listOf("ফ্রি টিয়ার (সীমিত)" to "warn", "কী লাগে" to "warn", "বাংলা নেই" to "err"),
            langs = setOf("ar", "en"),
            fields = listOf(
                TtsXField("url", "text", "Service URL", ph = "https://api.us-south.text-to-speech.watson.cloud.ibm.com/instances/…"),
                TtsXField("key", "pw", "API Key"),
                TtsXField("en", "sel", "ভয়েস — ইংরেজি", vop("en-US_MichaelV3Voice" to "Michael (US)", "en-US_AllisonV3Voice" to "Allison (US)", "en-GB_KateV3Voice" to "Kate (UK)")),
                TtsXField("ar", "sel", "ভয়েস — আরবি", vop("ar-MS_OmarVoice" to "Omar"))
            ),
            hint = "IBM Cloud-এ Text to Speech সার্ভিস বানালে তার Credentials থেকে URL ও API Key পাবেন। বাংলা অংশ নিজে থেকে ফোনের TTS/Google-এ পড়া হবে।"
        ),
        "fish" to TtsXEng(
            short = "Fish Audio", name = "Fish Audio",
            desc = "উচ্চমানের AI ভয়েস API। ফ্রি অ্যাক্সেস সীমিত সময়ের ছিল — এখন ফ্রি আছে কিনা তাদের সাইটে দেখুন।",
            tags = listOf("কী লাগে" to "warn", "ফ্রি অনিশ্চিত" to "warn", "বাংলা নেই বলে জানি" to "err"),
            langs = setOf("ar", "en"),
            fields = listOf(
                TtsXField("key", "pw", "API Key"),
                TtsXField("model", "text", "Model (হেডার)", ph = "s1"),
                TtsXField("ref", "text", "ভয়েস (reference_id) — ঐচ্ছিক")
            ),
            hint = "API Key: fish.audio → API Keys। Model-এ আপনার অ্যাকাউন্টে চালু মডেলের নাম দিন। বাংলা অংশ নিজে থেকে ফোনের TTS/Google-এ পড়া হবে।"
        )
    )

    /** React `XENG[id]` (null = not an extra service). */
    fun xeng(id: String): TtsXEng? = XENG[id]

    /** React `tplUrl(tpl, c, x)` — fills `{text}`, `{lang}`, `{voice}` of a URL template (each value URL-encoded). */
    fun tplUrl(tpl: String?, c: ViewUtils.TtsChunk, x: JSONObject): String =
        Regex("\\{(text|lang|voice)\\}").replace(tpl.orEmpty()) { m ->
            val v = when (m.groupValues[1]) { "text" -> c.t; "lang" -> c.lang; else -> x.optString("voice", "") }
            URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        }

    /** React `edgeGec()` — the Edge `Sec-MS-GEC` token: Windows-style time (rounded to 5 minutes) + the client token, SHA-256, upper-case hex. */
    fun edgeGec(): String {
        var t = System.currentTimeMillis() / 1000 + 11644473600L
        t -= t % 300
        t *= 10000000L
        val d = MessageDigest.getInstance("SHA-256").digest((t.toString() + EDGE_TOKEN).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02X".format(it) }
    }

    /**
     * React `edgeAudio(c, x)` — Edge's online voice over a WebSocket; the answer is the mp3 bytes. Cancelling the caller closes the socket
     * (React `AbortController`). The browser-like headers ([Origin], agent) are set here because a phone app, unlike a browser page, can.
     */
    suspend fun edgeAudio(c: ViewUtils.TtsChunk, x: JSONObject): ByteArray {
        val voice = x.optString(c.lang, "").ifEmpty { mapOf("bn" to "bn-BD-NabanitaNeural", "ar" to "ar-SA-ZariyahNeural", "en" to "en-US-AriaNeural")[c.lang].orEmpty() }
        val gec = edgeGec()
        val rid = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=" + EDGE_TOKEN +
            "&ConnectionId=" + rid + "&Sec-MS-GEC=" + gec + "&Sec-MS-GEC-Version=" + URLEncoder.encode(x.optString("ver", "").ifEmpty { EDGE_VER }, "UTF-8")
        val xml = c.t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val req = Request.Builder().url(url)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
            .build()
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val parts = ByteArrayOutputStream()
                var got = false
                var done = false
                var sock: WebSocket? = null
                fun end(err: Exception?) {
                    if (done) return
                    done = true
                    try { sock?.close(1000, null) } catch (_: Exception) { }
                    if (!cont.isActive) return
                    if (err != null) cont.resumeWithException(err) else cont.resume(parts.toByteArray())
                }
                sock = TtsAudio.http.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val ts = fmt.format(Date()).replace("GMT", "GMT+0000 (Coordinated Universal Time)")
                        webSocket.send("X-Timestamp:" + ts + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n")
                        webSocket.send("X-RequestId:" + rid + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + ts + "Z\r\nPath:ssml\r\n\r\n" +
                            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='" + voice +
                            "'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>" + xml + "</prosody></voice></speak>")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("Path:turn.end")) end(if (got) null else Exception("Edge: অডিও আসেনি"))
                    }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        if (bytes.size < 2) return
                        val hl = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                        if (2 + hl > bytes.size) return
                        val head = bytes.substring(2, 2 + hl).utf8()
                        if (head.contains("Path:audio")) {
                            val body = bytes.substring(2 + hl)
                            if (body.size > 0) { parts.write(body.toByteArray()); got = true }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { end(Exception("Edge সার্ভারে সংযোগ হয়নি")) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { end(if (got) null else Exception("Edge সংযোগ বন্ধ হয়ে গেছে")) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { end(if (got) null else Exception("Edge সংযোগ বন্ধ হয়ে গেছে")) }
                })
                cont.invokeOnCancellation { done = true; try { sock?.cancel() } catch (_: Exception) { } }   // React `sig` abort
            }
        }
    }

    /**
     * React `xSrc(cfg, eng, c, sig)` — the audio of piece [c] from service [eng], as bytes (React returned a blob / direct link for the player).
     * Settings come from `cfg.ttsx[eng]`. Throws a short message on failure; cancelling stops the work.
     */
    suspend fun xSrc(cfg: JSONObject, eng: String, c: ViewUtils.TtsChunk): ByteArray {
        val x = cfg.optJSONObject("ttsx")?.optJSONObject(eng) ?: JSONObject()
        return when (eng) {
            "poll" -> withContext(Dispatchers.IO) { TtsAudio.fetch("Pollinations", tplUrl(x.optString("tpl", ""), c, x)) }
            "server" -> withContext(Dispatchers.IO) { TtsAudio.fetch("নিজস্ব সার্ভার", tplUrl(x.optString("url", ""), c, x)) }
            "edge" -> edgeAudio(c, x)
            "watson" -> {
                val base = x.optString("url", "").trim().replace(Regex("/v1/.*$"), "").replace(Regex("/+$"), "")
                val auth = "Basic " + Base64.encodeToString(("apikey:" + x.optString("key", "")).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val body = JSONObject().put("text", c.t).toString().toRequestBody("application/json".toMediaType())
                withContext(Dispatchers.IO) {
                    TtsAudio.fetch("IBM Watson", base + "/v1/synthesize?voice=" + URLEncoder.encode(x.optString(c.lang, ""), "UTF-8"),
                        Request.Builder().post(body).header("Authorization", auth).header("Accept", "audio/mp3"))
                }
            }
            "fish" -> {
                val o = JSONObject().put("text", c.t).put("format", "mp3")
                if (x.optString("ref", "").isNotEmpty()) o.put("reference_id", x.optString("ref"))
                val body = o.toString().toRequestBody("application/json".toMediaType())
                withContext(Dispatchers.IO) {
                    TtsAudio.fetch("Fish Audio", "https://api.fish.audio/v1/tts",
                        Request.Builder().post(body).header("Authorization", "Bearer " + x.optString("key", "")).header("model", x.optString("model", "").ifEmpty { "s1" }))
                }
            }
            else -> throw Exception("অজানা সার্ভিস")
        }
    }
}

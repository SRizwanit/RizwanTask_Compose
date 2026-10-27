package com.srizwan.task.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An image ready to send to Gemini: mime type + base64 data (React `imageB64` result `{ mime, data }`). */
class ViewImgB64(val mime: String, val data: String) {
    /** React `{ inline_data: { mime_type, data } }` */
    fun toPart(): JSONObject =
        JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", data))
}

/** Result of [ViewGemini.callGemini]: the text (code fence removed) and Gemini's `finishReason` (null if none was sent). */
class GeminiResult(val text: String, val finish: String?)

/**
 * React `view/gemini.js` — Gemini streaming (SSE) call. The answer arrives in small pieces, so the connection does not
 * hang even for a big page. Cancelling the calling coroutine = React's `AbortController.abort()` (the HTTP call is cancelled).
 */
object ViewGemini {

    // No overall call timeout: the answer streams for a long time. [readTimeout] only limits the silence between two pieces
    // (Gemini can think for a while before the first piece comes).
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private suspend fun Call.awaitResp(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }

    /** React `geminiHint(status, msg)` */
    fun geminiHint(status: Int, msg: String?): String {
        val low = (msg ?: "").lowercase()
        return when {
            status == 400 && (low.contains("api key") || low.contains("api_key")) -> " (API Key ভুল)"
            status == 401 || status == 403 -> " (Key-তে অনুমতি নেই / ব্লক / মেয়াদ শেষ)"
            status == 404 -> " (মডেলের নাম ভুল হতে পারে)"
            status == 429 -> " (কোটা/রেট-লিমিট — একটু পরে আবার চেষ্টা করুন)"
            else -> ""
        }
    }

    /** `{ text: ... }` part */
    fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    private val FENCE = Regex("^```[A-Za-z]*\\r?\\n([\\s\\S]*?)\\r?\\n```$")

    private fun jsTrim(s: String): String = s.trim { it.isWhitespace() || it == '\uFEFF' || it == '\u00a0' }

    /**
     * React `callGemini({ key, model, parts, temp, signal, onProgress })`.
     * [parts] = JSON array of parts (`textPart`, `ViewImgB64.toPart()`); [onProgress] gets the characters received so far
     * (called on the main thread). Retries: network problem 3 times (waits 3 s, 6 s); 500/502/503/504 3 times (4 s, 8 s);
     * an empty answer 3 times (3 s, 6 s). Throws `Exception` with the Bengali message of React.
     */
    suspend fun callGemini(
        key: String, model: String, parts: JSONArray, temp: Double,
        onProgress: ((Int) -> Unit)? = null
    ): GeminiResult {
        val mdl = model.replace(Regex("^models/"), "")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" + enc(mdl) +
            ":streamGenerateContent?alt=sse&key=" + enc(key)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", temp))
            .toString()
        var lastErr: Exception? = null

        for (att in 1..3) {
            val call = client.newCall(
                Request.Builder().url(url).header("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
            )
            val r: Response = try {
                call.awaitResp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val ne = Exception("নেটওয়ার্ক সমস্যা: " + (e.message ?: ""))
                lastErr = ne
                if (att < 3) { ViewUtils.sleep(3000L * att); continue }
                throw ne
            }

            r.use { resp ->
                if (!resp.isSuccessful) {
                    val t = withContext(Dispatchers.IO) { try { resp.body?.string().orEmpty() } catch (_: Exception) { "" } }
                    val m = try { JSONObject(t).getJSONObject("error").optString("message", "") } catch (_: Exception) { t.take(200) }
                    lastErr = Exception(resp.code.toString() + ": " + m.take(300) + geminiHint(resp.code, m))
                    if (resp.code in intArrayOf(500, 502, 503, 504) && att < 3) { ViewUtils.sleep(4000L * att); return@use null }
                    throw lastErr!!
                }

                val chunks = StringBuilder()
                var finish: String? = null
                var chars = 0

                suspend fun handle(line: String) {
                    if (!line.startsWith("data:")) return
                    val d = line.substring(5).trim()
                    if (d.isEmpty() || d == "[DONE]") return
                    val j = try { JSONObject(d) } catch (_: Exception) { return }
                    j.optJSONObject("error")?.let { throw Exception(it.optString("message", "").ifEmpty { "API ত্রুটি" }.take(300)) }
                    j.optJSONObject("promptFeedback")?.optString("blockReason", "")?.takeIf { it.isNotEmpty() }
                        ?.let { throw Exception("Gemini ব্লক করেছে: $it") }
                    val cands = j.optJSONArray("candidates") ?: JSONArray()
                    for (i in 0 until cands.length()) {
                        val c = cands.optJSONObject(i) ?: continue
                        val ps = c.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                        for (k in 0 until ps.length()) {
                            val p = ps.optJSONObject(k) ?: continue
                            val tx = p.opt("text")
                            if (tx is String && !p.optBoolean("thought", false)) { chunks.append(tx); chars += tx.length }
                        }
                        val fr = c.optString("finishReason", "")
                        if (fr.isNotEmpty() && !c.isNull("finishReason")) finish = fr
                    }
                    if (onProgress != null) { val n = chars; withContext(Dispatchers.Main.immediate) { onProgress(n) } }
                }

                // Read line by line. A cancelled coroutine cancels the HTTP call, which makes the blocking read fail.
                val job = currentCoroutineContext()[Job]
                val reg = job?.invokeOnCompletion { if (it != null) call.cancel() }
                try {
                    withContext(Dispatchers.IO) {
                        val src = resp.body?.source() ?: throw Exception("খালি রেসপন্স (finishReason=null)")
                        while (true) {
                            ensureActive()
                            val line = src.readUtf8Line() ?: break
                            handle(line.trimEnd('\r'))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    throw Exception("নেটওয়ার্ক সমস্যা: " + (e.message ?: ""))
                } finally {
                    reg?.dispose()
                }

                var text = jsTrim(chunks.toString())
                FENCE.find(text)?.let { text = jsTrim(it.groupValues[1]) }   // whole output wrongly wrapped in a code fence — unwrap
                if (text.isEmpty()) {
                    lastErr = Exception("খালি রেসপন্স (finishReason=$finish)")
                    if (att < 3) { ViewUtils.sleep(3000L * att); return@use null }
                    throw lastErr!!
                }
                return GeminiResult(text, finish)
            }
        }
        throw lastErr ?: Exception("অজানা ত্রুটি")
    }

    /**
     * React `imageB64(im)` — the page image as base64 for Gemini. Uses the image cache; if that fails, downloads the raw file
     * straight from the repo. [name] = image file name (`images[i].name`).
     */
    suspend fun imageB64(gh: ViewGithub, imgCache: ViewGithub.ImageCache, name: String): ViewImgB64 {
        val bytes: ByteArray = try {
            imgCache.get(name).bytes
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()      // our own cancel must stay a cancel
            gh.api(gh.rawPath(name), headers = mapOf("Accept" to "application/vnd.github.raw+json")).use { r ->
                if (!r.isSuccessful) throw Exception("ছবি আনা যায়নি: " + gh.errMsg(r))
                withContext(Dispatchers.IO) { r.body?.bytes() ?: ByteArray(0) }
            }
        }
        return ViewImgB64(ViewUtils.MIME_BY_EXT[ViewUtils.extOf(name)] ?: "image/jpeg", ViewUtils.bytesToB64(bytes))
    }
}

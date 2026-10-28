package com.srizwan.task.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error from Supabase / the network. `message` is shown to the user exactly like the React app does. */
class SbException(message: String) : Exception(message)

/** common.js `sb` — Supabase REST (PostgREST) client. */
object Sb {
    private val JSON_TYPE = "application/json".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val NET_MSG = "ইন্টারনেট সংযোগ নেই বা খুব দুর্বল — আবার চেষ্টা করুন"

    private fun builder(path: String): Request.Builder = Request.Builder()
        .url(Cfg.SB_URL + "/rest/v1/" + path)
        .header("apikey", Cfg.SB_KEY)
        .header("Authorization", "Bearer " + Cfg.SB_KEY)
        .header("Cache-Control", "no-store")

    suspend fun req(method: String, path: String, body: Any? = null, prefer: String? = null): JSONArray =
        withContext(Dispatchers.IO) {
            if (!Cfg.configured) {
                throw SbException("SUPABASE_URL / SUPABASE_KEY সেট করা নেই (local.properties বা GitHub Secrets দেখুন)")
            }
            val tk = Net.start()
            try {
                val b = builder(path)
                if (prefer != null) b.header("Prefer", prefer)
                val rb = when {
                    body != null -> body.toString().toRequestBody(JSON_TYPE)
                    method == "POST" || method == "PATCH" || method == "PUT" -> "".toRequestBody(null)
                    else -> null
                }
                b.method(method, rb)
                b.build().let { client.newCall(it).await() }.use { r ->
                    // সার্ভার সাড়া দিয়েছে (HTTP কোড যা-ই হোক) — সংযোগ ঠিক আছে, "নেট সমস্যা" ফ্ল্যাগ নামাও
                    Net.clearProblem()
                    val t = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw SbException("$method ${path.substringBefore('?')} failed: ${r.code} $t")
                    parse(t)
                }
            } catch (e: IOException) {
                // কাজ নিজেই বাতিল হলে (স্ক্রিন বদল ইত্যাদি) সেটা নেট সমস্যা নয়
                if (isActive) Net.fail()
                throw SbException(NET_MSG)
            } finally {
                Net.end()
            }
        }

    /** Fire-and-forget POST used by the activity log: no net bar, never throws (fetch keepalive + .catch(()=>{})). */
    suspend fun postQuiet(path: String, body: Any) {
        withContext(Dispatchers.IO) {
            try {
                if (!Cfg.configured) return@withContext
                val r = builder(path)
                    .header("Prefer", "return=minimal")
                    .post(body.toString().toRequestBody(JSON_TYPE))
                    .build()
                client.newCall(r).await().close()
            } catch (e: Exception) {
                // ignored on purpose
            }
        }
    }

    private fun parse(t: String): JSONArray {
        val s = t.trim()
        if (s.isEmpty()) return JSONArray()
        return when (val v = JSONTokener(s).nextValue()) {
            is JSONArray -> v
            is JSONObject -> JSONArray().put(v)
            is Number, is String, is Boolean -> JSONArray().put(v)   // scalar RPC result (e.g. my_proof_count -> 5), like fetch().json()
            else -> JSONArray()
        }
    }

    suspend fun get(p: String): JSONArray = req("GET", p)
    suspend fun post(p: String, b: Any): JSONArray = req("POST", p, b, "return=representation")
    suspend fun patch(p: String, b: Any): JSONArray = req("PATCH", p, b, "return=representation")
    suspend fun patchMin(p: String, b: Any): JSONArray = req("PATCH", p, b, "return=minimal")
    suspend fun upsert(p: String, b: Any): JSONArray = req("POST", p, b, "resolution=merge-duplicates,return=representation")
    suspend fun del(p: String): JSONArray = req("DELETE", p)

    /** common.js getAll(): every row, 1000 at a time. */
    suspend fun getAll(path: String, max: Int = 200000): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val page = 1000
        val sep = if (path.contains('?')) "&" else "?"
        var off = 0
        while (off < max) {
            val r = get("$path${sep}limit=$page&offset=$off")
            out.addAll(r.objects())
            if (r.length() < page) break
            off += page
        }
        return out
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}

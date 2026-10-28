package com.srizwan.task.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/** An image downloaded from the repo: raw bytes + mime type (React kept an `objectURL` of a Blob). */
class ViewImage(val bytes: ByteArray, val mime: String) {
    /** Decoded bitmap for Compose, or null if the bytes are not a readable image. Decode off the main thread. */
    fun decode(): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    fun decodeAndroid(): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/** Result of [ViewGithub.apiText]: status code + body text (fetch Response replacement for JSON calls). */
class ViewGhResp(val code: Int, val text: String) {
    val ok: Boolean get() = code in 200..299
    fun json(): JSONObject = JSONObject(text)
}

/**
 * React `view/github.js` `createGithub(getCfg)` — GitHub REST API: reading / writing the repo's images and texts.
 * [getCfg] always returns the latest config (use `ViewCfgStore.cfg`). Repo / branch / folders come from [ViewConfig.repoPaths].
 */
class ViewGithub(private val getCfg: () -> JSONObject) {

    companion object {
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

        private suspend fun Call.awaitResp(): Response = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { cont.resume(response) }
            })
        }

        const val NO_NET = "ইন্টারনেট সংযোগ নেই বা খুব দুর্বল — আবার চেষ্টা করুন"
    }

    /** `?ref=<branch>` or "" */
    fun refQ(): String = getCfg().str("branch").orEmpty().let { if (it.isNotEmpty()) "?ref=" + enc(it) else "" }

    /**
     * `api(path, opts)` — `https://api.github.com/repos/<repo><path>`. The caller must close the response (`use`).
     * [noStore] = true is React's `cache: 'no-store'` (stops an old cached text / sha coming back after a save); the image
     * download passes false.
     */
    suspend fun api(
        path: String, method: String = "GET", headers: Map<String, String> = emptyMap(),
        body: String? = null, noStore: Boolean = true
    ): Response {
        val cfg = getCfg()
        val rb = Request.Builder().url("https://api.github.com/repos/" + cfg.str("repo").orEmpty() + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val token = cfg.str("token").orEmpty()
        if (token.isNotEmpty()) rb.header("Authorization", "Bearer $token")
        if (noStore) rb.header("Cache-Control", "no-store")
        for ((k, v) in headers) rb.header(k, v)
        val rq = if (body != null) body.toRequestBody("application/json".toMediaType()) else if (method == "GET" || method == "HEAD") null else "".toRequestBody()
        rb.method(method, rq)
        try {
            return client.newCall(rb.build()).awaitResp()
        } catch (e: CancellationException) { throw e } catch (e: IOException) { throw Exception(NO_NET) }
    }

    /** `api()` + read the whole body as text (for JSON calls). */
    suspend fun apiText(path: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null): ViewGhResp =
        api(path, method, headers, body).use { r -> withContext(Dispatchers.IO) { ViewGhResp(r.code, r.body?.string().orEmpty()) } }

    /** `errMsg(r)` — "<status>: <message> (<hint>)". */
    fun errMsg(code: Int, bodyText: String): String {
        val m = try { JSONObject(bodyText).optString("message", "") } catch (_: Exception) { "" }
        val hint = when (code) {
            401 -> " (টোকেন ভুল বা মেয়াদ শেষ)"
            403 -> " (অনুমতি নেই বা রেট-লিমিট; টোকেন দিন)"
            404 -> " (রিপো/ফোল্ডার/ফাইল পাওয়া যায়নি — নাম বা টোকেনের অ্যাক্সেস দেখুন)"
            else -> ""
        }
        return "$code: $m$hint"
    }
    fun errMsg(r: ViewGhResp): String = errMsg(r.code, r.text)
    suspend fun errMsg(r: Response): String = withContext(Dispatchers.IO) { errMsg(r.code, try { r.body?.string().orEmpty() } catch (_: Exception) { "" }) }

    /** `/contents/<pagesDir>/<name>[?ref=branch]` */
    fun rawPath(name: String): String =
        "/contents/" + ViewUtils.epath(ViewConfig.repoPaths(getCfg()).pagesDir + "/" + name) + refQ()

    /** Raw link of an image (for the AI prompt). */
    fun rawImageUrl(name: String, defaultBranch: String? = null): String {
        val cfg = getCfg()
        val br = (cfg.str("branch").orEmpty().ifEmpty { defaultBranch.orEmpty() }.ifEmpty { "main" }).split("/").joinToString("/") { enc(it) }
        return "https://raw.githubusercontent.com/" + cfg.str("repo").orEmpty() + "/refs/heads/" + br + "/" +
            ViewUtils.epath(ViewConfig.repoPaths(cfg).pagesDir + "/" + name)
    }

    /**
     * Image cache + download percentage. Three layers, fastest first:
     *  1. decoded bitmaps ([bmpCache], limited by size in bytes) — a page opened again shows at once, no decoding;
     *  2. raw bytes in memory (at most 6 images; the same download is shared by every caller);
     *  3. raw bytes on the phone's disk ([ViewImageDisk], per repo / branch / folder) — survives app restarts.
     * [onProgress] (name, pct): pct = 0..100, or null when the total size is unknown. Called on the main thread.
     * [scope] runs the downloads (they continue even if the caller stops waiting, like a started Promise).
     */
    fun createImageCache(scope: CoroutineScope = AppScope.scope, onProgress: (String, Float?) -> Unit): ImageCache =
        ImageCache(scope, onProgress)

    /** Folder key of the disk cache: images of another repo / branch / folder never mix. */
    private fun diskScope(): String {
        val cfg = getCfg()
        return cfg.str("repo").orEmpty() + "|" + cfg.str("branch").orEmpty() + "|" + ViewConfig.repoPaths(cfg).pagesDir
    }

    inner class ImageCache(private val scope: CoroutineScope, private val onProgress: (String, Float?) -> Unit) {
        private val cache = LinkedHashMap<String, Deferred<ViewImage>>()   // name -> download
        private val pct = HashMap<String, Float?>()                        // name -> percentage (key present = started)
        private val decoding = HashMap<String, Deferred<ImageBitmap?>>()   // name -> decode in progress (shared)

        /** Decoded pages, sized in KB (4 bytes per pixel): about 1/6 of the app's memory limit, between 24 and 128 MB. */
        private val bmpCache = object : LruCache<String, ImageBitmap>(
            (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceIn(24 * 1024, 128 * 1024)
        ) {
            override fun sizeOf(key: String, value: ImageBitmap): Int = ((value.width.toLong() * value.height * 4) / 1024).toInt().coerceAtLeast(1)
        }

        private val lock = Any()   // downloads finish on IO threads, callers are on the main thread

        private fun prog(name: String, p: Float?) {
            synchronized(lock) { pct[name] = p }
            scope.launch(Dispatchers.Main.immediate) { onProgress(name, p) }
        }

        /** Image bytes (memory, then disk, then GitHub); the same download is shared by every caller. Throws with the GitHub error text on failure. */
        suspend fun get(name: String): ViewImage {
            val d = synchronized(lock) {
                cache[name]?.let { return@synchronized it }
                val sc = diskScope()
                val nd = scope.async(Dispatchers.IO) { load(sc, name) }
                cache[name] = nd
                nd.invokeOnCompletion { e ->
                    if (e != null) synchronized(lock) { if (cache[name] === nd) cache.remove(name); pct.remove(name) }
                }
                if (cache.size > 6) cache.keys.first().let { cache.remove(it) }
                nd
            }
            return d.await()
        }

        /** Disk first (no network at all), else download and save to disk. */
        private suspend fun load(sc: String, name: String): ViewImage {
            ViewImageDisk.read(sc, name)?.let { b ->
                prog(name, 100f)
                return ViewImage(b, ViewUtils.MIME_BY_EXT[ViewUtils.extOf(name)] ?: "image/jpeg")
            }
            val vi = download(name)
            ViewImageDisk.write(sc, name, vi.bytes)
            return vi
        }

        /** The decoded page if it is already in memory (null = not yet). Instant, safe on the main thread. */
        fun peekBitmap(name: String): ImageBitmap? = bmpCache.get(name)

        /**
         * The decoded page: from the bitmap cache, else [get] + decode (off the main thread). One decode is shared by every
         * caller. null = the bytes are not a readable image (the bad copy is dropped from memory and disk, so the next try downloads again).
         */
        suspend fun getBitmap(name: String): ImageBitmap? {
            bmpCache.get(name)?.let { return it }
            val d = synchronized(lock) {
                decoding[name] ?: scope.async(Dispatchers.Default) { decodeNow(name) }.also { nd ->
                    decoding[name] = nd
                    nd.invokeOnCompletion { synchronized(lock) { if (decoding[name] === nd) decoding.remove(name) } }
                }
            }
            return d.await()
        }

        private suspend fun decodeNow(name: String): ImageBitmap? {
            bmpCache.get(name)?.let { return it }
            val vi = get(name)
            val bmp = vi.decode()
            if (bmp == null) {
                ViewImageDisk.remove(diskScope(), name)
                synchronized(lock) { cache.remove(name); pct.remove(name) }
            } else bmpCache.put(name, bmp)
            return bmp
        }

        /** Loads [name] in the background so it is ready when the reader gets there. [decode] = also decode it into the bitmap cache. */
        fun prefetch(name: String, decode: Boolean) {
            scope.launch {
                try { if (decode) getBitmap(name) else get(name) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }

        private suspend fun download(name: String): ViewImage {
            api(
                "/contents/" + ViewUtils.epath(ViewConfig.repoPaths(getCfg()).pagesDir + "/" + name) + refQ(),
                headers = mapOf("Accept" to "application/vnd.github.raw+json"), noStore = false
            ).use { r ->
                if (!r.isSuccessful) throw Exception(errMsg(r))
                val mime = ViewUtils.MIME_BY_EXT[ViewUtils.extOf(name)] ?: "image/jpeg"
                val total = (r.header("Content-Length")?.toLongOrNull() ?: r.body?.contentLength() ?: 0L).coerceAtLeast(0L)
                val body = r.body ?: throw Exception(errMsg(r.code, ""))
                prog(name, if (total > 0) 0f else null)
                val out = ByteArrayOutputStream(if (total in 1..64_000_000) total.toInt() else 32 * 1024)
                val src = body.source(); val buf = ByteArray(16 * 1024); var got = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); got += n
                    if (total > 0) prog(name, min(99f, got.toFloat() / total * 100f))
                }
                prog(name, 100f)
                return ViewImage(out.toByteArray(), mime)
            }
        }

        /** Memory only (decoded pages, bytes, percentages); the disk copies stay. React `imgCache.clear()`. */
        fun clear() { synchronized(lock) { cache.clear(); pct.clear(); decoding.clear() }; bmpCache.evictAll() }

        /** Memory + the disk copies of the current repo / branch / folder: the reload button, so every page is downloaded fresh. */
        fun clearAll() { clear(); ViewImageDisk.clear(diskScope()) }
        fun has(name: String): Boolean = synchronized(lock) { pct.containsKey(name) }
        /** Last percentage of [name] (null = unknown / not started). */
        fun pct(name: String): Float? = synchronized(lock) { pct[name] }
    }
}

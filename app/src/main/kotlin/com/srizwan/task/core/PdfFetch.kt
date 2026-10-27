package com.srizwan.task.core

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A PDF could not be downloaded; [message] is a ready-to-show Bengali text. */
class PdfFetchException(message: String) : Exception(message)

/**
 * Admin -> Books -> PDF viewer, step 1: downloads a book's PDF into the app cache (`cacheDir/pdfs`) BEFORE it is shown.
 *  - progress (bytes done / total, total = -1 when the server does not tell it)
 *  - cancels with the coroutine (the running OkHttp call is cancelled too)
 *  - an already downloaded + valid file is reused (no second download) unless `force = true`
 *  - a half-finished `.part` file is never shown; the HTML error pages are rejected (header must contain %PDF)
 *  - Google Drive share links and GitHub `blob` links are turned into direct download links
 *  - the cache is trimmed to [MAX_CACHE_BYTES] (oldest first)
 *
 * Usage (viewer, step 3):
 *   val file = PdfFetch.fetch(ctx, book.str("pdf_url"), force = false) { done, total -> pct = PdfFetch.percent(done, total) }
 */
object PdfFetch {

    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
    private const val MIN_BYTES = 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** `pdf_url` may hold several links (comma / space separated): the viewer opens the first one. */
    fun firstLink(raw: String?): String =
        (raw ?: "").split(Regex("[,\\s]+")).firstOrNull { it.isNotEmpty() } ?: ""

    /** Share links that open a web page are converted to the direct file link. */
    fun directUrl(link: String): String {
        val u = link.trim()
        Regex("drive\\.google\\.com/file/d/([A-Za-z0-9_-]+)").find(u)?.let {
            return "https://drive.google.com/uc?export=download&id=" + it.groupValues[1]
        }
        Regex("drive\\.google\\.com/open\\?id=([A-Za-z0-9_-]+)").find(u)?.let {
            return "https://drive.google.com/uc?export=download&id=" + it.groupValues[1]
        }
        Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(u)?.let { m ->
            return "https://raw.githubusercontent.com/" + m.groupValues[1] + "/" + m.groupValues[2] + "/" + m.groupValues[3]
        }
        return u
    }

    /** 0..100, or null when the total size is unknown. */
    fun percent(done: Long, total: Long): Int? =
        if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "pdfs").apply { mkdirs() }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Where this link's PDF is (or will be) stored. */
    fun cacheFile(ctx: Context, link: String?): File =
        File(dir(ctx), sha1(directUrl(firstLink(link))) + ".pdf")

    /** The downloaded copy if it is there and really a PDF, else null. */
    fun cached(ctx: Context, link: String?): File? =
        cacheFile(ctx, link).takeIf { it.isFile && looksLikePdf(it) }

    /** Removes the downloaded copy (for "download again"). */
    fun clear(ctx: Context, link: String?) {
        val f = cacheFile(ctx, link)
        f.delete()
        File(f.path + ".part").delete()
    }

    private fun looksLikePdf(f: File): Boolean {
        if (!f.isFile || f.length() < MIN_BYTES) return false
        return try {
            f.inputStream().use { ins ->
                val b = ByteArray(1024)
                val n = ins.read(b)
                n > 0 && String(b, 0, n, Charsets.ISO_8859_1).contains("%PDF")
            }
        } catch (_: Exception) { false }
    }

    /** Keeps the cache folder under [MAX_CACHE_BYTES]: the least recently used files go first, [keep] is never removed. */
    private fun trim(ctx: Context, keep: File) {
        try {
            val files = dir(ctx).listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                if (f.absolutePath == keep.absolutePath) continue
                total -= f.length()
                f.delete()
            }
        } catch (_: Exception) { }
    }

    /**
     * Downloads (or reuses) the PDF and returns the file. Throws [PdfFetchException] with a Bengali message on failure;
     * cancellation is passed through as the normal [CancellationException].
     */
    suspend fun fetch(
        ctx: Context,
        link: String?,
        force: Boolean = false,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): File {
        val url = directUrl(firstLink(link))
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw PdfFetchException("এই কিতাবের PDF লিংকটি সঠিক নয়")
        }
        val target = cacheFile(ctx, link)

        if (!force) {
            cached(ctx, link)?.let {
                it.setLastModified(System.currentTimeMillis())   // recently used
                onProgress(it.length(), it.length())
                return it
            }
        }

        return withContext(Dispatchers.IO) {
            val part = File(target.path + ".part")
            part.delete()
            val call = http.newCall(
                Request.Builder().url(url).header("Accept", "application/pdf,*/*").build()
            )
            // when the coroutine is cancelled (viewer closed) the network call stops at once
            val reg = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r ->
                    if (!r.isSuccessful) throw PdfFetchException("ডাউনলোড হয়নি (কোড ${r.code})")
                    val body = r.body ?: throw PdfFetchException("সার্ভার থেকে ফাইল আসেনি")
                    val total = body.contentLength()
                    var done = 0L
                    var lastPct = -1
                    var lastReport = 0L
                    onProgress(0L, total)
                    body.byteStream().use { ins ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; onProgress(done, total) }
                                } else if (done - lastReport >= 256 * 1024) {
                                    lastReport = done
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                }
                if (!looksLikePdf(part)) throw PdfFetchException("এটি PDF ফাইল নয় (লিংকটি ঠিক আছে কিনা দেখুন)")
                target.delete()
                if (!part.renameTo(target)) throw PdfFetchException("ফাইল সংরক্ষণ করা যায়নি")
                trim(ctx, target)
                onProgress(target.length(), target.length())
                target
            } catch (e: CancellationException) {
                part.delete(); throw e
            } catch (e: PdfFetchException) {
                part.delete(); throw e
            } catch (e: Exception) {
                part.delete()
                if (!isActive) throw CancellationException("cancelled")     // the cancelled OkHttp call throws IOException("Canceled")
                throw PdfFetchException(
                    if (!Net.isOnline) "ইন্টারনেট সংযোগ নেই"
                    else if (e is IOException) "ডাউনলোড ব্যর্থ হয়েছে, আবার চেষ্টা করুন"
                    else "ডাউনলোডে সমস্যা: " + (e.message ?: "অজানা ত্রুটি")
                )
            } finally {
                reg?.dispose()
            }
        }
    }
}

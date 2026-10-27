package com.srizwan.task.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale

/**
 * React `View.jsx` `downloadCurrent` — the ORIGINAL image file (not the cache) is fetched straight from the repo and saved to the
 * phone's Downloads folder (React: `<a download>` + Blob). Android 10+ uses MediaStore (no permission); older versions write to the
 * public Downloads folder (`WRITE_EXTERNAL_STORAGE`, already in the manifest up to API 28).
 */
object ViewDownload {

    private val http = OkHttpClient()

    /** The image is saved here so the user can find it; the same name as in the repo (React `a.download = im.name`). */
    private suspend fun fetchOriginal(gh: ViewGithub, name: String): ByteArray {
        val path = gh.rawPath(name)
        var buf: ByteArray = gh.api(path, headers = mapOf("Accept" to "application/vnd.github.raw+json")).use { r ->
            if (!r.isSuccessful) throw Exception(gh.errMsg(r))
            withContext(Dispatchers.IO) { r.body?.bytes() ?: ByteArray(0) }
        }
        if (buf.size < 2048) {   // too small (maybe an error response): try again through download_url
            val rj = gh.apiText(path)
            if (rj.ok) {
                val url = try { rj.json().optString("download_url", "") } catch (_: Exception) { "" }
                if (url.isNotEmpty()) {
                    val b2 = withContext(Dispatchers.IO) {
                        try {
                            http.newCall(Request.Builder().url(url).header("Cache-Control", "no-store").build()).execute().use { r2 ->
                                if (r2.isSuccessful) r2.body?.bytes() else null
                            }
                        } catch (_: Exception) { null }
                    }
                    if (b2 != null) buf = b2
                }
            }
        }
        if (buf.size < 2048) throw Exception("ফাইল ঠিকমতো আসেনি (" + buf.size + " বাইট)")
        return buf
    }

    private fun save(ctx: Context, name: String, mime: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val res = ctx.contentResolver
            val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw Exception("ফাইল তৈরি করা যায়নি")
            try {
                (res.openOutputStream(uri) ?: throw Exception("ফাইল লেখা যায়নি")).use { it.write(bytes) }
                res.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { try { res.delete(uri, null, null) } catch (_: Exception) { }; throw e }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).outputStream().use { it.write(bytes) }
        }
    }

    /** How many words of the book name go into the downloaded file name (\"আমীরে আহলে সুন্নাত …\" -> \"আমীরে আহলে\"). */
    const val SHORT_NAME_WORDS = 2

    /** Short book name for file names: the first [SHORT_NAME_WORDS] words, characters Android forbids in file names removed. */
    fun shortBookName(book: String): String =
        book.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(SHORT_NAME_WORDS).joinToString(" ").trim('.', ' ')

    /** Saved file name: \"<short book name> <page file name>\", e.g. \"আমীরে আহলে 0001.jpg\" (just the page name when the book has no name). */
    fun fileName(bookName: String, imageName: String): String {
        val base = imageName.substringAfterLast('/')
        val short = shortBookName(bookName)
        return if (short.isEmpty()) base else "$short $base"
    }

    /**
     * React `downloadCurrent`. [name] = the open image's file name, or null/empty when no image is open
     * (\"কোনো ছবি খোলা নেই\"). All messages go through [toaster] exactly like React's `toast(...)`.
     */
    suspend fun downloadCurrent(ctx: Context, gh: ViewGithub, toaster: ViewToaster, name: String?, bookName: String = "") {
        if (name.isNullOrEmpty()) { toaster.toast("কোনো ছবি খোলা নেই", true); return }
        val mime = ViewUtils.MIME_BY_EXT[ViewUtils.extOf(name)] ?: "application/octet-stream"
        val saveName = fileName(bookName, name)
        try {
            toaster.toast("মূল ছবি আনা হচ্ছে…", false, 4000)
            val buf = fetchOriginal(gh, name)
            withContext(Dispatchers.IO) { save(ctx, saveName, mime, buf) }
            toaster.toast("ডাউনলোড: " + saveName + " (" + String.format(Locale.US, "%.2f", buf.size / 1048576.0) + " MB) — ফোনের Downloads ফোল্ডারে সেভ হয়েছে", false, 3500)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { toaster.toast("ডাউনলোড ব্যর্থ: " + (e.message ?: ""), true) }
    }
}

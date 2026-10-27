package com.srizwan.task.screens.admin

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.srizwan.task.core.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The PDF could not be opened / drawn; [message] is a ready-to-show Bengali text. */
class PdfEngineException(message: String) : Exception(message)

/**
 * Admin -> Books -> PDF viewer, step 2: the drawing engine on top of Android's built-in [PdfRenderer] (no extra library).
 *
 *  - [PdfRenderer] can have only ONE page open at a time, so every open/render goes through one [Mutex]; a request that is
 *    cancelled while waiting (the page scrolled away) simply never runs.
 *  - [pageCount] and the size of every page ([pageWidthPt]/[pageHeightPt], PDF points) are read once in [open], so the
 *    viewer can reserve the exact height of each page before it is drawn (no jumping while scrolling).
 *  - [render] draws a page at any pixel width (zoom = a bigger width) on a white background and keeps the result in an LRU
 *    cache (by bytes), so a page that was just seen is not drawn again.
 *
 * Usage (viewer, step 3):
 *   val engine = PdfEngine.open(file)                       // suspend, throws PdfEngineException
 *   val bmp = engine.render(index = 0, widthPx = 1080)      // suspend, cancellable
 *   DisposableEffect(Unit) { onDispose { engine.closeAsync() } }
 */
class PdfEngine private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageWidthPt: IntArray,
    val pageHeightPt: IntArray
) {
    val pageCount: Int get() = pageWidthPt.size

    private val mutex = Mutex()
    @Volatile private var closed = false

    // Bitmaps are kept by size in bytes (4-gha: smaller than before, big PDFs must not push the app to its limit):
    // at most 1/8 of the app's heap, never more than 64 MB and never less than 16 MB (about two full pages).
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Height / width of a page (e.g. 1.41 for A4) — the viewer uses it to size the page box before drawing. */
    fun aspect(index: Int): Float {
        val w = pageWidthPt[index]
        return if (w > 0) pageHeightPt[index].toFloat() / w else 1.414f
    }

    /** A page that was drawn before at exactly this width (no waiting), else null. */
    fun cachedPage(index: Int, widthPx: Int): Bitmap? = cache.get(key(index, clampWidth(widthPx)))

    /**
     * Draws page [index] (0-based) [widthPx] pixels wide; the height follows the page's own shape. The width is limited to
     * [MIN_WIDTH]..[MAX_SIDE] and the bitmap to [MAX_PIXELS], so a big zoom can never run out of memory — the viewer just
     * scales the result. Cancellable: a cancelled call stops before drawing.
     */
    suspend fun render(index: Int, widthPx: Int): Bitmap {
        if (index !in 0 until pageCount) throw PdfEngineException("এই পেইজটি নেই")
        val w = clampWidth(widthPx)
        val k = key(index, w)
        cache.get(k)?.let { return it }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureActive()
                if (closed) throw PdfEngineException("PDF বন্ধ হয়ে গেছে")
                cache.get(k)?.let { return@withLock it }          // someone else drew it while we waited

                var h = (w * aspect(index)).toInt().coerceAtLeast(1)
                var ww = w
                if (h > MAX_SIDE) { ww = (ww * MAX_SIDE.toFloat() / h).toInt().coerceAtLeast(1); h = MAX_SIDE }
                if (ww.toLong() * h > MAX_PIXELS) {
                    val f = Math.sqrt(MAX_PIXELS.toDouble() / (ww.toLong() * h))
                    ww = (ww * f).toInt().coerceAtLeast(1); h = (h * f).toInt().coerceAtLeast(1)
                }

                val bmp = try {
                    Bitmap.createBitmap(ww, h, Bitmap.Config.ARGB_8888)
                } catch (_: OutOfMemoryError) {
                    cache.evictAll()
                    try { Bitmap.createBitmap(ww, h, Bitmap.Config.ARGB_8888) }
                    catch (_: OutOfMemoryError) { throw PdfEngineException("মেমরি কম, জুম কমিয়ে দেখুন") }
                }
                bmp.eraseColor(Color.WHITE)                         // PdfRenderer draws on transparent; the page must be white
                try {
                    renderer.openPage(index).use { p -> p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                } catch (e: Exception) {
                    bmp.recycle()
                    throw PdfEngineException("পেইজ ${index + 1} আঁকা যায়নি")
                }
                cache.put(k, bmp)
                bmp
            }
        }
    }

    /** Drops every drawn page (low memory, or when the zoom changes a lot). */
    fun clearCache() = cache.evictAll()

    /**
     * 4-gha: keeps only the pages drawn at [widthPx] — after a turn of the screen or a zoom change the old sizes can never be
     * shown again and would only take memory until they slowly leave the LRU list.
     */
    fun retainWidth(widthPx: Int) {
        val suffix = "@" + clampWidth(widthPx)
        for (k in cache.snapshot().keys) if (!k.endsWith(suffix)) cache.remove(k)
    }

    /**
     * 4-gha: reacts to the system's memory warnings (`ComponentCallbacks2.onTrimMemory` level).
     * 10+ (running low): half of the drawn pages go; 15+ (critical) or 20+ (app hidden): all go. The page on the screen stays,
     * because the screen holds its own reference and bitmaps are never recycled while shown.
     */
    fun trim(level: Int) {
        when {
            level >= 15 -> cache.evictAll()
            level >= 10 -> cache.trimToSize(cache.size() / 2)
        }
    }

    /** Closes the file once a running draw has finished. Safe to call twice. */
    suspend fun close() {
        mutex.withLock {
            if (closed) return
            closed = true
            cache.evictAll()                                         // bitmaps are not recycled: the screen may still show one
            try { renderer.close() } catch (_: Exception) { }
            try { pfd.close() } catch (_: Exception) { }
        }
    }

    /** For `onDispose { }` (cannot suspend). */
    fun closeAsync() { AppScope.scope.launch(Dispatchers.IO) { close() } }

    private fun key(index: Int, w: Int) = "$index@$w"
    private fun clampWidth(w: Int) = w.coerceIn(MIN_WIDTH, MAX_SIDE)

    companion object {
        const val MIN_WIDTH = 200
        const val MAX_SIDE = 8192                                    // longest side of a drawn page, px
        const val MAX_PIXELS = 8L * 1000 * 1000                      // ~32 MB for one page at ARGB_8888 (4-gha: was 12 million)

        /** Opens [file] and reads every page's size. Throws [PdfEngineException] (Bengali) for a broken / locked / empty PDF. */
        suspend fun open(file: File): PdfEngine = withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var r: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                r = PdfRenderer(pfd)
                val n = r.pageCount
                if (n <= 0) throw PdfEngineException("এই PDF-এ কোনো পেইজ নেই")
                val ws = IntArray(n)
                val hs = IntArray(n)
                for (i in 0 until n) {
                    ensureActive()
                    r.openPage(i).use { p -> ws[i] = p.width; hs[i] = p.height }
                }
                PdfEngine(pfd, r, ws, hs)
            } catch (e: Exception) {
                try { r?.close() } catch (_: Exception) { }
                try { pfd?.close() } catch (_: Exception) { }
                when (e) {
                    is PdfEngineException, is kotlinx.coroutines.CancellationException -> throw e
                    is SecurityException -> throw PdfEngineException("এই PDF-এ পাসওয়ার্ড দেওয়া আছে, খোলা যাচ্ছে না")
                    is IOException -> throw PdfEngineException("PDF ফাইলটি নষ্ট বা অসম্পূর্ণ, আবার ডাউনলোড করুন")
                    else -> throw PdfEngineException("PDF খোলা যায়নি: " + (e.message ?: "অজানা ত্রুটি"))
                }
            }
        }
    }
}

package com.srizwan.task.screens.view

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One piece of online audio — React `new Audio()` as `gAudio` / the extra services use it (4-ঝ-১; 4-ঝ-২ reuses it with its own [load]).
 * The bytes start loading as soon as [preload] is called (React `preload = 'auto'`, the next piece is fetched ahead); [play] waits for them,
 * then plays through `MediaPlayer`. Everything reports on the main thread. [kill] = React `killAudio(a)`: stops, frees the player, deletes the temp file,
 * and nothing is reported any more.
 */
class TtsAudio(
    private val appCtx: Context,
    private val scope: CoroutineScope,
    private val load: suspend () -> ByteArray
) {
    /** What playback reports (React `onplay/onplaying`, `onended`, `onerror`). [onError] gets the short reason shown in the toast. */
    class Cb(val onStart: () -> Unit, val onEnd: () -> Unit, val onError: (String) -> Unit)

    private class Loaded(val bytes: ByteArray?, val why: String)

    private var data: Deferred<Loaded>? = null
    private var job: Job? = null
    private var mp: MediaPlayer? = null
    private var file: File? = null
    private var dead = false
    private var rate = 1.0

    /** Start fetching the bytes now (once). */
    fun preload() {
        if (data != null || dead) return
        data = scope.async(Dispatchers.IO) {
            try { Loaded(load(), "") }
            catch (e: CancellationException) { throw e }
            catch (e: IOException) { Loaded(null, "নেটওয়ার্ক ত্রুটি") }
            catch (e: Exception) { Loaded(null, (e.message ?: "অজানা ত্রুটি").take(90)) }   // never throws out: a failed load must not cancel the page scope
        }
    }

    /** React `a.playbackRate = r` — takes effect at once while playing. */
    fun setRate(r: Double) {
        rate = r
        try { val p = mp; if (p != null && p.isPlaying) p.playbackParams = PlaybackParams().setSpeed(r.toFloat()) } catch (_: Exception) { }
    }

    /** React `a.play()` — plays when the bytes are ready; errors come through [Cb.onError]. */
    fun play(cb: Cb) {
        if (dead) return
        preload()
        job = scope.launch {
            val r = data?.await() ?: return@launch
            if (dead) return@launch
            val b = r.bytes
            if (b == null) { cb.onError(r.why); return@launch }
            try {
                val f = File.createTempFile("tts", ".mp3", appCtx.cacheDir)
                file = f
                withIo { f.writeBytes(b) }
                if (dead) return@launch
                val p = MediaPlayer()
                mp = p
                p.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                p.setDataSource(f.path)
                p.setOnPreparedListener {
                    if (!dead) {
                        try { it.playbackParams = PlaybackParams().setSpeed(rate.toFloat()) } catch (_: Exception) { }
                        it.start()
                        cb.onStart()
                    }
                }
                p.setOnCompletionListener { if (!dead) cb.onEnd() }
                p.setOnErrorListener { _, _, _ -> if (!dead) cb.onError("সার্ভার অডিও দেয়নি"); true }
                p.prepareAsync()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (!dead) cb.onError("সার্ভার অডিও দেয়নি") }
        }
    }

    private suspend fun <T> withIo(f: () -> T): T = kotlinx.coroutines.withContext(Dispatchers.IO) { f() }

    /** React `killAudio(a)`. */
    fun kill() {
        dead = true
        job?.cancel(); job = null
        data?.cancel(); data = null
        val p = mp; mp = null
        if (p != null) { try { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null); p.reset(); p.release() } catch (_: Exception) { } }
        val f = file; file = null
        if (f != null) { try { f.delete() } catch (_: Exception) { } }
    }

    companion object {
        /** HTTP client for the audio services (a browser-like agent; no referrer, like React `referrerPolicy = 'no-referrer'`). */
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

        /** GET [url] and return the audio bytes; throws with a short message when the server did not send audio (React `fetchAudio` rules). */
        fun fetch(name: String, url: String, request: Request.Builder = Request.Builder()): ByteArray {
            val req = request.url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    val m = try { r.body?.string().orEmpty().take(120) } catch (_: Exception) { "" }
                    throw Exception(name + " " + r.code + (if (m.isNotEmpty()) ": $m" else ""))
                }
                val b = r.body?.bytes() ?: ByteArray(0)
                val t = r.header("Content-Type").orEmpty()
                if (b.isEmpty() || Regex("json|text|html", RegexOption.IGNORE_CASE).containsMatchIn(t)) throw Exception("$name: অডিও আসেনি")
                return b
            }
        }
    }
}

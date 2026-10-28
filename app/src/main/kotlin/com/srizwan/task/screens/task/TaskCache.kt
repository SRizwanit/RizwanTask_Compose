package com.srizwan.task.screens.task

import android.app.Application
import com.srizwan.task.core.ViewImageDisk
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Offline cache of the Task page (ধাপ ১). Only the data the page shows first is kept — nothing more:
 *  - the first [MAX_BOOKS] (2) books of `shownBooks` (the ones shown at the top) + their progress counts
 *  - `Fin` (settings, my proofs, my payments) -> the dashboard / earnings
 *  - `myCount` and the notices
 *  - the cover pictures of those 2 books ([ViewImageDisk], scope [COVER_SCOPE]) so the cards are not blank offline
 *
 * One JSON file in the app's internal storage (`filesDir/task_cache_v1.json`), written to a temporary name first and
 * renamed (a half-written file is never read). Every successful load **replaces** the old snapshot with the new one
 * (old books / covers are removed). The cache belongs to one user id: another user never sees it, and
 * [clear] is called on logout.
 *
 * This file is only storage. Reading it when the load fails = ধাপ ২; the dialog = ধাপ ৩–৫.
 */
object TaskCache {
    const val MAX_BOOKS = 2
    private const val FILE = "task_cache_v1.json"
    private const val COVER_SCOPE = "task_cache_covers"
    private const val VERSION = 1

    private var file: File? = null
    private val lock = Mutex()   // one writer at a time (two saves must not share the temporary file)
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** What [read] returns: everything the Task page needs to draw itself without internet. */
    class Snap(
        val books: List<JSONObject>,
        val bookCounts: Map<String, Int>,
        val myCount: Int?,
        val fin: Fin,
        val notices: List<JSONObject>,
        val savedAt: Long
    )

    fun init(app: Application) { file = File(app.filesDir, FILE) }

    /** The saved snapshot of user [userId], or null (no file / another user's file / unreadable file). */
    fun read(userId: String): Snap? = try {
        val f = file
        if (f == null || !f.isFile || f.length() == 0L) null
        else {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            if (o.optInt("v") != VERSION || o.optString("uid") != userId) null
            else {
                val books = (o.optJSONArray("books") ?: JSONArray()).objects()
                val counts = LinkedHashMap<String, Int>()
                o.optJSONObject("counts")?.let { c -> c.keys().forEach { k -> counts[k] = c.optInt(k, 0) } }
                val fin = Fin(
                    o.optJSONObject("S") ?: JSONObject(),
                    (o.optJSONArray("proofs") ?: JSONArray()).objects(),
                    (o.optJSONArray("pays") ?: JSONArray()).objects(),
                    true
                )
                Snap(
                    books, counts,
                    if (o.has("myCount") && !o.isNull("myCount")) o.optInt("myCount") else null,
                    fin,
                    (o.optJSONArray("notices") ?: JSONArray()).objects(),
                    o.optLong("savedAt")
                )
            }
        }
    } catch (_: Exception) { null }

    /** True when [read] would return a snapshot for this user. */
    fun has(userId: String): Boolean = read(userId) != null

    /** The saved cover picture bytes of book [bookId] (one of the cached 2), or null. */
    fun coverBytes(bookId: String): ByteArray? = ViewImageDisk.read(COVER_SCOPE, bookId)

    /**
     * Replace the snapshot with the data of a **successful** load. [shownBooks] is the full list shown on the page —
     * only its first [MAX_BOOKS] are kept. Does nothing when there is no book or the money data has not loaded
     * (a half-empty snapshot must never replace a good one). Never throws.
     */
    suspend fun save(
        userId: String,
        shownBooks: List<JSONObject>,
        bookCounts: Map<String, Int>,
        myCount: Int?,
        fin: Fin,
        notices: List<JSONObject>
    ) {
        if (userId.isEmpty() || shownBooks.isEmpty() || !fin.ready) return
        lock.withLock { withContext(Dispatchers.IO) {
            try {
                val f = file ?: return@withContext
                val two = shownBooks.take(MAX_BOOKS)
                val ids = two.map { it.opt("id")?.toString() ?: "" }.filter { it.isNotEmpty() }

                // covers: drop the ones of books that left the cache, download only new / changed ones
                val old = read(userId)?.books.orEmpty().associateBy { it.opt("id")?.toString() ?: "" }
                old.keys.filter { it.isNotEmpty() && it !in ids }.forEach { ViewImageDisk.remove(COVER_SCOPE, it) }
                for (b in two) {
                    val id = b.opt("id")?.toString() ?: continue
                    val url = b.str("cover_url").orEmpty()
                    if (url.isEmpty()) { ViewImageDisk.remove(COVER_SCOPE, id); continue }
                    val same = old[id]?.str("cover_url") == url && ViewImageDisk.read(COVER_SCOPE, id) != null
                    if (!same) download(url)?.let { ViewImageDisk.write(COVER_SCOPE, id, it) }
                }

                val counts = JSONObject()
                ids.forEach { counts.put(it, bookCounts[it] ?: 0) }
                val o = JSONObject()
                    .put("v", VERSION)
                    .put("uid", userId)
                    .put("savedAt", System.currentTimeMillis())
                    .put("books", JSONArray(two))
                    .put("counts", counts)
                    .put("myCount", myCount ?: JSONObject.NULL)
                    .put("S", fin.S)
                    .put("proofs", JSONArray(fin.proofs))
                    .put("pays", JSONArray(fin.pays))
                    .put("notices", JSONArray(notices))
                val tmp = File(f.parentFile, "$FILE.tmp")
                tmp.writeText(o.toString(), Charsets.UTF_8)
                if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) tmp.delete() }
            } catch (_: Exception) { /* the cache is a bonus: never break the page */ }
        } }
    }

    /** Logout: the snapshot and the 2 covers are deleted so the next user never sees them. */
    fun clear() {
        try { file?.delete() } catch (_: Exception) { }
        try { ViewImageDisk.clear(COVER_SCOPE) } catch (_: Exception) { }
    }

    private fun download(url: String): ByteArray? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes()?.takeIf { it.isNotEmpty() } else null
        }
    } catch (_: Exception) { null }
}

package com.srizwan.task.screens.admin

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.str
import org.json.JSONObject

/**
 * React admin/BooksTab.jsx run-state model (`runs`, `runsRef`, `runsLS`, `getRun`, `setRun`) + admin/helpers.js
 * `readRunsLS` / `writeRunsLS` (localStorage kp_bookrun_v1).
 *
 * - [runs]   : bookId -> latest [RunState] fetched from GitHub (React `runs` state; reads recompose the UI).
 * - [runsLS] : bookId -> [RunInfo] (run id + dispatch time) saved on this phone (React `runsLS` ref).
 * `isActiveRun` lives in GitHubApi.kt (2-ঝ-১). Starting a run / polling are added in 2-ঝ-২-খ / 2-ঝ-২-গ.
 */
private const val RK = "kp_bookrun_v1"

/** React `readRunsLS` — a broken / missing value gives an empty map. */
fun readRunsLS(): MutableMap<String, RunInfo> {
    val out = LinkedHashMap<String, RunInfo>()
    try {
        val o = JSONObject(Prefs.getString(RK) ?: "{}")
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.optJSONObject(k) ?: continue
            val at = v.str("at") ?: continue
            out[k] = RunInfo(v.str("id"), at)
        }
    } catch (e: Exception) { /* ignore */ }
    return out
}

/** React `writeRunsLS`. */
fun writeRunsLS(m: Map<String, RunInfo>) {
    try {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONObject().put("id", v.id ?: JSONObject.NULL).put("at", v.at))
        Prefs.putString(RK, o.toString())
    } catch (e: Exception) { /* ignore */ }
}

object RunStore {
    /** bookId -> latest GitHub state (null = not fetched yet -> RunBox shows "fetching"). Snapshot state: UI updates by itself. */
    val runs = mutableStateMapOf<String, RunState>()

    /** Bumped whenever [runsLS] changes (React `bump`), so cards re-read [getRun]. */
    var version by mutableStateOf(0)
        private set

    private val runsLS: MutableMap<String, RunInfo> by lazy { readRunsLS() }

    /** Latest run of a book: Supabase `gh_run_*` columns and this phone's saved run — whichever is newer; null = none. */
    fun getRun(b: JSONObject): RunInfo? {
        @Suppress("UNUSED_VARIABLE") val v = version
        val c = ArrayList<RunInfo>(2)
        val at = b.str("gh_run_at")
        if (!at.isNullOrEmpty()) c.add(RunInfo(b.str("gh_run_id")?.takeIf { it.isNotEmpty() && it != "0" }, at))
        runsLS[b.idStr()]?.let { c.add(it) }
        if (c.isEmpty()) return null
        return c.sortedByDescending { it.at }.first()   // newest ISO time first; tie keeps the Supabase one (stable sort)
    }

    /** Saved-on-this-phone part of React `saveRun` (the Supabase patch is added in 2-ঝ-২-খ). */
    fun saveLocal(bookId: String, info: RunInfo) {
        runsLS[bookId] = info
        writeRunsLS(runsLS)
        version++
    }

    /** Forget a book's saved run (used on delete in 2-ঝ-২-জ). */
    fun clearLocal(bookId: String) {
        if (runsLS.remove(bookId) != null) { writeRunsLS(runsLS); version++ }
        runs.remove(bookId)
    }

    /** React `setRun(id, s)`. */
    fun setRun(bookId: String, s: RunState) { runs[bookId] = s }

    fun stateOf(bookId: String): RunState? = runs[bookId]
}

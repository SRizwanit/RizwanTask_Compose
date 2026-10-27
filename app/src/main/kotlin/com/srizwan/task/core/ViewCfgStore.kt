package com.srizwan.task.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * React `ViewPage` config holder: `cfgRef` + `setCfg(patch, silent)` + `forceCfg`.
 *
 * [cfg] is always the latest value (safe to read after an `await`, like `cfgRef.current`); every change is saved to [Prefs]
 * right away via [ViewConfig.saveCfgTo]. Composables that show config values read [rev] (or [cfg] after reading [rev]) so they
 * recompose when a non-silent change happens.
 */
class ViewCfgStore(private val ctx: ViewCtx) {
    val keys: ViewConfig.Keys = ViewConfig.keysFor(ctx.uid, ctx.bookId)

    /** Saved values + repo / token / book / Gemini from Supabase (React `loadCfg(CK, ctx)`). */
    var cfg: JSONObject = ViewConfig.loadCfg(keys.CK, ctx.book, ctx.settings)
        private set

    /** Bumped on every non-silent change (React `forceCfg`). */
    var rev: Int by mutableIntStateOf(0)
        private set

    /** `setCfg({ ...patch })` — keys of [patch] replace the same keys of the config. */
    fun setCfg(patch: JSONObject, silent: Boolean = false) = setCfg(silent) { cur ->
        JSONObject(cur.toString()).also { n -> for (k in patch.keys()) n.put(k, patch.opt(k)) }
    }

    /** `setCfg(prev => next)`; [silent] = save only, no recomposition (scroll / last page). */
    fun setCfg(silent: Boolean = false, fn: (JSONObject) -> JSONObject) {
        val next = fn(cfg)
        cfg = next
        ViewConfig.saveCfgTo(keys.CK, next)
        if (!silent) rev++
    }

    /** Drafts map (React `draftsRef`), loaded once; the caller saves with [saveDrafts]. */
    val drafts: JSONObject = ViewConfig.loadDrafts(keys.DK)

    /** React `persistDrafts` (without the extension mirror). Returns false when the device storage refused (toast is up to the caller). */
    fun saveDrafts(): Boolean = try { Prefs.putString(keys.DK, drafts.toString()); true } catch (_: Exception) { false }
}

package com.srizwan.task.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * React `ViewPage` API on/off flag: `apiEnabled` / `apiEnabledRef` / `refreshApiFlag` [schema: app_settings.api_enabled boolean].
 *
 * The admin can switch the API off (`app_settings.api_enabled = false`). Empty value / missing column / failed request = on.
 * Starts from the settings loaded with the page, then [refresh] re-reads it (30 s while the page is visible, when the app
 * comes back to the foreground, and right before an API run).
 *
 * Later steps hook in through [onTurnedOff] (abort the running API call, React `apiRunRef.current.ctrl.abort()`) and
 * [onTurnedOn] (React `maybeAutoOcr()`).
 */
class ViewApiFlag(settings: JSONObject, private val toaster: ViewToaster) {

    /** `apiEnabled` — read in composables (recomposes) and in async code (always the latest value, like `apiEnabledRef`). */
    var enabled: Boolean by mutableStateOf(settings.opt("api_enabled") != false)
        private set

    /**
     * `app_settings.ai_link_enabled` — admin switch "GitHub লিংক চালু/বন্ধ" for the AI prompt button.
     * on (default; also when the column is missing) = prompt + GitHub download part + image link, off = the prompt only.
     */
    var aiLink: Boolean by mutableStateOf(settings.opt("ai_link_enabled") != false)
        private set

    /** Reads the AI-link switch right before a copy; a network / column problem keeps the previous value. */
    suspend fun refreshAiLink(): Boolean {
        try {
            val r = Sb.get("app_settings?id=eq.1&select=ai_link_enabled&limit=1").optJSONObject(0)
            aiLink = !(r != null && r.opt("ai_link_enabled") == false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* keep the previous value */ }
        return aiLink
    }

    var onTurnedOff: () -> Unit = {}
    var onTurnedOn: () -> Unit = {}

    /** `refreshApiFlag()` — returns the current state; a network / column problem keeps the previous state. */
    suspend fun refresh(): Boolean {
        try {
            val r = Sb.get("app_settings?id=eq.1&select=api_enabled&limit=1").optJSONObject(0)
            val on = !(r != null && r.opt("api_enabled") == false)
            if (on != enabled) {
                enabled = on
                if (!on) { onTurnedOff(); toaster.toast("অ্যাডমিন এপি আই বন্ধ করেছেন", true) }
                else { toaster.toast("এপি আই চালু হয়েছে"); onTurnedOn() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* network / column problem: the previous state stays */ }
        return enabled
    }
}

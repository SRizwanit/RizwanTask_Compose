package com.srizwan.task.core

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** React `ctx` of `ViewPage`: { uid, uname, isAdmin, book, settings }. */
class ViewCtx(
    val uid: String,
    val uname: String,
    val isAdmin: Boolean,
    /** `books` row */
    val book: JSONObject,
    /** `app_settings` row (id = 1); empty object when it could not be read */
    val settings: JSONObject
) {
    val bookId: String get() = book.opt("id")?.toString() ?: ""
    val bookName: String get() = book.str("name").orEmpty()
}

/** What the entry (React `View()` wrapper) decided. */
sealed interface ViewEntryResult {
    /** Everything loaded: show the page. */
    class Ready(val ctx: ViewCtx) : ViewEntryResult

    /** The book is hidden by the admin: show [message] in a dialog, then go to the task page (React `window.alert` + `navTo('task', true)`). */
    class Hidden(val message: String) : ViewEntryResult

    /** Already redirected (login / task / blocked screen): keep the loading screen, do nothing more. */
    object Stay : ViewEntryResult

    /** The book could not be read because of the network: the loading screen stays and shows the problem (the caller retries). */
    object Failed : ViewEntryResult
}

/**
 * State of the loading screen that covers the View page from the first tap until the first page is ready.
 * [pct] is one 0..100 value for the whole way: account check, book, settings (this file), then repo / page list / first page image + text
 * (fed by `ViewBootEffect` in ViewScreen.kt). Inside a stage the number creeps forward slowly (never past the stage end) while the
 * connection is fine; it stops while offline. Real progress (a finished stage, the image download %) moves it forward at once.
 * [issue] is what the problem toast shows: no internet, slow internet, a failed request, or "internet is back".
 * [retryTick] changes whenever a new try must start (button, or the internet coming back).
 */
@Stable
class ViewBoot {
    enum class Issue { NONE, OFFLINE, SLOW, FAILED, BACK }

    companion object {
        /** No real progress for this long while online = slow internet. */
        const val SLOW_AFTER_MS = 8000L
        /** How long the green "internet is back" toast stays. */
        const val BACK_SHOW_MS = 2500L
    }

    var pct by mutableFloatStateOf(0f); private set
    var detail by mutableStateOf("আপনার কিতাবের তথ্য ও সেটিংস লোড হচ্ছে।"); private set
    var issue by mutableStateOf(Issue.NONE); private set
    var reason by mutableStateOf(""); private set
    var done by mutableStateOf(false); private set
    var retryTick by mutableIntStateOf(0); private set

    private var cap = 8f
    private var advancedAt = SystemClock.elapsedRealtime()
    private var failed = false
    private var backUntil = 0L

    /** A new stage from [from] to [to] percent: the number may creep up to 92% of the stage while it runs. */
    fun stage(from: Float, to: Float, text: String) {
        cap = from + (to - from) * 0.92f
        if (detail != text) detail = text
        reach(from)
    }

    /** Real progress: the number never goes back, and the slow-internet timer starts again. */
    fun reach(v: Float) {
        val x = v.coerceIn(0f, 99f)
        if (x > pct) { pct = x; advancedAt = SystemClock.elapsedRealtime() }
    }

    /** A request failed (the problem toast shows [why]); it stays until a retry starts. */
    fun fail(why: String) { failed = true; reason = why }

    /** A new try is running again (the init started once more): the failure mark goes away. */
    fun clearFail() { if (failed) { failed = false; reason = "" } }

    /** Retry button / the app's net bar button. */
    fun requestRetry() {
        failed = false; reason = ""
        advancedAt = SystemClock.elapsedRealtime()
        if (issue == Issue.FAILED || issue == Issue.SLOW) issue = Issue.NONE
        retryTick++
    }

    /** 100% for a moment, then the screen goes away. */
    suspend fun finish() {
        if (done) return
        pct = 100f
        delay(350)
        done = true
    }

    /** Called about 4 times a second by the screen: creeping number + what the problem toast must show. */
    fun tick(online: Boolean) {
        if (done) return
        val now = SystemClock.elapsedRealtime()
        if (issue == Issue.BACK) {
            if (now < backUntil) return
            issue = Issue.NONE
        }
        if (online && !failed && pct < cap) pct = minOf(cap, pct + maxOf(0.1f, (cap - pct) * 0.05f))
        if (issue == Issue.OFFLINE && online) {            // the internet is back: show it and load again
            failed = false; reason = ""
            backUntil = now + BACK_SHOW_MS; advancedAt = now
            issue = Issue.BACK; retryTick++
            return
        }
        val next = when {
            !online -> Issue.OFFLINE
            failed -> Issue.FAILED
            now - advancedAt > SLOW_AFTER_MS -> Issue.SLOW
            else -> Issue.NONE
        }
        if (next != issue) issue = next
    }
}

/**
 * React `View.jsx` entry (the wrapper above `ViewPage`): auth check, book id from the route, book + settings load.
 * `restoreFromExt` / `requestPersist` / `installFlushListeners` are browser-extension / browser-storage helpers and are not needed on Android.
 */
object ViewEntry {

    suspend fun load(bookId: String?, boot: ViewBoot? = null): ViewEntryResult {
        boot?.stage(0f, 8f, "অ্যাকাউন্ট যাচাই হচ্ছে…")
        val u = Auth.requireAuth() ?: return ViewEntryResult.Stay            // login / device mismatch / blocked -> already handled
        if (bookId.isNullOrEmpty()) { Nav.go(Routes.TASK, replace = true); return ViewEntryResult.Stay }
        boot?.reach(8f)

        boot?.stage(8f, 30f, "কিতাবের তথ্য আনা হচ্ছে…")
        var b: JSONObject? = null
        try { b = Sb.get("books?id=eq.${enc(bookId)}&limit=1").optJSONObject(0) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {                                                // network problem: stay here (no redirect), the loading screen shows it
            boot?.fail(e.message.orEmpty())
            return ViewEntryResult.Failed
        }
        if (b == null) { Nav.go(Routes.TASK, replace = true); return ViewEntryResult.Stay }
        boot?.reach(30f)

        val isAdmin = Phone.isAdminPhone(u.phone)
        if (b.optBoolean("hidden", false) && !isAdmin) {                     // admin hid the book: not reachable via direct link/bookmark either (admin can)
            return ViewEntryResult.Hidden("\"${b.str("name").orEmpty()}\" কিতাবটির এক্সেস অ্যাডমিন সাময়িক বন্ধ রেখেছেন।\nআপনি অন্য কিতাবের কাজ করতে পারেন।")
        }

        boot?.stage(30f, 45f, "সেটিংস লোড হচ্ছে…")
        var s = JSONObject()
        try { s = Sb.get("app_settings?id=eq.1&limit=1").optJSONObject(0) ?: JSONObject() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* ignore */ }
        boot?.reach(45f)

        val uname = u.fullName.takeUnless { it.isNullOrEmpty() } ?: u.raw.str("name").orEmpty()
        return ViewEntryResult.Ready(ViewCtx(uid = u.id, uname = uname, isAdmin = isAdmin, book = b, settings = s))
    }
}

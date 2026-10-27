package com.srizwan.task.core

import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import kotlinx.coroutines.CancellationException
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

    /** Already redirected (login / task / blocked screen) or the network failed: keep the loading screen, do nothing more. */
    object Stay : ViewEntryResult
}

/**
 * React `View.jsx` entry (the wrapper above `ViewPage`): auth check, book id from the route, book + settings load.
 * `restoreFromExt` / `requestPersist` / `installFlushListeners` are browser-extension / browser-storage helpers and are not needed on Android.
 */
object ViewEntry {

    suspend fun load(bookId: String?): ViewEntryResult {
        val u = Auth.requireAuth() ?: return ViewEntryResult.Stay            // login / device mismatch / blocked -> already handled
        if (bookId.isNullOrEmpty()) { Nav.go(Routes.TASK, replace = true); return ViewEntryResult.Stay }

        var b: JSONObject? = null
        try { b = Sb.get("books?id=eq.${enc(bookId)}&limit=1").optJSONObject(0) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* ignore */ }
        if (b == null) { Nav.go(Routes.TASK, replace = true); return ViewEntryResult.Stay }

        val isAdmin = Phone.isAdminPhone(u.phone)
        if (b.optBoolean("hidden", false) && !isAdmin) {                     // admin hid the book: not reachable via direct link/bookmark either (admin can)
            return ViewEntryResult.Hidden("\"${b.str("name").orEmpty()}\" কিতাবটির এক্সেস অ্যাডমিন সাময়িক বন্ধ রেখেছেন।\nআপনি অন্য কিতাবের কাজ করতে পারেন।")
        }

        var s = JSONObject()
        try { s = Sb.get("app_settings?id=eq.1&limit=1").optJSONObject(0) ?: JSONObject() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* ignore */ }

        val uname = u.fullName.takeUnless { it.isNullOrEmpty() } ?: u.raw.str("name").orEmpty()
        return ViewEntryResult.Ready(ViewCtx(uid = u.id, uname = uname, isAdmin = isAdmin, book = b, settings = s))
    }
}

package com.srizwan.task.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * React `ViewPage` "কাজ চলছে" signal (`rpc/book_lock_beat` / `rpc/book_lock_release`) [schema: book_locks].
 *
 * While someone works on a book it beats every 20 s; on leaving it releases the lock. Another user who taps the book on the
 * Task page sees "(name) is working" (`book_lock_of`, already in TaskScreen). The admin never holds a lock.
 * If the Supabase table/functions are missing, every error is ignored silently — the work is never interrupted.
 *
 * [onTaken] gets the holder's name when somebody else is already active on this book (e.g. opened by a direct link);
 * only the first beat can trigger it (afterwards the page stays open, like React).
 */
class ViewLock(private val ctx: ViewCtx, private val onTaken: (holder: String) -> Unit) {
    private var alive = true
    private var held = false

    private fun args(withName: Boolean): JSONObject =
        JSONObject().put("p_book", ctx.bookId).put("p_user", ctx.uid).also { if (withName) it.put("p_name", ctx.uname) }

    /** One beat. Returns quietly on any error. */
    suspend fun beat() {
        if (ctx.isAdmin || !alive) return
        try {
            val r = Sb.post("rpc/book_lock_beat", args(true))
            if (!alive) return
            val holder = (r.opt(0) as? String).orEmpty()          // text = other holder's name, null = lock is ours now
            if (holder.isNotEmpty()) {
                if (!held) { alive = false; onTaken(holder) }
                return
            }
            held = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* ignore */ }
    }

    /** Beat now, then every 20 seconds until cancelled. Run it in a `LaunchedEffect`. */
    suspend fun loop() {
        if (ctx.isAdmin) return
        beat()
        while (alive) { delay(20_000); beat() }
    }

    /** Fire-and-forget release (React `release`, errors ignored). */
    fun release() {
        if (ctx.isAdmin) return
        AppScope.scope.launch {
            try { Sb.post("rpc/book_lock_release", args(false)) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    /** Leaving the page: stop beating and release the lock if we hold it. */
    fun stop() {
        alive = false
        if (held) { held = false; release() }
    }
}

package com.srizwan.task.screens.task

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.Summary
import com.srizwan.task.core.enc
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** React `fin`: settings + my proofed pages + my payments; [ready] = the first money load finished. */
class Fin(val S: JSONObject = JSONObject(), val proofs: List<JSONObject> = emptyList(), val pays: List<JSONObject> = emptyList(), val ready: Boolean = false)

/**
 * React Task.jsx data loaders (3-ক-২): `fetchMine`, `loadCount`, `loadMoney`, `loadCounts`, `load` and the values
 * calculated from them (`booksById`, `shownBooks`, `sum`, `rateOf`). Logic only — the panes that show the data are
 * added in 3-খ … 3-ঘ. Tables / RPCs (rizwantask_full_schema.sql): `page_proofs`, `books`, `payments`, `app_settings`
 * (id = 1: page_rate, min_payout, warn_pct, warn_min), `rpc/my_proof_count(p_user text)`, `rpc/book_proof_counts()`.
 * All ids are Strings.
 */
class TaskData {
    var me by mutableStateOf<AppUser?>(null)

    /** books list; [booksStatus] = loading | ready | error; [spinning] = a refresh of an already shown list. */
    var books by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var booksStatus by mutableStateOf("loading"); private set
    var spinning by mutableStateOf(false); private set
    var bookCounts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var myCount by mutableStateOf<Int?>(null); private set
    var fin by mutableStateOf(Fin()); private set

    /** React `goal` / `goalRef`: my daily goal in pages (saved per user in Prefs, default 20) — 3-খ-১. */
    var goal by mutableStateOf(20)

    /* ---- notices (3-খ-৩): `badge`, `nList`, `nIdx`, the dialog is open while [nOpen] ---- */
    var badge by mutableStateOf(0); private set
    var nList by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var nIdx by mutableStateOf(0)
    var nOpen by mutableStateOf(false); private set
    private var allNotices: List<JSONObject> = emptyList()

    /** React `guide.current.isOpen()` — set by the guide dialog in 3-ঙ-২; a notice never opens over the guide. */
    var guideOpen: () -> Boolean = { false }

    /** React `busy` ref: one loader of each kind at a time. */
    private var busyCnt = false
    private var busyMoney = false

    /** Called with the fresh proofs after every money load — TaskScreen sets it to `goalToast(proofs, goal, me.id)` (3-খ-১). */
    var onMoneyLoaded: ((List<JSONObject>) -> Unit)? = null

    /** React `booksById`: every book, hidden ones too (used for calculations). */
    val booksById: Map<String, JSONObject> by derivedStateOf { books.associateBy { it.opt("id")?.toString() ?: "" } }

    /**
     * Books shown in the list: not hidden by the admin, and either the page count is known (> 0) or someone already
     * proofed pages of it (so older work is not lost). New books appear only after their images are made and counted.
     */
    val shownBooks: List<JSONObject> by derivedStateOf {
        // newest books first (stable order: created_at desc, then name, then id)
        com.srizwan.task.core.BookOrder.sortNewestFirst(books.filter { b ->
            !b.optBoolean("hidden", false) &&
                ((b.finiteNum("total_pages") ?: 0.0) > 0.0 || (bookCounts[b.opt("id")?.toString() ?: ""] ?: 0) > 0)
        })
    }

    /** React `sum`: my earnings summary, null until the first money load. */
    val sum: Summary? by derivedStateOf {
        val f = fin
        if (f.ready) Earnings.summarize(f.proofs, booksById, f.S, f.pays) else null
    }

    /** React `rateOf(book)`. */
    fun rateOf(b: JSONObject?): Double = Earnings.rateOf(b, fin.S)


    /** React `fetchMine`: my proofs with review columns; falls back to the two basic columns on an old schema. */
    private suspend fun fetchMine(uid: String): List<JSONObject> = try {
        Sb.getAll("page_proofs?user_id=eq.${enc(uid)}&select=book_id,stem,review_status,review_note,rate,created_at,reviewed_at&order=created_at.desc,book_id.asc,stem.asc")
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Sb.getAll("page_proofs?user_id=eq.${enc(uid)}&select=book_id,stem&order=book_id.asc,stem.asc")
    }

    /** React `loadCount`: how many pages I proofed (fast RPC, else counted by paging). Keeps the old number on failure. */
    suspend fun loadCount() {
        val u = me ?: return
        if (busyCnt) return
        busyCnt = true
        try {
            var n: Int
            try {
                val r: JSONArray = Sb.post("rpc/my_proof_count", JSONObject().put("p_user", u.id))
                val v = r.opt(0)
                val d = (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull() ?: throw IllegalStateException("bad")
                if (!d.isFinite()) throw IllegalStateException("bad")
                n = d.toInt()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                n = 0                                   // function missing: count directly
                var off = 0
                while (true) {
                    val r = Sb.get("page_proofs?user_id=eq.${enc(u.id)}&select=stem&order=stem.asc&limit=1000&offset=$off")
                    n += r.length()
                    if (r.length() < 1000) break
                    off += 1000
                }
            }
            myCount = n
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* no table / network: keep the old number */ }
        finally { busyCnt = false }
    }

    /** React `loadMoney`: settings + my proofs + my payments. Returns the newest [Fin] (a request needs fresh data). */
    suspend fun loadMoney(): Fin {
        val u = me ?: return fin
        if (busyMoney) return fin
        busyMoney = true
        try {
            var S = fin.S
            try { S = Sb.get("app_settings?id=eq.1&select=page_rate,min_payout,warn_pct,warn_min&limit=1").optJSONObject(0) ?: JSONObject() }
            catch (e: CancellationException) { throw e } catch (e: Exception) { /* keep the old one */ }
            if (books.isEmpty()) {
                try { books = Sb.get("books?select=*&order=created_at.desc").objects() }
                catch (e: CancellationException) { throw e } catch (e: Exception) { /* ignore */ }
            }
            val proofs = fetchMine(u.id)
            val pays = try { Sb.getAll("payments?user_id=eq.${enc(u.id)}&order=created_at.desc") }
                catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            fin = Fin(S, proofs, pays, true)
            onMoneyLoaded?.invoke(proofs)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* network / table problem: keep the old data */ }
        finally { busyMoney = false }
        return fin
    }

    /** React `loadCounts`: proofed pages per book (progress bars) — RPC, else counted from every proof row. */
    suspend fun loadCounts() {
        try {
            val m = LinkedHashMap<String, Int>()
            try {
                val rows = Sb.post("rpc/book_proof_counts", JSONObject())
                for (r in rows.objects()) m[r.opt("book_id")?.toString() ?: "null"] = (r.finiteNum("n") ?: 0.0).toInt()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                m.clear()
                for (r in Sb.getAll("page_proofs?select=book_id,stem&order=book_id.asc,stem.asc")) {
                    val k = r.opt("book_id")?.toString() ?: "null"
                    m[k] = (m[k] ?: 0) + 1
                }
            }
            bookCounts = m
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* no table: the bars are simply absent */ }
    }

    /** React `load`: the book list, then the per-book counts and the money data. */
    suspend fun load() {
        if (books.isEmpty()) booksStatus = "loading" else spinning = true
        try {
            books = Sb.get("books?select=*&order=created_at.desc").objects()
            booksStatus = "ready"
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            AppToast.show("কিতাব লোড হয়নি: ${e.message}", true)
            if (books.isEmpty()) booksStatus = "error"
        } finally { spinning = false }
        loadCounts(); loadMoney()
    }

    /** React `openNotices`: show these notices, starting at the first (the Task page passes oldest first, the Profile button newest first). */
    fun showNotices(list: List<JSONObject>) { nList = list; nIdx = 0; if (!nOpen) nOpen = true }

    /**
     * React `loadNotices`: notices for everyone (`user_id` null) or for me, newest 40. The ones I have not seen and that
     * are not older than my sign-up (minus a day) set the badge and open the dialog. Table `notices`
     * (id, user_id, title, body, kind, created_at); a missing table is ignored.
     * [autoOpen] = false is the Profile page: it only sets the badge (React Profile `loadNotices` never opens the dialog);
     * the notices open from its "নোটিশ ও ঘোষণা" button. Returns the loaded list (newest first; empty on a failure).
     */
    suspend fun loadNotices(autoOpen: Boolean = true): List<JSONObject> {
        val u = me ?: return emptyList()
        try {
            val list = Sb.get("notices?or=" + enc("(user_id.is.null,user_id.eq.${u.id})") + "&order=created_at.desc&limit=40").objects()
            allNotices = list
            val seen = readSeen(u.id)
            val joined = Fmt.parseInstant(u.raw.str("created_at"))?.toEpochMilli()?.minus(86400000L) ?: 0L
            val fresh = list.filter { n ->
                val t = Fmt.parseInstant(n.str("created_at"))?.toEpochMilli()
                (n.opt("id")?.toString() ?: "") !in seen && t != null && t >= joined
            }.reversed()
            badge = fresh.size
            if (autoOpen && fresh.isNotEmpty() && !nOpen && !guideOpen()) showNotices(fresh)
            return list
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* no notices table */ }
        return emptyList()
    }

    /** React `NoticeDialog` close + `onNoticeClosed`: every notice that was shown counts as seen; the badge is recounted. */
    fun closeNotices() {
        if (!nOpen) return
        nOpen = false
        val u = me ?: return
        val s = readSeen(u.id)
        nList.forEach { s.add(it.opt("id")?.toString() ?: "") }
        writeSeen(u.id, s)
        badge = allNotices.count { (it.opt("id")?.toString() ?: "") !in s }
    }

    /** React `refreshAll`. */
    suspend fun refreshAll() { loadCount(); loadCounts(); loadMoney(); loadNotices() }

    /** React `retryAll`: no books yet -> full load, else refresh. */
    suspend fun retryAll() { if (books.isEmpty()) load() else refreshAll() }
}

package com.srizwan.task.screens.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Sb
import com.srizwan.task.core.Summary
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

class ProofStat(var n: Int = 0, var today: Int = 0, var w7: Int = 0, var last: Long = 0L, val list: MutableList<JSONObject> = ArrayList())
class BookStat(var n: Int = 0, var a: Int = 0, var p: Int = 0, var c: Int = 0)
data class RvFilter(val st: String = "pending", val user: String = "", val book: String = "")
data class LgFilter(val user: String = "", val act: String = "")

/**
 * React Admin.jsx state + admin/useAdminData.js: shared users, books, settings, proofs, payments
 * and the derived statistics. Tabs read these and call the loaders / patchers.
 */
class AdminViewModel : ViewModel() {
    var users by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var books by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var S by mutableStateOf(JSONObject()); private set
    var proofs by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var pays by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var proofsLoaded by mutableStateOf(false); private set
    var payTbl by mutableStateOf(true); private set

    /** user id -> stats (buildStats) */
    var pst by mutableStateOf<Map<String, ProofStat>>(emptyMap()); private set
    /** book id -> stats */
    var bst by mutableStateOf<Map<String, BookStat>>(emptyMap()); private set
    var booksMap by mutableStateOf<Map<String, JSONObject>>(emptyMap()); private set

    // ---- tab state (Admin.jsx) ----
    var tab by mutableStateOf("Dash"); private set
    /** tab -> how many times it was opened; a tab re-fetches its own data when this grows. */
    var keys by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var rvFilter by mutableStateOf(RvFilter()); private set
    var noticeTo by mutableStateOf(""); private set
    var noticeFocus by mutableIntStateOf(0); private set
    var logFilter by mutableStateOf(LgFilter()); private set

    private var proofsAt = 0L
    private var started = false

    fun openKey(k: String): Int = keys[k] ?: 0
    fun openTab(k: String) { tab = k; keys = keys + (k to openKey(k) + 1) }
    fun updateRvFilter(f: RvFilter) { rvFilter = f }
    fun updateNoticeTo(v: String) { noticeTo = v }
    fun updateLogFilter(f: LgFilter) { logFilter = f }

    /** Jumps from Users / Dash to other tabs. */
    fun reviewUser(uid: String, resetBook: Boolean) {
        rvFilter = RvFilter(st = "", user = uid, book = if (resetBook) "" else rvFilter.book)
        openTab("Review")
    }
    private var noticeFocusPending = false
    fun messageUser(uid: String) { noticeTo = uid; noticeFocus += 1; noticeFocusPending = true; openTab("Notice") }
    /** True once after "বার্তা" was pressed on the Users tab: the Notice tab then focuses its title field. */
    fun takeNoticeFocus(): Boolean = noticeFocusPending.also { noticeFocusPending = false }
    fun logUser(uid: String) { logFilter = logFilter.copy(user = uid); openTab("Log") }

    /** First load after the admin guard passed (once). */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { loadUsers() }
        viewModelScope.launch { fetchS(); loadBooks() }
    }

    // ---- setters (keep derived values in sync) ----
    fun updateS(v: JSONObject) { S = v }
    fun updatePays(v: List<JSONObject>) { pays = v }
    fun updateProofs(v: List<JSONObject>) { proofs = v; buildStats() }
    private fun updateUsers(v: List<JSONObject>) { users = v }
    fun updateBooks(v: List<JSONObject>) { books = v; booksMap = v.associateBy { it.idStr() } }

    fun uname(id: String?): String = users.firstOrNull { it.idStr() == id }?.str("full_name") ?: "ইউজার"
    fun bname(id: String?): String = books.firstOrNull { it.idStr() == id }?.str("name") ?: "কিতাব"

    // ---- loaders ----
    private fun toast(e: Exception) = AppToast.show(e.message ?: "", true)

    suspend fun loadUsers(): List<JSONObject> = try {
        Sb.get("app_users?select=*&order=created_at.desc").objects().also { updateUsers(it) }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e); users }

    suspend fun loadBooks(): List<JSONObject> = try {
        Sb.get("books?select=*&order=created_at.desc").objects().also { updateBooks(it) }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e); books }

    suspend fun fetchS(): JSONObject {
        var s = JSONObject()
        try {
            s = Sb.get("app_settings?id=eq.1&limit=1").optJSONObject(0) ?: JSONObject()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { s = JSONObject(); toast(e) }
        updateS(s)
        return s
    }

    /** Proofs are cached for 20 s unless [force]. Falls back to fewer columns when the review columns do not exist. */
    suspend fun loadProofs(force: Boolean = false): List<JSONObject> {
        if (!force && proofsAt != 0L && System.currentTimeMillis() - proofsAt < 20000) return proofs
        val r: List<JSONObject> = try {
            Sb.getAll("page_proofs?select=user_id,book_id,stem,review_status,rate,created_at&order=book_id.asc,stem.asc")
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            try {
                Sb.getAll("page_proofs?select=user_id,book_id,stem&order=book_id.asc,stem.asc")
            } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { emptyList() }
        }
        proofsAt = System.currentTimeMillis()
        updateProofs(r)
        proofsLoaded = true
        return r
    }

    suspend fun loadPaysQuiet() {
        try {
            updatePays(Sb.getAll("payments?order=created_at.desc,id.desc")); payTbl = true
        } catch (e: CancellationException) { throw e } catch (e: Exception) { updatePays(emptyList()); payTbl = false }
    }

    /** Proofs + payments (money screens). */
    suspend fun ensureMoney() { loadProofs(); loadPaysQuiet() }

    // ---- local updates after a server write ----
    /** Marks every page in [keyset] (rvKey) as approved / needing correction. */
    fun applyStatus(keyset: Set<String>, st: String, note: String?) {
        val S0 = S
        val bm = booksMap
        updateProofs(proofs.map { p ->
            if (rvKey(p) in keyset) p.copyWith(bodyFor(S0, st, note, bm[p.opt("book_id")?.toString() ?: ""])) else p
        })
    }

    fun patchPay(id: String, patch: JSONObject) { pays = pays.map { if (it.idStr() == id) it.copyWith(patch) else it } }
    fun patchBook(id: String, patch: JSONObject) { updateBooks(books.map { if (it.idStr() == id) it.copyWith(patch) else it }) }

    // ---- statistics (old buildStats) ----
    private fun buildStats() {
        val a = t0()
        val w = d7()
        val ps = HashMap<String, ProofStat>()
        val bs = HashMap<String, BookStat>()
        for (p in proofs) {
            val k = p.opt("user_id")?.toString() ?: "null"
            val x = ps.getOrPut(k) { ProofStat() }
            val t = tm(p.str("created_at"))
            x.n++; x.list.add(p)
            if (t >= a) x.today++
            if (t >= w) x.w7++
            if (t > x.last) x.last = t
            val b = bs.getOrPut(p.opt("book_id")?.toString() ?: "null") { BookStat() }
            b.n++
            when (status(p)) { "approved" -> b.a++; "correction" -> b.c++; else -> b.p++ }
        }
        pst = ps
        bst = bs
    }

    fun lastActive(u: JSONObject): Long = maxOf(tm(u.str("last_active_at")), pst[u.idStr()]?.last ?: 0L)

    fun userSum(uid: String): Summary =
        Earnings.summarize(pst[uid]?.list ?: emptyList(), booksMap, S, pays.filter { it.str("user_id") == uid })
}

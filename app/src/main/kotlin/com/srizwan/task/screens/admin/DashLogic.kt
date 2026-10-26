package com.srizwan.task.screens.admin

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.Instant

/** One summary tile: [n] = value text, [label], [hl] = highlighted (React `[n, l, hl]`). */
data class DashTile(val n: String, val label: String, val hl: Boolean = false)

/** Activity report row (React `activity`). */
data class ActRow(val u: JSONObject, val n: Int, val today: Int, val w7: Int, val la: Long, val ap: Int, val co: Int, val act: Boolean)

/** Per-user correction-rate row (React `corr`). [pc] = correction percent, null when nothing was reviewed yet. */
data class CorrRow(val u: JSONObject, val ap: Int, val co: Int, val pe: Int, val rv: Int, val pc: Int?, val tot: Int)

/** Leaderboard entry: user id + proofed page count. */
data class LbRow(val uid: String, val n: Int)

/**
 * React admin/DashTab.jsx lines 1-99 — data loading and all calculations (`load`, the `useMemo`s, `approveUserAll`).
 * Logic only; the UI (tiles, cards) is added in 2-ঞ-২ / 2-ঞ-৩. Every calculated list is a `derivedStateOf`, so it
 * recomputes only when the ViewModel data (users / proofs / pays / pst / booksMap / S) or a filter changes.
 * Data comes from tables `app_users`, `page_proofs`, `payments` through the existing loaders of [AdminViewModel].
 */
class DashState(private val vm: AdminViewModel) {
    var loading by mutableStateOf(true); private set
    /** Time of the last refresh (epoch ms); null = never. */
    var at by mutableStateOf<Long?>(null); private set
    /** Leaderboard period: day | week | all (React `dP`). */
    var dP by mutableStateOf("day"); private set
    /** Activity filter: all | act | idle (React `rF`). */
    var rF by mutableStateOf("all"); private set
    /** Correction filter: all | high | pend (React `crF`). */
    var crF by mutableStateOf("all"); private set
    /** User id whose one-click approval is running (React `busyUid`). */
    var busyUid by mutableStateOf(""); private set

    // Keep the Kotlin method names used by the UI, but give their JVM names distinct
    // from the generated property setters (setDP, setRF, setCrF).
    @JvmName("updateDP")
    fun setDP(v: String) { dP = v }

    @JvmName("updateRF")
    fun setRF(v: String) { rF = v }

    @JvmName("updateCrF")
    fun setCrF(v: String) { crF = v }

    /** "হালনাগাদ: …" text, empty before the first load (React `dAt`). */
    val atText: String get() = at?.let { "হালনাগাদ: " + Fmt.fmtDT(Instant.ofEpochMilli(it).toString()) } ?: ""

    /** React `load`: users first, then proofs (forced) and payments together. Called on every open of the tab. */
    suspend fun load() {
        loading = true
        try {
            vm.loadUsers()
            coroutineScope {
                val a = async { vm.loadProofs(true) }
                val b = async { vm.loadPaysQuiet() }
                a.await(); b.await()
            }
        } finally { loading = false }
        at = System.currentTimeMillis()
    }

    /** Users without the admin phone (React `non`). */
    val non: List<JSONObject> by derivedStateOf { vm.users.filter { !Phone.isAdminPhone(it.str("phone")) } }

    /** Summary tiles (React `tiles`). */
    val tiles: List<DashTile> by derivedStateOf {
        val non = non; val proofs = vm.proofs; val pays = vm.pays
        val ids = non.map { it.idStr() }.toSet()
        val t0 = t0(); val w0 = d7()
        val a1 = HashSet<String>(); val a7 = HashSet<String>()
        for (u in non) {
            val t = tm(u.str("last_active_at"))
            if (t >= t0) a1.add(u.idStr())
            if (t >= w0) a7.add(u.idStr())
        }
        var today = 0
        for (p in proofs) {
            val t = tm(p.str("created_at")); val k = p.opt("user_id")?.toString() ?: "null"
            if (t >= t0) { today++; if (k in ids) a1.add(k) }
            if (t >= w0 && k in ids) a7.add(k)
        }
        val pend = proofs.count { status(it) == "pending" }
        val rq = pays.filter { it.str("status") == "requested" }
        val rqAmt = rq.sumOf { it.finiteNum("amount") ?: 0.0 }
        listOf(
            DashTile(Fmt.bn(non.size), "মোট ইউজার", true),
            DashTile(Fmt.bn(today), "আজকের প্রুফড পেইজ", true),
            DashTile(Fmt.bn(a1.size), "আজ সক্রিয় ইউজার", true),
            DashTile(Fmt.bn(a7.size), "৭ দিনে সক্রিয়"),
            DashTile(Fmt.bn(vm.proofs.size), "মোট প্রুফড পেইজ"),
            DashTile(Fmt.bn(pend), "রিভিউ বাকি"),
            DashTile(Fmt.bn(rq.size) + " (" + Fmt.money(rqAmt) + ")", "পেমেন্ট রিকোয়েস্ট"),
            DashTile(Fmt.bn(vm.users.count { it.optBoolean("is_blocked", false) }), "ব্লকড ইউজার")
        )
    }

    /** Leaderboard, top 15; pages needing correction are not counted (React `lb`). */
    val lb: List<LbRow> by derivedStateOf {
        val since = when (dP) { "day" -> t0(); "week" -> d7(); else -> 0L }
        val m = LinkedHashMap<String, Int>()
        for (p in vm.proofs) {
            if (p.str("review_status") == "correction") continue
            if (since != 0L && tm(p.str("created_at")) < since) continue
            val k = p.opt("user_id")?.toString() ?: "null"
            m[k] = (m[k] ?: 0) + 1
        }
        m.entries.sortedByDescending { it.value }.take(15).map { LbRow(it.key, it.value) }
    }

    /** Activity report (React `activity`). */
    val activity: List<ActRow> by derivedStateOf {
        val w0 = d7(); val pst = vm.pst
        var list = non.map { u ->
            val x = pst[u.idStr()]
            val sm = Earnings.summarize(x?.list ?: emptyList(), vm.booksMap, vm.S, emptyList())
            val w7 = x?.w7 ?: 0
            ActRow(u, x?.n ?: 0, x?.today ?: 0, w7, vm.lastActive(u), sm.approved, sm.correction, w7 > 0 || tm(u.str("last_active_at")) >= w0)
        }
        if (rF == "act") list = list.filter { it.act } else if (rF == "idle") list = list.filter { !it.act }
        list.sortedWith(compareByDescending<ActRow> { it.w7 }.thenByDescending { it.n }.thenByDescending { it.la })
    }

    /** Warning limits (React `warnLimits(S)` -> `wp` %, `wm` minimum reviewed pages). */
    val limits: Pair<Double, Double> by derivedStateOf { warnLimits(vm.S) }

    /** React `high(r)`: enough reviewed pages and a correction rate at or above the limit. */
    fun high(r: CorrRow): Boolean = r.pc != null && r.rv >= limits.second && r.pc >= limits.first

    /** Per-user correction rate (React `corr`). */
    val corr: List<CorrRow> by derivedStateOf {
        val pst = vm.pst
        var rows = non.map { u ->
            var ap = 0; var co = 0; var pe = 0
            for (p in pst[u.idStr()]?.list ?: emptyList()) when (status(p)) { "approved" -> ap++; "correction" -> co++; else -> pe++ }
            val rv = ap + co
            CorrRow(u, ap, co, pe, rv, if (rv > 0) Math.round(co.toDouble() / rv * 100).toInt() else null, ap + co + pe)
        }.filter { it.tot > 0 }
        if (crF == "high") rows = rows.filter { high(it) } else if (crF == "pend") rows = rows.filter { it.pe > 0 }
        rows.sortedWith(
            compareByDescending<CorrRow> { it.pc ?: -1 }.thenByDescending { it.co }.thenByDescending { it.tot }
        )
    }

    /** React `approveUserAll`: approve every pending page of one user at once (asks first). */
    private suspend fun approveUserAll(uid: String, ask: AskController) {
        val fresh = vm.loadProofs(true)
        val m = fresh.filter { (it.opt("user_id")?.toString() ?: "") == uid && status(it) == "pending" }
        if (m.isEmpty()) { AppToast.show("এই ইউজারের রিভিউ-বাকি পেইজ নেই"); return }
        if (!ask.confirm(vm.uname(uid) + "-এর রিভিউ বাকি সব " + Fmt.bn(m.size) + "টি পেইজ একসাথে অনুমোদন করবেন?")) return
        val S0 = vm.S
        for (bid in m.map { it.opt("book_id")?.toString() ?: "null" }.distinct()) {
            Sb.patchMin(
                "page_proofs?book_id=eq." + enc(bid) + "&user_id=eq." + enc(uid) + "&review_status=eq.pending",
                bodyFor(S0, "approved", "", vm.booksMap[bid])
            )
        }
        vm.applyStatus(m.map { rvKey(it) }.toSet(), "approved", null)
        ActivityLog.log("admin_approve", Fmt.bn(m.size) + " পেইজ (এক ক্লিকে) • " + vm.uname(uid))
        AppToast.show(Fmt.bn(m.size) + "টি পেইজ অনুমোদিত হয়েছে")
        at = System.currentTimeMillis()
    }

    /** React `onApprove`: wraps [approveUserAll] with the busy flag and the error toast. */
    suspend fun onApprove(uid: String, ask: AskController) {
        busyUid = uid
        try { approveUserAll(uid, ask) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { AppToast.show(e.message.orEmpty(), true) }
        finally { busyUid = "" }
    }
}

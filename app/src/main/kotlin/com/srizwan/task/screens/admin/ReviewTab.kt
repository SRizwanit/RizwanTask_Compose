package com.srizwan.task.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import com.srizwan.task.nav.Nav
import com.srizwan.task.ui.PAGE_SIZE
import com.srizwan.task.ui.PagerBarOpen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** `busy` ref of React ReviewTab (not UI state). */
private class RvLoader { var busy = false }

private fun JSONObject.bookKey(): String = opt("book_id")?.toString() ?: "null"
private fun JSONObject.userKey(): String = opt("user_id")?.toString() ?: "null"

/**
 * React admin/ReviewTab.jsx — approve / mark "needs correction" for proofed pages (one, selected, or everything the
 * filter matches) + a warning notice to the user when the mistake rate is high.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewTab(vm: AdminViewModel, ask: AskController, openKey: Int) {
    val filter = vm.rvFilter
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    // 0-based page (20 rows per page, loaded from the server); a new filter starts again from page 1
    var page by remember(filter.st, filter.user, filter.book) { mutableStateOf(0) }
    var warn by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyKey by remember { mutableStateOf("") }
    var okAllBusy by remember { mutableStateOf(false) }
    val ld = remember { RvLoader() }
    val cs = MaterialTheme.colorScheme

    fun toast(m: String, bad: Boolean = false) = AppToast.show(m, bad)

    /** Fetches [p] (0-based page) for the filter that is current *now*; PAGE_SIZE + 1 rows are asked for to know whether a next page exists. */
    suspend fun load(p: Int, clearSel: Boolean = true) {
        if (ld.busy) return
        ld.busy = true
        try {
            if (clearSel) sel = emptySet()
            val f = vm.rvFilter
            var q = ""
            if (f.st.isNotEmpty()) q += "&review_status=eq." + f.st
            if (f.user.isNotEmpty()) q += "&user_id=eq." + enc(f.user)
            if (f.book.isNotEmpty()) q += "&book_id=eq." + enc(f.book)
            try {
                val r = Sb.get(
                    "page_proofs?select=book_id,stem,user_id,review_status,review_note,created_at,reviewed_at,rate" + q +
                        "&order=created_at.desc,book_id.asc,stem.asc&limit=${PAGE_SIZE + 1}&offset=${p * PAGE_SIZE}"
                ).objects()
                rows = r.take(PAGE_SIZE); more = r.size > PAGE_SIZE; warn = ""
                // the last row of a later page was handled: step back to the previous page instead of showing an empty one
                if (rows.isEmpty() && p > 0) page = p - 1
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val m = e.message.orEmpty()
                warn = if (Regex("review_status|created_at|42703|PGRST", RegexOption.IGNORE_CASE).containsMatchIn(m)) "রিভিউ কলাম নেই — $SQLHINT।" else m
            }
        } finally { ld.busy = false }
    }

    // Opening the tab, changing a filter or turning the page loads that page (old onchange -> loadReview).
    LaunchedEffect(openKey, filter.st, filter.user, filter.book, page) { if (openKey > 0) load(page) }

    /** When a user's mistake rate is over the limit: a warning notice (at most once per 12 hours). */
    suspend fun maybeWarn(uids: List<String>) {
        val (wp, wm) = warnLimits(vm.S)
        for (uid in uids) {
            val list = vm.proofs.filter { it.userKey() == uid }
            val ap = list.count { it.str("review_status") == "approved" }
            val co = list.count { it.str("review_status") == "correction" }
            val tot = ap + co
            if (tot < wm) continue
            val pc = Math.floor(co.toDouble() / tot * 100 + 0.5).toInt()   // JS Math.round
            if (pc < wp) continue
            try {
                val last = Sb.get("notices?user_id=eq.${enc(uid)}&kind=eq.review&order=created_at.desc&limit=1").optJSONObject(0)
                if (last != null && System.currentTimeMillis() - tm(last.str("created_at")) < 12 * 3600_000L) continue
                sendNotice(
                    uid, "প্রুফে ভুল বেশি ধরা পড়েছে",
                    "আপনার রিভিউ হওয়া ${Fmt.bn(tot)}টি পেইজের মধ্যে ${Fmt.bn(co)}টিতে (${Fmt.bn(pc)}%) সংশোধন দরকার হয়েছে।\n" +
                        "দয়া করে আরও মনোযোগ দিয়ে প্রুফ করুন — \"সংশোধন দরকার\" পেইজে পয়েন্টস গোনা হয় না। " +
                        "\"পেমেন্ট\" ট্যাবের তালিকা থেকে পেইজ খুলে ঠিক করে আবার \"প্রুফড\" চাপুন।",
                    "review"
                )
                toast(vm.uname(uid) + " কে ভুলের সতর্কবার্তা পাঠানো হয়েছে")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Writes the review state of [items] to the server (per book, 40 stems per request), then updates the local lists. */
    suspend fun setReview(items: List<JSONObject>, st: String, note: String? = null) {
        val S0 = vm.S
        val bm = vm.booksMap
        val by = LinkedHashMap<String, MutableList<String>>()
        items.forEach { by.getOrPut(it.bookKey()) { ArrayList() }.add(it.str("stem").orEmpty()) }
        for ((bid, stems) in by) {
            val body = bodyFor(S0, st, note, bm[bid])
            for (i in stems.indices step 40) {
                val part = stems.subList(i, minOf(i + 40, stems.size))
                    .joinToString(",") { "\"" + it.replace(Regex("([\"\\\\])"), "\\\\$1") + "\"" }
                Sb.patchMin("page_proofs?book_id=eq.${enc(bid)}&stem=in.${enc("($part)")}", body)
            }
        }
        val ks = items.map { rvKey(it) }.toSet()
        vm.applyStatus(ks, st, note)
        var next = rows.map { p -> if (rvKey(p) in ks) p.copyWith(bodyFor(S0, st, note, bm[p.bookKey()])) else p }
        val fst = vm.rvFilter.st
        if (fst.isNotEmpty()) next = next.filter { status(it) == fst }
        rows = next
        sel = sel.filter { k -> next.any { rvKey(it) == k } }.toSet()
        val uids = items.map { it.userKey() }.distinct()
        ActivityLog.log(
            if (st == "approved") "admin_approve" else "admin_correction",
            items.size.toString() + " পেইজ • " + uids.take(3).joinToString(", ") { vm.uname(it) } +
                (if (!note.isNullOrEmpty()) " • $note" else "")
        )
        if (st == "correction") maybeWarn(uids)
    }

    // Server writes run on the app scope so that switching tabs does not cancel them half-way.
    fun one(r: JSONObject, a: String) {
        val k = rvKey(r)
        busyKey = k
        AppScope.scope.launch {
            try {
                if (a == "ok") {
                    setReview(listOf(r), "approved"); toast("অনুমোদিত")
                } else {
                    val note = ask.ask(title = "সংশোধন দরকার", label = "ইউজারের জন্য নোট — কী ঠিক করতে হবে (ঐচ্ছিক)", ok = "চিহ্নিত করুন") ?: return@launch
                    setReview(listOf(r), "correction", note); toast("সংশোধন দরকার চিহ্নিত হয়েছে")
                }
                load(page, clearSel = false)   // refill the page to 20 rows
            } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e.message.orEmpty(), true) } finally { busyKey = "" }
        }
    }

    fun toggle(r: JSONObject, on: Boolean) {
        val k = rvKey(r)
        sel = if (on) sel + k else sel - k
    }

    fun selectAll() {
        val all = rows.isNotEmpty() && rows.all { rvKey(it) in sel }
        sel = if (all) sel - rows.map { rvKey(it) }.toSet() else sel + rows.map { rvKey(it) }
    }

    fun okSel() {
        AppScope.scope.launch {
            val items = rows.filter { rvKey(it) in sel }
            if (items.isEmpty()) { toast("কিছু নির্বাচন করুন", true); return@launch }
            if (!ask.confirm(Fmt.bn(items.size) + "টি পেইজ অনুমোদন করবেন?")) return@launch
            try {
                setReview(items, "approved"); toast(Fmt.bn(items.size) + "টি পেইজ অনুমোদিত")
                load(page, clearSel = false)   // refill the page to 20 rows
            } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e.message.orEmpty(), true) }
        }
    }

    fun okAll() {
        AppScope.scope.launch {
            val f = vm.rvFilter
            val st = f.st; val u = f.user; val bk = f.book
            if (st == "approved") { toast("এগুলো আগেই অনুমোদিত", true); return@launch }
            val fresh = vm.loadProofs(true)
            val m = fresh.filter { p ->
                (u.isEmpty() || p.userKey() == u) && (bk.isEmpty() || p.bookKey() == bk) &&
                    (if (st.isNotEmpty()) status(p) == st else status(p) != "approved")
            }
            if (m.isEmpty()) { toast("এই ফিল্টারে অনুমোদনের মতো পেইজ নেই"); return@launch }
            if (!ask.confirm(
                    "ফিল্টারের সব ${Fmt.bn(m.size)}টি পেইজ অনুমোদন করবেন? (ইউজার: ${if (u.isNotEmpty()) vm.uname(u) else "সবাই"}, কিতাব: ${if (bk.isNotEmpty()) vm.bname(bk) else "সব"})"
                )
            ) return@launch
            okAllBusy = true
            try {
                val S0 = vm.S
                for (bid in m.map { it.bookKey() }.distinct()) {
                    Sb.patchMin(
                        "page_proofs?book_id=eq.${enc(bid)}" + (if (u.isNotEmpty()) "&user_id=eq.${enc(u)}" else "") +
                            (if (st.isNotEmpty()) "&review_status=eq.$st" else "&review_status=neq.approved"),
                        bodyFor(S0, "approved", "", vm.booksMap[bid])
                    )
                }
                vm.applyStatus(m.map { rvKey(it) }.toSet(), "approved", null)
                ActivityLog.log("admin_approve", Fmt.bn(m.size) + " পেইজ (একসাথে) • " + (if (u.isNotEmpty()) vm.uname(u) else "সব ইউজার"))
                toast(Fmt.bn(m.size) + "টি পেইজ অনুমোদিত হয়েছে")
                if (page != 0) page = 0 else load(0)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e.message.orEmpty(), true) } finally { okAllBusy = false }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (warn.isNotEmpty()) Column(Modifier.padding(bottom = 10.dp)) { NoticeBox(warn) }

        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            val w = Modifier.widthIn(min = 120.dp, max = 170.dp)
            AdminSelect(
                filter.st,
                listOf("pending" to "রিভিউ বাকি", "correction" to "সংশোধন দরকার", "approved" to "অনুমোদিত", "" to "সব"),
                onPick = { vm.updateRvFilter(filter.copy(st = it)) }, modifier = w
            )
            AdminSelect(
                filter.user,
                listOf("" to "সব ইউজার") + vm.users.map { it.idStr() to "${it.str("full_name").orEmpty()} (${it.str("phone").orEmpty()})" },
                onPick = { vm.updateRvFilter(filter.copy(user = it)) }, modifier = w
            )
            AdminSelect(
                filter.book,
                listOf("" to "সব কিতাব") + vm.books.map { it.idStr() to it.str("name").orEmpty() },
                onPick = { vm.updateRvFilter(filter.copy(book = it)) }, modifier = w
            )
            Badge("পেইজ ${Fmt.bn(page + 1)} • ${Fmt.bn(rows.size)}টি")
            SmBtn("", "refresh") { AppScope.scope.launch { load(page) } }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmBtn("সব নির্বাচন") { selectAll() }
            SmBtn("নির্বাচিত অনুমোদন", "check", tone = "ok") { okSel() }
            SmBtn("ফিল্টারের সব অনুমোদন", tone = "ok", enabled = !okAllBusy) { okAll() }
        }

        if (rows.isEmpty()) {
            Empty("এই ফিল্টারে কোনো পেইজ নেই", art = "list")
        } else {
            rows.forEach { r ->
                val k = rvKey(r)
                key(k) {
                    val st = STB[status(r)] ?: STB.getValue("pending")
                    val busy = busyKey == k
                    LiCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = k in sel, onCheckedChange = { toggle(r, it) })
                            Column(Modifier.weight(1f)) {
                                Text("${vm.bname(r.str("book_id"))} — ${r.str("stem").orEmpty()}", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val reviewed = !r.str("reviewed_at").isNullOrEmpty() && r.str("review_status") != "pending"
                                Text(
                                    vm.uname(r.str("user_id")) + " • " + Fmt.fmtDT(r.str("created_at")) +
                                        (if (reviewed) " • রিভিউ: " + Fmt.fmtDT(r.str("reviewed_at")) else ""),
                                    color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp
                                )
                            }
                            Badge(st.first, st.second)
                        }
                        if (!r.str("review_note").isNullOrEmpty()) {
                            Text("নোট: ${r.str("review_note")}", color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            SmBtn("অনুমোদন", "check", tone = "ok", enabled = !busy) { one(r, "ok") }
                            SmBtn("সংশোধন দরকার", "x", tone = "warn", enabled = !busy) { one(r, "bad") }
                            // React: opens /view?book=…&page=… in a new tab; here: the in-app View route (View screen arrives in step 4).
                            Box(Modifier.clickable { Nav.go("view?book=${enc(r.opt("book_id")?.toString() ?: "")}&page=${enc(r.str("stem").orEmpty())}") }) {
                                Badge("পেইজ দেখুন", "gold")
                            }
                        }
                    }
                }
            }
        }
        PagerBarOpen(page = page, hasNext = more, onPage = { page = it })
    }
}

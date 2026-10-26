package com.srizwan.task.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.openUrl
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.HintText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/** (key, label) — React FLT. running / done / failed come from [RunStore.runs] (filled by RunPoller); the chips show the live counts (React `fcount`). */
private val FLT = listOf(
    "all" to "সব", "hidden" to "লুকানো", "notshown" to "Task-এ দেখাচ্ছে না",
    "running" to "ছবি তৈরি চলছে", "done" to "ছবি তৈরি সম্পন্ন", "failed" to "ব্যর্থ / বাতিল"
)
private val SORTS = listOf(
    "new" to "নতুন আগে", "old" to "পুরনো আগে", "name" to "নাম (অ–ঔ / A–Z)",
    "prog_desc" to "অগ্রগতি: বেশি আগে", "prog_asc" to "অগ্রগতি: কম আগে",
    "left_desc" to "বাকি পেইজ: বেশি আগে", "left_asc" to "বাকি পেইজ: কম আগে"
)
private const val HIDDEN_SQL = "Supabase SQL Editor-এ চালান: alter table books add column if not exists hidden boolean not null default false;"

/** React `st(b)` — per-book values used by the filters, the sort and the card. */
private class BkView(val x: BookStat, val tot: Double, val hidden: Boolean, val notShown: Boolean, val running: Boolean, val done: Boolean, val failed: Boolean)

private fun matchF(k: String, v: BkView): Boolean =
    k == "all" || (k == "hidden" && v.hidden) || (k == "notshown" && v.notShown) ||
        (k == "running" && v.running) || (k == "done" && v.done) || (k == "failed" && v.failed)

/**
 * React admin/BooksTab.jsx — parts 2-জ-১ (list, search / filter / sort, per-book progress, hide / show, delete,
 * selection mode with bulk hide / show / rate / delete) and 2-জ-২ (header buttons + the dialogs in BookDialogs.kt).
 * 2-ঝ-১: page counting. 2-ঝ-২-ক/খ: run box + start run. 2-ঝ-২-গ: status polling + automatic count after a successful run (RunPoller.kt). Still to come (2-ঝ-২-ঘ…): run filter counts check, retry-all, bulk run, run options in dialogs / import.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BooksTab(vm: AdminViewModel, ask: AskController, openKey: Int) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val f = vm.booksFilter
    var selMode by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkBusy by remember { mutableStateOf(false) }
    var rateOpen by remember { mutableStateOf(false) }
    var rateVal by remember { mutableStateOf("") }
    var rateErr by remember { mutableStateOf("") }
    var rateList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var bookDlg by remember { mutableStateOf(false) }                       // new / edit dialog open
    var editBook by remember { mutableStateOf<JSONObject?>(null) }          // null = new book
    var impOpen by remember { mutableStateOf(false) }                       // JSON import dialog
    var counting by remember { mutableStateOf("") }                         // book id whose "পেইজ গণনা" is running
    var allCounting by remember { mutableStateOf(false) }
    // bulk image-creation dialog state (React runOpen / runList / runSplit / runErr)
    var runOpen by remember { mutableStateOf(false) }
    var runList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var runSplit by remember { mutableStateOf(false) }
    var runErr by remember { mutableStateOf("") }

    fun toast(m: String, bad: Boolean = false) = AppToast.show(m, bad)

    // Opening the tab loads the proofs (cached for 20 s) for the progress bars.
    LaunchedEffect(openKey) { if (openKey > 0) { vm.loadProofs(); vm.poller.pollNow() } }

    // React `visibilitychange`: polling only while the app is in the foreground; coming back polls at once.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        vm.poller.visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        var ready = false   // addObserver replays the current state once: ignore that replay
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) { vm.poller.visible = true; if (ready) vm.poller.pollNow() }
            else if (e == Lifecycle.Event.ON_STOP) vm.poller.visible = false
        }
        lifecycle.addObserver(obs); ready = true
        onDispose { lifecycle.removeObserver(obs) }
    }

    fun reload() { AppScope.scope.launch { vm.reloadBooks() } }
    fun exitSel() { selMode = false; sel = emptySet() }

    /** React `openRunDlg(list)`: opens the bulk image-creation dialog for [list]. */
    fun openRunDlg(list: List<JSONObject>) {
        if (list.isEmpty()) { toast("কোনো কিতাব সিলেক্ট করা হয়নি", true); return }
        runList = list.toList(); runSplit = false; runErr = ""; runOpen = true
    }

    // ---- search + filter + sort ----
    val books = vm.books
    fun st(b: JSONObject): BkView {
        val x = vm.bst[b.idStr()] ?: BookStat()
        val tot = b.finiteNum("total_pages") ?: 0.0
        val hidden = b.optBoolean("hidden", false)
        val r = RunStore.stateOf(b.idStr())
        return BkView(x, tot, hidden, notShown = vm.proofsLoaded && !hidden && tot == 0.0 && x.n == 0, running = r != null && isActiveRun(r),
            done = r != null && r.status == "completed" && r.conclusion == "success",
            failed = r != null && r.status == "completed" && r.conclusion != "success")
    }
    val qn = f.query.trim().lowercase()
    val searched = books.filter { qn.isEmpty() || it.str("name").orEmpty().lowercase().contains(qn) || it.str("author").orEmpty().lowercase().contains(qn) }
    val fcount = FLT.associate { (k, _) -> k to searched.count { matchF(k, st(it)) } }
    val idx = books.withIndex().associate { it.value.idStr() to it.index }
    // byNew: newest first when both have created_at, else the order they came in
    val byNew = Comparator<JSONObject> { a, b ->
        val ca = a.str("created_at"); val cb = b.str("created_at")
        if (ca != null && cb != null) cb.compareTo(ca) else (idx[a.idStr()] ?: 0) - (idx[b.idStr()] ?: 0)
    }
    fun progOf(b: JSONObject): Double? { val v = st(b); return if (v.tot != 0.0) v.x.n / v.tot else null }
    fun leftOf(b: JSONObject): Double? { val v = st(b); return if (v.tot != 0.0) maxOf(0.0, v.tot - v.x.n) else null }
    fun numSort(fn: (JSONObject) -> Double?, asc: Boolean) = Comparator<JSONObject> { a, b ->
        val A = fn(a); val B = fn(b)
        when {
            A == null && B == null -> byNew.compare(a, b)
            A == null -> 1
            B == null -> -1
            A == B -> byNew.compare(a, b)
            else -> if (asc) A.compareTo(B) else B.compareTo(A)
        }
    }
    val collator = remember { Collator.getInstance(Locale.forLanguageTag("bn")) }
    val cmp: Comparator<JSONObject> = when (f.sort) {
        "old" -> Comparator { a, b -> byNew.compare(b, a) }
        "name" -> Comparator { a, b -> collator.compare(a.str("name").orEmpty(), b.str("name").orEmpty()) }
        "prog_desc" -> numSort(::progOf, false)
        "prog_asc" -> numSort(::progOf, true)
        "left_desc" -> numSort(::leftOf, false)
        "left_asc" -> numSort(::leftOf, true)
        else -> byNew
    }
    val shown = searched.filter { matchF(f.flt, st(it)) }.sortedWith(cmp)
    val selList = shown.filter { it.idStr() in sel }
    val selCount = selList.size

    // ---- single-book actions ----
    fun toggleHide(bk: JSONObject) {
        val hide = !bk.optBoolean("hidden", false)
        val name = bk.str("name").orEmpty()
        AppScope.scope.launch {
            try {
                Sb.patch("books?id=eq.${enc(bk.idStr())}", jsonOf("hidden" to hide))
                vm.patchBook(bk.idStr(), jsonOf("hidden" to hide))
                toast(if (hide) "\"$name\" লুকানো হয়েছে — ইউজাররা আর দেখবে না" else "\"$name\" আবার দেখানো হচ্ছে")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                toast(if (Regex("hidden", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) HIDDEN_SQL else e.message.orEmpty(), true)
            }
        }
    }

    fun deleteBook(bk: JSONObject) {
        AppScope.scope.launch {
            if (!ask.confirm("\"${bk.str("name").orEmpty()}\" কিতাবটি ডিলিট করবেন?")) return@launch
            try {
                Sb.del("books?id=eq.${enc(bk.idStr())}")
                toast("কিতাব ডিলিট হয়েছে"); reload()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e.message.orEmpty(), true) }
        }
    }

    /** Card button: counts the GitHub folder and saves total_pages (also when it is 0, like React). */
    fun countOne(bk: JSONObject) {
        counting = bk.idStr()
        AppScope.scope.launch {
            try {
                val c = countPages(vm.S, bk)
                Sb.patch("books?id=eq.${enc(bk.idStr())}", jsonOf("total_pages" to c.n))
                vm.patchBook(bk.idStr(), jsonOf("total_pages" to c.n))
                toast("মোট পেইজ: ${Fmt.bn(c.n)}" + (if (c.truncated) " (তালিকা অসম্পূর্ণ হতে পারে)" else ""))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { toast("গণনা ব্যর্থ: ${e.message}", true) }
            finally { counting = "" }
        }
    }

    /** Card button "ছবি তৈরি" (React act('run')): confirm, then start the workflow with the saved split choice. */
    fun runOne(bk: JSONObject) {
        AppScope.scope.launch {
            val sp = getSplitPref()
            val msg = "\"${bk.str("name").orEmpty()}\" — পিডিএফ থেকে আবার ছবি তৈরি করবেন?\n\nআউটপুট ফোল্ডার: ${outDirOf(vm.S, bk)}\nবাম-ডান ভাগ: ${if (sp) "হ্যাঁ" else "না"}\n(একই নামের ছবি নতুন করে লেখা হবে; পুরনো ছবি মুছবে না)"
            if (!ask.confirm(msg)) return@launch
            vm.startRun(bk, sp)
        }
    }

    /** Header "সব গণনা": every book without a total page count. */
    fun countAll() {
        AppScope.scope.launch {
            val list = vm.books.filter { (it.finiteNum("total_pages") ?: 0.0) <= 0.0 }
            if (list.isEmpty()) { toast("সব কিতাবেই মোট পেইজ দেওয়া আছে"); return@launch }
            if (!ask.confirm("${list.size}টি কিতাবের মোট পেইজ GitHub ফোল্ডার থেকে গোনা হবে। চালাবেন?")) return@launch
            allCounting = true
            var ok = 0
            try {
                for (i in list.indices) {
                    toast("পেইজ গণনা ${Fmt.bn(i + 1)}/${Fmt.bn(list.size)} — ${list[i].str("name").orEmpty()}")
                    if (autoCount(vm, list[i], true) != null) ok++
                }
            } finally { allCounting = false }
            toast("গণনা শেষ — ${Fmt.bn(ok)}টি কিতাবের পেইজ সংখ্যা সংরক্ষিত, ${Fmt.bn(list.size - ok)}টিতে ছবি পাওয়া যায়নি")
        }
    }

    // ---- bulk actions ----
    fun delSelected() {
        val list = selList.toList()
        AppScope.scope.launch {
            if (list.isEmpty()) { toast("কোনো কিতাব সিলেক্ট করা হয়নি", true); return@launch }
            val names = list.take(5).joinToString("\n") { "• " + it.str("name").orEmpty() } +
                (if (list.size > 5) "\n… ও আরও ${Fmt.bn(list.size - 5)}টি" else "")
            if (!ask.confirm("${Fmt.bn(list.size)}টি কিতাব ডিলিট করবেন?\n\n$names\n\n(এটি ফেরানো যাবে না)")) return@launch
            bulkBusy = true
            var ok = 0; var fail = 0; var lastErr = ""
            for (i in list.indices) {
                toast("ডিলিট ${Fmt.bn(i + 1)}/${Fmt.bn(list.size)} — ${list[i].str("name").orEmpty()}")
                try { Sb.del("books?id=eq.${enc(list[i].idStr())}"); ok++ }
                catch (e: CancellationException) { throw e } catch (e: Exception) { fail++; lastErr = e.message.orEmpty() }
            }
            bulkBusy = false; exitSel()
            toast("${Fmt.bn(ok)}টি কিতাব ডিলিট হয়েছে" + (if (fail > 0) " • ${Fmt.bn(fail)}টি ব্যর্থ: $lastErr" else ""), fail > 0)
            reload()
        }
    }

    fun bulkHide(hide: Boolean) {
        val all = selList.toList()
        val list = all.filter { it.optBoolean("hidden", false) != hide }
        AppScope.scope.launch {
            if (all.isEmpty()) { toast("কোনো কিতাব সিলেক্ট করা হয়নি", true); return@launch }
            if (list.isEmpty()) { toast(if (hide) "সিলেক্ট করা সবই আগে থেকে লুকানো" else "সিলেক্ট করা সবই আগে থেকে দেখানো"); return@launch }
            bulkBusy = true
            var ok = 0; var errMsg = ""
            for (i in list.indices) {
                toast((if (hide) "লুকানো হচ্ছে " else "দেখানো হচ্ছে ") + "${Fmt.bn(i + 1)}/${Fmt.bn(list.size)} — ${list[i].str("name").orEmpty()}")
                try {
                    Sb.patch("books?id=eq.${enc(list[i].idStr())}", jsonOf("hidden" to hide))
                    vm.patchBook(list[i].idStr(), jsonOf("hidden" to hide)); ok++
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    errMsg = if (Regex("hidden", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) HIDDEN_SQL else e.message.orEmpty()
                    break
                }
            }
            bulkBusy = false
            toast("${Fmt.bn(ok)}টি কিতাব " + (if (hide) "লুকানো হয়েছে" else "আবার দেখানো হচ্ছে") + (if (errMsg.isNotEmpty()) " • ব্যর্থ: $errMsg" else ""), errMsg.isNotEmpty())
            if (errMsg.isEmpty()) exitSel()
        }
    }

    fun bulkCount() {
        val list = selList.toList()
        AppScope.scope.launch {
            if (list.isEmpty()) { toast("কোনো কিতাব সিলেক্ট করা হয়নি", true); return@launch }
            if (!ask.confirm("${Fmt.bn(list.size)}টি কিতাবের মোট পেইজ GitHub ফোল্ডার থেকে গোনা হবে। চালাবেন?")) return@launch
            bulkBusy = true
            var ok = 0
            try {
                for (i in list.indices) {
                    toast("পেইজ গণনা ${Fmt.bn(i + 1)}/${Fmt.bn(list.size)} — ${list[i].str("name").orEmpty()}")
                    if (autoCount(vm, list[i], true) != null) ok++
                }
            } finally { bulkBusy = false }
            exitSel()
            toast("গণনা শেষ — ${Fmt.bn(ok)}টি কিতাবের পেইজ সংখ্যা সংরক্ষিত, ${Fmt.bn(list.size - ok)}টিতে ছবি পাওয়া যায়নি")
        }
    }

    /** React `doBulkRun`: starts the image-creation workflow one book after another. */
    fun doBulkRun() {
        val list = runList.filter { !it.str("pdf_url").isNullOrEmpty() }
        if (list.isEmpty()) { runErr = "সিলেক্ট করা কিতাবগুলোর কোনোটিতেই পিডিএফ লিংক নেই"; return }
        if (ghTok(vm.S).isEmpty()) { runErr = "workflow চালাতে সেটিংস ট্যাবে GitHub Token দিন"; return }
        runErr = ""; bulkBusy = true; setSplitPref(runSplit)
        val split = runSplit
        AppScope.scope.launch {
            var ok = 0
            try {
                for (i in list.indices) {
                    toast("workflow চালু ${Fmt.bn(i + 1)}/${Fmt.bn(list.size)} — ${list[i].str("name").orEmpty()}")
                    if (vm.startRun(list[i], split, true)) ok++
                }
            } finally { bulkBusy = false }
            runOpen = false; exitSel()
            toast("${Fmt.bn(ok)}টি কিতাবের ছবি তৈরির workflow চালু হয়েছে — স্ট্যাটাস কার্ডে দেখা যাবে" + (if (ok < list.size) " • ${Fmt.bn(list.size - ok)}টি চালু হয়নি" else ""), ok < list.size)
        }
    }

    fun openBulkRate() {
        if (selList.isEmpty()) { toast("কোনো কিতাব সিলেক্ট করা হয়নি", true); return }
        rateList = selList.toList(); rateVal = ""; rateErr = ""; rateOpen = true
    }

    fun doBulkRate() {
        val rv = rateVal.trim()
        val num = rv.toDoubleOrNull()
        if (rv.isNotEmpty() && !(num != null && num >= 0)) { rateErr = "সঠিক রেট দিন"; return }
        val v: Double? = if (rv.isEmpty()) null else num
        rateErr = ""; bulkBusy = true
        val list = rateList
        AppScope.scope.launch {
            var ok = 0; var errMsg = ""
            for (b in list) {
                try {
                    Sb.patch("books?id=eq.${enc(b.idStr())}", jsonOf("page_rate" to v))
                    vm.patchBook(b.idStr(), jsonOf("page_rate" to v))
                    val old = if (b.isNull("page_rate") || !b.has("page_rate")) "" else numStr(b.opt("page_rate"))
                    if (old != (if (v == null) "" else numStr(v))) {
                        ActivityLog.log("admin_rate", b.str("name").orEmpty() + " → " + (if (v == null) "ডিফল্ট" else numStr(v) + " ৳"))
                    }
                    ok++
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    errMsg = if (Regex("page_rate", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) "রেট কলাম নেই — $SQLHINT" else e.message.orEmpty()
                    break
                }
            }
            bulkBusy = false
            if (errMsg.isNotEmpty()) { rateErr = "${Fmt.bn(ok)}টি বদলেছে, তারপর ব্যর্থ: $errMsg"; return@launch }
            rateOpen = false; exitSel()
            toast("${Fmt.bn(ok)}টি কিতাবের রেট " + (if (v == null) "ডিফল্ট করা হয়েছে" else "${rateTxt(v)} করা হয়েছে"))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // header row
        Text("সব কিতাব", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmBtn("", "refresh") { AppScope.scope.launch { vm.reloadBooks(); toast("রিফ্রেশ হয়েছে") } }
            SmBtn("সব গণনা", "refresh", enabled = !allCounting) { countAll() }
            SmBtn("JSON ইমপোর্ট", "plus") { impOpen = true }
            SmBtn(if (selMode) "সিলেক্ট বন্ধ" else "সিলেক্ট", "check", tone = if (selMode) "primary" else "", enabled = !bulkBusy) {
                if (selMode) exitSel() else selMode = true
            }
            SmBtn("নতুন কিতাব", "plus", tone = "primary") { editBook = null; bookDlg = true }
        }
        // search + sort
        Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(value = f.query, onValueChange = { vm.updateBooksFilter(f.copy(query = it)) }, placeholder = "কিতাব বা লেখকের নাম খুঁজুন")
            AdminSelect(f.sort, SORTS, onPick = { vm.updateBooksFilter(f.copy(sort = it)) })
        }
        // filter chips (label + count)
        Column(Modifier.padding(bottom = 12.dp)) {
            Chips(items = FLT.map { (k, l) -> k to "$l (${Fmt.bn(fcount[k] ?: 0)})" }, value = f.flt, onPick = { vm.updateBooksFilter(f.copy(flt = it)) })
        }
        // "retry all failed": the books now shown under the failed filter that have a PDF link
        if (f.flt == "failed" && !selMode && (fcount["failed"] ?: 0) > 0) {
            Box(Modifier.padding(bottom = 10.dp)) {
                SmBtn("সব ব্যর্থ আবার চেষ্টা", "play", tone = "primary") { openRunDlg(shown.filter { !it.str("pdf_url").isNullOrEmpty() }) }
            }
        }
        HintText("${Fmt.bn(shown.size)}টি কিতাব দেখানো হচ্ছে (মোট ${Fmt.bn(books.size)}টি)", Modifier.padding(bottom = 10.dp))

        if (selMode) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildAnnotatedString {
                        append("সিলেক্ট করা: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) { append(Fmt.bn(selCount)) }
                        append("/${Fmt.bn(shown.size)}")
                    },
                    color = cs.onSurfaceVariant, fontSize = 12.5.sp
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmBtn(if (selCount > 0 && selCount == shown.size) "সব বাদ" else "সব সিলেক্ট", "check", enabled = !bulkBusy && shown.isNotEmpty()) {
                        val allSel = shown.isNotEmpty() && shown.all { it.idStr() in sel }
                        sel = if (allSel) sel - shown.map { it.idStr() }.toSet() else sel + shown.map { it.idStr() }
                    }
                    SmBtn("লুকান", "ban", enabled = !bulkBusy && selCount > 0) { bulkHide(true) }
                    SmBtn("দেখান", "check", enabled = !bulkBusy && selCount > 0) { bulkHide(false) }
                    SmBtn("রেট", "edit", enabled = !bulkBusy && selCount > 0) { openBulkRate() }
                    SmBtn("পেইজ গণনা", "refresh", enabled = !bulkBusy && selCount > 0) { bulkCount() }
                    SmBtn("ছবি তৈরি", "play", enabled = !bulkBusy && selCount > 0) { openRunDlg(selList) }
                    SmBtn("ডিলিট (${Fmt.bn(selCount)})", "trash", tone = "bad", enabled = !bulkBusy && selCount > 0) { delSelected() }
                }
            }
        }

        if (books.isEmpty()) {
            Empty("কোনো কিতাব যোগ করা হয়নি")
        } else if (shown.isEmpty()) {
            Empty("কোনো কিতাব মেলেনি — সার্চ বা ফিল্টার বদলান")
        } else {
            shown.forEach { b ->
                val id = b.idStr()
                androidx.compose.runtime.key(id) {
                    val v = st(b)
                    val x = v.x
                    val tot = v.tot
                    val picked = id in sel
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.alpha(if (v.hidden) 0.6f else 1f)
                            .then(if (selMode && picked) Modifier.clip(shape).border(2.dp, cs.primary, shape) else Modifier)
                            .then(if (selMode) Modifier.clickable { sel = if (picked) sel - id else sel + id } else Modifier)
                    ) {
                        LiCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selMode) Checkbox(checked = picked, onCheckedChange = { sel = if (picked) sel - id else sel + id })
                                Box(
                                    Modifier.size(width = 44.dp, height = 58.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val cover = b.str("cover_url").orEmpty()
                                    if (cover.isNotEmpty()) AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else AppIcon("book", size = 20.dp, tint = cs.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(b.str("name").orEmpty(), color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                        if (v.hidden) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            AppIcon("ban", size = 13.dp, tint = cs.onSurfaceVariant)
                                            Text("লুকানো", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        if (v.notShown) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            AppIcon("clock", size = 13.dp, tint = cs.onSurfaceVariant)
                                            Text("Task পেইজে দেখাচ্ছে না (ছবি তৈরি ও পেইজ গণনা বাকি)", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    val noGh = b.str("github_username").isNullOrEmpty() && b.str("repo_name").isNullOrEmpty()
                                    val folder = b.str("folder")?.takeIf { it.isNotEmpty() } ?: b.str("name").orEmpty()
                                    val pagesDir = vm.S.str("pages_dir")?.takeIf { it.isNotEmpty() } ?: DEF.getValue("pages_dir")
                                    Text(
                                        (b.str("author").orEmpty()) + "\n" + eff(vm.S, b, "github_username") + "/" + eff(vm.S, b, "repo_name") +
                                            (if (noGh) " (ডিফল্ট)" else "") + " · " + folder + "_" + pagesDir,
                                        color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp
                                    )
                                    val pdf = b.str("pdf_url").orEmpty()
                                    if (pdf.isNotEmpty()) {
                                        Text(
                                            "PDF লিংক", color = cs.primary, fontSize = 12.5.sp, textDecoration = TextDecoration.Underline,
                                            modifier = Modifier.clickable { openUrl(ctx, pdf) }
                                        )
                                    }
                                }
                            }
                            val noRate = !b.has("page_rate") || b.isNull("page_rate") || b.str("page_rate") == ""
                            Text(
                                buildAnnotatedString {
                                    append("রেট: ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) { append(rateTxt(Earnings.rateOf(b, vm.S))) }
                                    append("/পেইজ" + (if (noRate) " (ডিফল্ট)" else "") + " • ")
                                    append(if (tot != 0.0) "মোট ${Fmt.bn(tot)} পেইজ" else "মোট পেইজ দেওয়া নেই (এডিট বা \"পেইজ গণনা\")")
                                },
                                color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp
                            )
                            if (vm.proofsLoaded) {
                                SegBar(x.a, x.p, x.c, (if (tot != 0.0) tot.toInt() else x.n))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "প্রুফড ${Fmt.bn(x.n)}" + (if (tot != 0.0) " / ${Fmt.bn(tot)} • বাকি ${Fmt.bn(maxOf(0.0, tot - x.n))}" else ""),
                                        color = cs.onSurfaceVariant, fontSize = 11.5.sp, modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "অনুমোদিত ${Fmt.bn(x.a)} • রিভিউ বাকি ${Fmt.bn(x.p)} • সংশোধন ${Fmt.bn(x.c)}",
                                        color = cs.onSurfaceVariant, fontSize = 11.5.sp, modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (RunStore.getRun(b) != null) RunBox(RunStore.stateOf(id))
                            if (!selMode) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SmBtn("পেইজ গণনা", "refresh", enabled = counting != id) { countOne(b) }
                                    SmBtn(if (v.hidden) "দেখান" else "লুকান", if (v.hidden) "check" else "ban") { toggleHide(b) }
                                    SmBtn("এডিট", "edit") { editBook = b; bookDlg = true }
                                    // retry opens the bulk run dialog in React (2-ঝ-২-চ); until then it asks the same question as "ছবি তৈরি"
                                    if (v.failed && b.str("pdf_url").orEmpty().isNotEmpty()) SmBtn("আবার চেষ্টা", "play", tone = "primary") { runOne(b) }
                                    if (b.str("pdf_url").orEmpty().isNotEmpty()) SmBtn("ছবি তৈরি", "play") { runOne(b) }
                                    SmBtn("ডিলিট", "trash", tone = "bad") { deleteBook(b) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // new / edit book dialog and JSON import (BookDialogs.kt)
    if (bookDlg) androidx.compose.runtime.key(editBook?.idStr() ?: "new") { BookDialog(vm, editBook, onClose = { bookDlg = false }) }
    if (impOpen) ImportDialog(vm, onClose = { impOpen = false })

    // bulk image-creation / retry dialog (React bRunDlg)
    if (runOpen) {
        val withPdf = runList.count { !it.str("pdf_url").isNullOrEmpty() }
        val noPdf = runList.size - withPdf
        AlertDialog(
            onDismissRequest = { if (!bulkBusy) runOpen = false },
            title = { Text("ছবি তৈরির workflow চালান", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText(
                        "${Fmt.bn(withPdf)}টি কিতাবের জন্য আলাদা workflow চালু হবে" +
                            (if (noPdf > 0) " (পিডিএফ লিংক নেই এমন ${Fmt.bn(noPdf)}টি বাদ যাবে)" else "") +
                            "। একই নামের ছবি নতুন করে লেখা হবে; পুরনো ছবি মুছবে না।\n" +
                            runList.take(5).joinToString("\n") { "• " + it.str("name").orEmpty() } +
                            (if (runList.size > 5) "\n… ও আরও ${Fmt.bn(runList.size - 5)}টি" else "")
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !bulkBusy) { runSplit = !runSplit },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = runSplit, onCheckedChange = { runSplit = it }, enabled = !bulkBusy)
                        Text("প্রতিটি পেইজ বাম-ডান দুই ভাগ (split) করুন", color = cs.onSurface, fontSize = 13.sp)
                    }
                    if (runErr.isNotEmpty()) ErrText(runErr)
                }
            },
            confirmButton = { TextButton(onClick = { doBulkRun() }, enabled = !bulkBusy) { Text("চালু করুন") } },
            dismissButton = { TextButton(onClick = { runOpen = false }, enabled = !bulkBusy) { Text("বন্ধ") } }
        )
    }

    // bulk rate dialog (React bRateDlg)
    if (rateOpen) {
        AlertDialog(
            onDismissRequest = { if (!bulkBusy) rateOpen = false },
            title = { Text("রেট বদলান", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText("${Fmt.bn(rateList.size)}টি কিতাবের পেইজ প্রতি রেট একসাথে বদলাবে।")
                    Text("পেইজ প্রতি রেট (৳) — ফাঁকা = ডিফল্ট রেট", color = cs.onSurfaceVariant, fontSize = 13.sp)
                    AppField(
                        value = rateVal, onValueChange = { rateVal = it },
                        placeholder = "ডিফল্ট " + numStr(Earnings.rateOf(null, vm.S)), keyboardType = KeyboardType.Decimal,
                        onAction = { if (!bulkBusy) doBulkRate() }
                    )
                    if (rateErr.isNotEmpty()) ErrText(rateErr)
                }
            },
            confirmButton = { TextButton(onClick = { doBulkRate() }, enabled = !bulkBusy) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { rateOpen = false }, enabled = !bulkBusy) { Text("বন্ধ") } }
        )
    }
}

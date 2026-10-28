package com.srizwan.task.screens.task

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.Auth
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.Net
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Summary
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import com.srizwan.task.nav.Nav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * React Task.jsx — 2-ট: the EMPTY structure of the Task page. No data, no logic yet.
 *
 * Mirrors the JSX skeleton:
 *   header.top            -> [TaskHeader]  (TaskNav.kt, 3-ক-১: logo, title, count chip, theme toggle, profile / admin / logout)
 *   main.wrap.wide        -> scrollable content, max width, bottom padding 90 (room for the bottom bar)
 * Data: [TaskData] (TaskData.kt, 3-ক-২: books, counts, my proofs / payments / settings, loaders).
 *     section#pTask       -> [TaskPane]   (3-খ: dashboard, search, book grid)
 *     PayPane             -> [PayPane]    (TaskPay.kt, 3-গ: tiles, forecast, request, history, per-book earnings, corrections)
 *     RankPane            -> [RankPane]   (TaskRank.kt, 3-ঘ: today / week / all-time leaderboard, my place)
 *   nav.bottom            -> [TaskBottomNav] (TaskNav.kt, 3-ক-১: task / payment / rank; `switchTab` is here)
 *   dialogs               -> goal, request, notice, guide, hidden-book and "in use" dialogs (3-খ … 3-ঙ)
 *
 * It already runs the React guard (requireAuth: session, device, blocked) so login / signup / logout can be tested.
 * The temporary logout button is gone (3-চ-১): users log out from the Profile page, the admin also from the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen() {
    val data = remember { TaskData() }
    val me = data.me
    val scope = rememberCoroutineScope()

    // React `tab`: task | pay | rank.
    var tab by remember { mutableStateOf("task") }
    val scroll = rememberScrollState()
    val pullState = rememberPullToRefreshState()
    val isAdmin = me?.let { Phone.isAdminPhone(it.phone) } == true
    // badge = unseen notices (3-খ-৩; the profile list in 3-ছ-১)
    val badge = data.badge

    // React `resume`: read again whenever the screen is shown (the View screen writes it every time the page changes).
    var resume by remember { mutableStateOf<ResumeRec?>(null) }
    fun reloadResume() { resume = data.me?.let { getResume(it.id) } }

    // React `glDlg` / `glVal` / `glErr`: daily goal dialog.
    var goalOpen by remember { mutableStateOf(false) }
    var glVal by remember { mutableStateOf("") }
    var glErr by remember { mutableStateOf("") }

    /** React `openGoal`. */
    fun openGoal() { glVal = data.goal.toString(); glErr = ""; goalOpen = true }

    /** React `saveGoal`: 1..1000 whole pages, saved on this device. */
    fun saveGoal() {
        val u = data.me ?: return
        val g = Math.floor(glVal.trim().toDoubleOrNull() ?: 0.0)
        if (!g.isFinite() || g < 1 || g > 1000) { glErr = "১ থেকে ১০০০-এর মধ্যে একটি সংখ্যা দিন"; return }
        data.goal = g.toInt()
        writeGoal(u.id, g.toInt())
        goalOpen = false
        AppToast.show("দৈনিক লক্ষ্য: ${Fmt.bn(g.toInt())} পেইজ")
    }

    // React `query`, `lockBusy`, `lockInfo` / `hiddenInfo` (+ their dialogs).
    var query by remember { mutableStateOf("") }
    var lockBusy by remember { mutableStateOf(false) }
    var lockInfo by remember { mutableStateOf<Pair<String, String>?>(null) }   // (holder name, book name)
    var hiddenInfo by remember { mutableStateOf<String?>(null) }               // book name

    /**
     * React `openBook`: before opening, stop a hidden book and check that nobody else is working on it right now
     * (`rpc/book_lock_of`; the admin may always open). A missing function / network problem never blocks — it opens.
     */
    fun openBook(id: String, stem: String = "") {
        if (lockBusy) return
        lockBusy = true
        scope.launch {
            try {
                val bk = data.booksById[id]
                if (bk != null && bk.optBoolean("hidden", false) && !isAdmin) {
                    hiddenInfo = bk.str("name") ?: ""
                    return@launch
                }
                val u = data.me
                if (!isAdmin && u != null) {
                    try {
                        val r = Sb.post("rpc/book_lock_of", JSONObject().put("p_book", id))
                        val l = r.optJSONObject(0)
                        if (l != null && (l.opt("holder_id")?.toString() ?: "") != u.id) {
                            lockInfo = (l.str("holder_name")?.takeIf { it.isNotEmpty() } ?: "অন্য একজন ইউজার") to (bk?.str("name") ?: "")
                            return@launch
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { /* function / table missing or no network: do not block */ }
                }
                Nav.go("view?book=${enc(id)}" + if (stem.isNotEmpty()) "&page=${enc(stem)}" else "")
            } finally { lockBusy = false }
        }
    }

    /** React `openResume`. */
    fun openResume() {
        val u = data.me ?: return
        val rt = resumeTarget(getResume(u.id), data.booksById, data.fin.proofs) ?: return
        openBook(rt.bid, rt.stem)
    }

    // React `reqBusy`, `rq`, `rqAmt`, `rqErr`, `rqSending`: payment request (button, dialog, sending).
    var reqBusy by remember { mutableStateOf(false) }
    var rq by remember { mutableStateOf<RequestInfo?>(null) }
    var rqAmt by remember { mutableStateOf("") }
    var rqErr by remember { mutableStateOf("") }
    var rqSending by remember { mutableStateOf(false) }

    // React `guide` ref: the first-time guide dialog (3-ঙ-২). Seen -> remembered per user; closing loads the notices.
    var guideOpen by remember { mutableStateOf(false) }
    var exitOpen by remember { mutableStateOf(false) }   // custom "exit the app?" dialog (back press)
    data.guideOpen = { guideOpen }

    // React `rankP` / `rank` / `rankCache` (3-ঘ): the leaderboard state.
    val rank = remember { RankState(data) }

    /** React `summaryOf`: my earnings summary from a freshly loaded [Fin] (null until the first money load). */
    fun summaryOf(f: Fin): Summary? = if (f.ready) Earnings.summarize(f.proofs, data.booksById, f.S, f.pays) else null

    /** JS `String(number)`: 150.0 -> "150", 150.5 -> "150.5". */
    fun numStr(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /**
     * React `onRequest`: reload the money data, check there is a payment number and enough to withdraw, then open the
     * request dialog pre-filled with everything available.
     */
    fun onRequest() {
        if (reqBusy) return
        reqBusy = true
        scope.launch {
            try {
                val f = data.loadMoney()
                val s = summaryOf(f)
                val min = minPayout(f.S)
                val u = data.me ?: return@launch
                if (s == null) { AppToast.show("তথ্য আনা যায়নি, আবার চেষ্টা করুন", true); return@launch }
                if (u.paymentNumber.isNullOrEmpty()) { AppToast.show("আগে পেমেন্ট নাম্বার দিন", true); return@launch }
                if (s.available < min || s.available <= 0) { AppToast.show("ন্যূনতম ${Fmt.money(min)} হলে রিকোয়েস্ট করা যাবে", true); return@launch }
                rq = RequestInfo(
                    avail = Fmt.money(s.available), min = Fmt.money(min),
                    pmLabel = (PM[u.paymentMethod] ?: "পেমেন্ট") + " নাম্বার", number = u.paymentNumber ?: "", max = s.available
                )
                rqAmt = numStr(s.available); rqErr = ""
            } catch (e: CancellationException) { throw e } finally { reqBusy = false }
        }
    }

    /**
     * React `onSend`: reload again (the numbers may have changed), check the amount (> 0, >= minimum, <= available) and
     * insert the `payments` row (status `requested`; table columns: user_id, user_name, amount, pages, method, number, status).
     */
    fun onSend() {
        if (rqSending) return
        rqSending = true
        scope.launch {
            try {
                val f = data.loadMoney()
                val s = summaryOf(f)
                val min = minPayout(f.S)
                val amt = Fmt.r2(rqAmt.toDoubleOrNull())
                val u = data.me ?: return@launch
                if (s == null) throw IllegalStateException("তথ্য আনা যায়নি, আবার চেষ্টা করুন")
                if (!amt.isFinite() || amt <= 0) throw IllegalStateException("সঠিক পরিমাণ দিন")
                if (amt < min) throw IllegalStateException("ন্যূনতম ${Fmt.money(min)} চাইতে হবে")
                if (amt > s.available) throw IllegalStateException("উত্তোলনযোগ্য ${Fmt.money(s.available)}-এর বেশি চাওয়া যাবে না")
                Sb.post(
                    "payments",
                    jsonOf(
                        "user_id" to u.id, "user_name" to u.fullName, "amount" to amt, "pages" to s.approved,
                        "method" to u.paymentMethod, "number" to u.paymentNumber, "status" to "requested"
                    )
                )
                ActivityLog.log("payment_request", "${Fmt.money(amt)} • ${PM[u.paymentMethod] ?: ""} ${u.paymentNumber ?: ""}")
                rq = null
                AppToast.show("পেমেন্ট রিকোয়েস্ট পাঠানো হয়েছে")
                scope.launch { data.loadMoney() }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val m = e.message ?: ""
                rqErr = if (Regex("payments", RegexOption.IGNORE_CASE).containsMatchIn(m) &&
                    Regex("404|PGRST205|42P01", RegexOption.IGNORE_CASE).containsMatchIn(m)
                ) "পেমেন্ট টেবিল নেই — অ্যাডমিন rizwantask_full_schema.sql চালাননি" else m
            } finally { rqSending = false }
        }
    }

    /** React `switchTab`: change the tab, run that tab's loader, scroll to the top. */
    fun switchTab(t: String) {
        tab = t
        when (t) {
            "pay" -> scope.launch { data.loadMoney() }
            "rank" -> scope.launch { rank.load(rank.period) }
            else -> scope.launch { data.loadCounts() }
        }
        scope.launch { scroll.scrollTo(0) }
    }

    // Pull-to-refresh, the same on every tab: pull down at the top of the page. Each tab reloads its own data
    // (task: books + counts + notices, pay: money data + count, rank: the leaderboard without its 60 s cache).
    var pulling by remember { mutableStateOf(false) }
    fun pullRefresh() {
        if (pulling || data.me == null) return
        pulling = true
        val t = tab
        val t0 = System.currentTimeMillis()
        scope.launch {
            try {
                coroutineScope {
                    when (t) {
                        "pay" -> { launch { data.loadCount() }; launch { data.loadMoney() } }
                        "rank" -> launch { rank.load(rank.period, force = true) }
                        else -> { launch { data.load() }; launch { data.loadCount() }; launch { data.loadNotices() } }
                    }
                }
                // a loader that returns at once (already running) must not make the indicator flash
                val left = 600L - (System.currentTimeMillis() - t0)
                if (left > 0) delay(left)
            } finally { pulling = false }
        }
    }

    /** React `logout`: log the action, then sign out after a short delay. */
    fun logout() {
        scope.launch { ActivityLog.log("logout", ""); delay(150); Auth.logout() }
    }

    // React init: login check, then the first load; a #pay / #rank hash opens that tab at once.
    // `hash` is kept for 3-ঙ-২: "guide" opens the guide dialog.
    var hash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val u = Auth.requireAuth() ?: return@LaunchedEffect
        data.goal = readGoal(u.id)
        data.onMoneyLoaded = { proofs -> goalToast(proofs, data.goal, u.id) }
        data.me = u
        reloadResume()
        hash = Nav.consumeHash()
        scope.launch { data.loadCount() }
        scope.launch { data.load() }
        // React: a "#guide" link (profile's guide button) opens the guide after 200 ms; a user who has not seen it yet gets
        // it after 500 ms (the notices load when it is closed); otherwise the notices load at once.
        val seenGuide = Prefs.getString(guideKey(u.id)) == "1"
        if (hash == "guide") scope.launch { delay(200); guideOpen = true }
        else if (!seenGuide) scope.launch { delay(500); guideOpen = true }
        else scope.launch { data.loadNotices() }
        when (hash) {
            "prof" -> Nav.go("profile", replace = true)   // old link: the profile is a separate page now
            "pay" -> switchTab("pay")
            "rank" -> switchTab("rank")
        }
    }

    // React visibilitychange / pageshow: coming back to the app refreshes everything (the first ON_START is skipped).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, me) {
        var ready = false   // addObserver replays the current state once: ignore that replay
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) reloadResume()
            if (e == Lifecycle.Event.ON_START && ready && data.me != null) scope.launch { data.refreshAll() }
        }
        lifecycle.addObserver(obs); ready = true
        onDispose { lifecycle.removeObserver(obs) }
    }
    // React setInterval(loadNotices, 180000): every 3 minutes while the app is on screen.
    LaunchedEffect(me, lifecycle) {
        if (me == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { delay(180000); data.loadNotices() } }
    }
    // React kp-online: the connection came back -> retryAll.
    LaunchedEffect(me) {
        if (me == null) return@LaunchedEffect
        Net.onlineEvents.collect { data.retryAll() }
    }
    // React kp-retry: the net bar's retry button reloads the data instead of the whole page.
    DisposableEffect(me) {
        val h: () -> Boolean = { if (data.me == null) false else { scope.launch { data.retryAll() }; true } }
        Net.addRetryHandler(h)
        onDispose { Net.removeRetryHandler(h) }
    }

    // Back on the Task page asks before leaving the app (dialogs are their own windows and take back first)
    BackHandler(enabled = !exitOpen) { exitOpen = true }

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TaskHeader(
                tab, data.myCount, badge, isAdmin,
                onProfile = { Nav.go("profile") }, onAdmin = { Nav.go("admin") }, onLogout = { logout() }
            )
            PullToRefreshBox(
                isRefreshing = pulling,
                onRefresh = { pullRefresh() },
                state = pullState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState, isRefreshing = pulling,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = MaterialTheme.colorScheme.surface,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Column(
                    Modifier.widthIn(max = 980.dp).fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(start = 14.dp, end = 14.dp, bottom = 90.dp)
                ) {
                    when (tab) {
                        "pay" -> PayPane(
                            data, reqBusy, onRequest = { onRequest() },
                            onOpenPage = { bid, stem -> Nav.go("view?book=${enc(bid)}&page=${enc(stem)}") }
                        )
                        "rank" -> RankPane(
                            rank, meId = me?.id ?: "",
                            onPeriod = { k -> rank.pick(k); scope.launch { rank.load(k) } },
                            pulling = pulling
                        )
                        else -> TaskPane(
                            data, resume, query, onQuery = { query = it },
                            onReload = { scope.launch { data.load() }; scope.launch { data.loadCount() }; scope.launch { data.loadNotices() } },
                            onGoal = { openGoal() }, onResume = { openResume() }, onOpen = { openBook(it) },
                            pulling = pulling
                        )
                    }
                }
            }
            if (me != null) TaskBottomNav(tab, onPick = { switchTab(it) })
        }
        // dialogs: goal (3-খ-১), hidden-book / book-in-use (3-খ-২), notice (3-খ-৩), payment request (3-গ-২), guide (3-ঙ-২)
        if (guideOpen) GuideDialog(onClosed = {
            // React `onGuideClosed`
            guideOpen = false
            data.me?.let { Prefs.putString(guideKey(it.id), "1") }
            scope.launch { data.loadNotices() }
        })
        if (data.nOpen) NoticeDialog(data.nList, data.nIdx, onIdx = { data.nIdx = it }, onClose = { data.closeNotices() })
        hiddenInfo?.let { HiddenBookDialog(it, onClose = { hiddenInfo = null }) }
        lockInfo?.let { (who, book) -> LockedBookDialog(who, book, onClose = { lockInfo = null }) }
        rq?.let {
            RequestDialog(
                it, rqAmt, onAmt = { v -> rqAmt = v }, err = rqErr, busy = rqSending,
                onCancel = { rq = null }, onSend = { onSend() }
            )
        }
        if (exitOpen) ExitDialog(onDismiss = { exitOpen = false })
        if (goalOpen) GoalDialog(
            value = glVal, onValue = { glVal = it; glErr = "" }, err = glErr,
            onCancel = { goalOpen = false }, onSave = { saveGoal() }
        )
    }
}

/**
 * section#pTask — dashboard (3-খ-১), search + refresh and the book grid (3-খ-২).
 * [resume] = where I stopped last on this device (the "continue" button and the "শেষ কাজ" tag).
 */
@Composable
private fun TaskPane(
    data: TaskData, resume: ResumeRec?, query: String, onQuery: (String) -> Unit, onReload: () -> Unit,
    onGoal: () -> Unit, onResume: () -> Unit, onOpen: (String) -> Unit, pulling: Boolean = false
) {
    TaskDashboard(
        ready = data.fin.ready, proofs = data.fin.proofs, booksById = data.booksById,
        goal = data.goal, resume = resume, onGoal = onGoal, onResume = onResume
    )
    TaskSearchRow(query, onQuery, onReload)
    // `#noticeBar` is an empty div in React; notices open as a dialog (3-খ-৩)
    BookGrid(
        status = data.booksStatus, spinning = data.spinning && !pulling, books = data.shownBooks, query = query,
        bookCounts = data.bookCounts, byBook = data.sum?.byBook ?: emptyMap(), resume = resume,
        rateOf = { data.rateOf(it) }, onOpen = onOpen
    )
}

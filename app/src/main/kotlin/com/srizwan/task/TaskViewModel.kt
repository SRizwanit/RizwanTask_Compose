package com.srizwan.task

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

// ---------------------------------------------------------------------------
// Server calls for the Task page (Supabase RPC, see supabase/compose_step1_auth_task.sql)
// ---------------------------------------------------------------------------
object TaskApi {
    private fun tokenBody(token: String) = JSONObject().put("p_token", token)

    suspend fun books(token: String) = SupabaseApi.call("task_books", tokenBody(token))
    suspend fun proofs(token: String) = SupabaseApi.call("task_proofs", tokenBody(token))
    suspend fun payments(token: String) = SupabaseApi.call("task_payments", tokenBody(token))
    suspend fun notices(token: String) = SupabaseApi.call("task_notices", tokenBody(token))

    /** [sinceMillis] = null -> all time */
    suspend fun leaderboard(token: String, sinceMillis: Long?) = SupabaseApi.call(
        "task_leaderboard",
        tokenBody(token).put(
            "p_since",
            if (sinceMillis == null) JSONObject.NULL else Instant.ofEpochMilli(sinceMillis).toString()
        )
    )

    suspend fun requestPayment(token: String, amount: Double) =
        SupabaseApi.call("task_request_payment", tokenBody(token).put("p_amount", amount))
}

// ---------------------------------------------------------------------------
// UI state types
// ---------------------------------------------------------------------------
enum class TaskTab(val title: String) {
    Task("টাস্কের তালিকা"),
    Pay("পেমেন্ট"),
    Rank("লিডারবোর্ড")
}

enum class LoadState { Loading, Ready, Error }

enum class RankPeriod(val chip: String, val label: String) {
    Day("আজ", "আজ"),
    Week("এই সপ্তাহ", "এই সপ্তাহে"),
    All("সব সময়", "সব মিলিয়ে")
}

data class RankState(
    val loading: Boolean = false,
    val period: RankPeriod? = null,          // period of [rows]
    val rows: List<RankRow> = emptyList(),
    val error: String = ""
)

/** Data shown in the payment-request dialog. */
data class RequestInfo(
    val available: Double,
    val min: Double,
    val methodLabel: String,
    val number: String
)

data class ToastMsg(val id: Long, val text: String, val bad: Boolean)

// ---------------------------------------------------------------------------
// ViewModel  (port of React pages/Task.jsx state + loaders)
// ---------------------------------------------------------------------------
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val prefs = TaskPrefs(application)

    private var me: AppUser? = null
    private var started = false

    // ---- screen state ---------------------------------------------------------
    var tab by mutableStateOf(TaskTab.Task); private set
    var query by mutableStateOf("")

    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var booksState by mutableStateOf(LoadState.Loading); private set
    var spinning by mutableStateOf(false); private set
    var bookCounts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var settings by mutableStateOf(Settings()); private set

    var proofs by mutableStateOf<List<Proof>>(emptyList()); private set
    var payments by mutableStateOf<List<Payment>>(emptyList()); private set
    var finReady by mutableStateOf(false); private set
    var summary by mutableStateOf<Summary?>(null); private set
    var booksById by mutableStateOf<Map<String, Book>>(emptyMap()); private set

    var goal by mutableIntStateOf(TaskPrefs.DEFAULT_GOAL); private set
    var resume by mutableStateOf<Pair<String, String>?>(null); private set

    var rankPeriod by mutableStateOf(RankPeriod.Day); private set
    var rank by mutableStateOf(RankState()); private set

    var badge by mutableIntStateOf(0); private set
    var noticeQueue by mutableStateOf<List<Notice>>(emptyList()); private set
    var noticeIndex by mutableIntStateOf(0)

    var guideOpen by mutableStateOf(false); private set

    var goalDialogOpen by mutableStateOf(false); private set
    var goalError by mutableStateOf(""); private set

    var requestInfo by mutableStateOf<RequestInfo?>(null); private set
    var requestBusy by mutableStateOf(false); private set        // button on the Pay tab
    var requestSending by mutableStateOf(false); private set     // "send" in the dialog
    var requestError by mutableStateOf(""); private set

    var toast by mutableStateOf<ToastMsg?>(null); private set

    /** true when the server says the token is no longer valid -> screen logs the user out */
    var sessionExpired by mutableStateOf(false); private set

    /** number of finished tasks (= my proofed pages) for the header, null until loaded */
    val myCount: Int? get() = if (finReady) proofs.size else null

    // ---- internals ---------------------------------------------------------------
    private var allNotices: List<Notice> = emptyList()
    private var booksJob: Job? = null
    private var moneyJob: Job? = null
    private var noticeJob: Job? = null
    private var rankSeq = 0
    private var lastBooksAt = 0L
    private val rankCache = HashMap<RankPeriod, Pair<Long, List<RankRow>>>()
    private var toastSeq = 0L

    // ---- start ----------------------------------------------------------------------
    /** Called by the screen; safe to call many times (the user object may change after profile edits). */
    fun bind(user: AppUser) {
        me = user
        if (started) return
        started = true
        goal = prefs.goal(user.id)
        resume = prefs.resume(user.id)
        loadBooks()
        loadMoney()
        viewModelScope.launch {
            if (!prefs.guideSeen(user.id)) {
                delay(500)
                guideOpen = true
            } else {
                loadNotices()
            }
        }
    }

    /** Back on screen / pull-to-refresh style reload. */
    fun refreshAll() {
        if (me == null) return
        resume = me?.id?.let { prefs.resume(it) }
        loadBooks()
        loadMoney()
        // before the first-time guide is closed no notice is shown (same as the web page)
        if (me?.id?.let { prefs.guideSeen(it) } == true) loadNotices()
        if (tab == TaskTab.Rank) loadRank(rankPeriod, force = true)
    }

    // ---- helpers ----------------------------------------------------------------------
    private fun token(): String? {
        val t = store.token()
        if (t == null) sessionExpired = true
        return t
    }

    fun showToast(text: String, bad: Boolean = false) {
        toast = ToastMsg(++toastSeq, text, bad)
    }

    fun clearToast(id: Long) {
        if (toast?.id == id) toast = null
    }

    private fun failText(f: CallResult.Fail): String {
        if (f.error == "invalid_session") sessionExpired = true
        return when (f.error) {
            "network" -> AuthError.NETWORK.text()
            "not_configured" -> AuthError.NOT_CONFIGURED.text()
            "setup_missing" -> AuthError.SERVER_SETUP.text()
            "invalid_session" -> AuthError.INVALID_SESSION.text()
            else -> AuthError.UNKNOWN.text()
        }
    }

    private fun recompute() {
        booksById = books.associateBy { it.id }
        summary = if (finReady) summarize(proofs, booksById, settings, payments) else null
    }

    fun rateOfBook(b: Book?): Double = rateOf(b, settings)

    // ---- books + progress bars ---------------------------------------------------------
    fun loadBooks() {
        if (booksJob?.isActive == true) return
        val t = token() ?: return
        if (books.isEmpty()) booksState = LoadState.Loading else spinning = true
        booksJob = viewModelScope.launch {
            when (val r = TaskApi.books(t)) {
                is CallResult.Ok -> {
                    books = parseBooks(r.json.optJSONArray("books"))
                    bookCounts = parseCounts(r.json.optJSONObject("counts"))
                    settings = parseSettings(r.json.optJSONObject("settings"))
                    booksState = LoadState.Ready
                    lastBooksAt = System.currentTimeMillis()
                    recompute()
                }
                is CallResult.Fail -> {
                    val msg = failText(r)
                    showToast("কিতাব লোড হয়নি: $msg", bad = true)
                    if (books.isEmpty()) booksState = LoadState.Error
                }
            }
            spinning = false
        }
    }

    // ---- my pages + payments (earnings) --------------------------------------------------
    fun loadMoney() {
        if (moneyJob?.isActive == true) return
        moneyJob = viewModelScope.launch { fetchMoney() }
    }

    /** Returns true when fresh data arrived. Used by the payment request (needs the latest numbers). */
    private suspend fun fetchMoney(): Boolean {
        val t = token() ?: return false
        val user = me ?: return false
        val proofsCall = viewModelScope.async { TaskApi.proofs(t) }
        val paysCall = viewModelScope.async { TaskApi.payments(t) }
        val pr = proofsCall.await()
        val pa = paysCall.await()

        if (pr !is CallResult.Ok) {
            if (pr is CallResult.Fail) failText(pr)
            return false
        }
        proofs = parseProofs(pr.json.optJSONArray("proofs"))
        // payments table may not exist on very old databases -> keep going with an empty list
        payments = if (pa is CallResult.Ok) parsePayments(pa.json.optJSONArray("payments")) else emptyList()
        finReady = true
        recompute()
        goalToast(user.id)
        return true
    }

    private fun goalToast(userId: String) {
        if (proofs.isEmpty() || !hasDates(proofs)) return
        val today = streakInfo(proofs).today
        if (today >= goal && !prefs.goalToastShownToday(userId)) {
            prefs.markGoalToastShown(userId)
            showToast("মাশাআল্লাহ! আজকের লক্ষ্য পূরণ হয়েছে (${bn(today)}/${bn(goal)} পেইজ)")
        }
    }

    // ---- tabs --------------------------------------------------------------------------------
    fun selectTab(t: TaskTab) {
        tab = t
        when (t) {
            TaskTab.Pay -> loadMoney()
            TaskTab.Rank -> loadRank(rankPeriod)
            TaskTab.Task -> if (System.currentTimeMillis() - lastBooksAt > 15_000) loadBooks()
        }
    }

    // ---- leaderboard (60 s cache per period) ----------------------------------------------------
    fun pickPeriod(p: RankPeriod) {
        rankPeriod = p
        loadRank(p)
    }

    fun loadRank(period: RankPeriod, force: Boolean = false) {
        val t = token() ?: return
        val seq = ++rankSeq     // a slow old answer must not cover a newer one
        val cached = rankCache[period]
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 60_000) {
            rank = RankState(loading = false, period = period, rows = cached.second)
            return
        }
        rank = rank.copy(loading = true, error = "")
        viewModelScope.launch {
            val since = when (period) {
                RankPeriod.Day -> startOfToday()
                RankPeriod.Week -> startOfDaysAgo(6)
                RankPeriod.All -> null
            }
            val r = TaskApi.leaderboard(t, since)
            if (seq != rankSeq) return@launch
            when (r) {
                is CallResult.Ok -> {
                    val rows = parseRank(r.json.optJSONArray("rows"))
                    rankCache[period] = System.currentTimeMillis() to rows
                    rank = RankState(loading = false, period = period, rows = rows)
                }
                is CallResult.Fail -> rank = rank.copy(loading = false, error = failText(r))
            }
        }
    }

    // ---- notices --------------------------------------------------------------------------------
    private fun joinedMillis(): Long =
        parseTime(me?.createdAt)?.minus(86_400_000L) ?: 0L

    private fun freshNotices(): List<Notice> {
        val id = me?.id ?: return emptyList()
        val seen = prefs.seenNotices(id)
        val joined = joinedMillis()
        return allNotices.filter { it.id !in seen && (it.createdAt ?: Long.MAX_VALUE) >= joined }
    }

    fun loadNotices() {
        if (noticeJob?.isActive == true) return
        val t = token() ?: return
        noticeJob = viewModelScope.launch {
            val r = TaskApi.notices(t)
            if (r is CallResult.Ok) {
                allNotices = parseNotices(r.json.optJSONArray("notices"))
                val fresh = freshNotices()
                badge = fresh.size
                if (fresh.isNotEmpty() && noticeQueue.isEmpty() && !guideOpen) {
                    noticeIndex = 0
                    noticeQueue = fresh.reversed()   // oldest first
                }
            } else if (r is CallResult.Fail) {
                failText(r)   // only to catch an expired session; a missing notices table is not an error
            }
        }
    }

    fun closeNotices() {
        val id = me?.id ?: return
        val seen = prefs.seenNotices(id)
        noticeQueue.forEach { seen.add(it.id) }
        prefs.saveSeenNotices(id, seen)
        noticeQueue = emptyList()
        noticeIndex = 0
        badge = freshNotices().size
    }

    /** Re-show everything that is still unseen (profile "notice" button will use this later). */
    fun openUnseenNotices() {
        val fresh = freshNotices()
        if (fresh.isNotEmpty()) {
            noticeIndex = 0
            noticeQueue = fresh.reversed()
        }
    }

    // ---- first-time guide ---------------------------------------------------------------------------
    fun openGuide() {
        guideOpen = true
    }

    fun closeGuide() {
        guideOpen = false
        me?.id?.let { prefs.setGuideSeen(it) }
        loadNotices()
    }

    // ---- daily goal ---------------------------------------------------------------------------------------
    fun openGoalDialog() {
        goalError = ""
        goalDialogOpen = true
    }

    fun closeGoalDialog() {
        goalDialogOpen = false
    }

    fun saveGoal(text: String) {
        val g = text.trim().toIntOrNull()
        if (g == null || g < 1 || g > 1000) {
            goalError = "১ থেকে ১০০০-এর মধ্যে একটি সংখ্যা দিন"
            return
        }
        goal = g
        me?.id?.let { prefs.setGoal(it, g) }
        goalDialogOpen = false
        showToast("দৈনিক লক্ষ্য: ${bn(g)} পেইজ")
    }

    // ---- payment request ------------------------------------------------------------------------------------
    fun onRequest() {
        if (requestBusy) return
        val user = me ?: return
        requestBusy = true
        viewModelScope.launch {
            val fresh = fetchMoney()
            requestBusy = false
            val s = summary
            val min = settings.minPayout
            if (!fresh || s == null) {
                showToast("তথ্য আনা যায়নি, আবার চেষ্টা করুন", bad = true)
                return@launch
            }
            if (user.payNumber.isBlank()) {
                showToast("আগে পেমেন্ট নাম্বার দিন", bad = true)
                return@launch
            }
            if (s.available < min || s.available <= 0) {
                showToast("ন্যূনতম ${money(min)} হলে রিকোয়েস্ট করা যাবে", bad = true)
                return@launch
            }
            requestError = ""
            requestInfo = RequestInfo(
                available = s.available,
                min = min,
                methodLabel = (PAY_METHOD_LABEL[user.payMethod] ?: "পেমেন্ট") + " নাম্বার",
                number = user.payNumber
            )
        }
    }

    fun closeRequest() {
        if (requestSending) return
        requestInfo = null
    }

    fun sendRequest(amountText: String) {
        if (requestSending) return
        val t = token() ?: return
        val amt = amountText.trim().toDoubleOrNull()?.let { r2(it) }
        val s = summary
        val min = settings.minPayout
        when {
            amt == null || amt.isNaN() || amt <= 0 -> { requestError = "সঠিক পরিমাণ দিন"; return }
            amt < min -> { requestError = "ন্যূনতম ${money(min)} চাইতে হবে"; return }
            s != null && amt > s.available -> {
                requestError = "উত্তোলনযোগ্য ${money(s.available)}-এর বেশি চাওয়া যাবে না"
                return
            }
        }
        requestError = ""
        requestSending = true
        viewModelScope.launch {
            val r = TaskApi.requestPayment(t, amt!!)
            requestSending = false
            when (r) {
                is CallResult.Ok -> {
                    requestInfo = null
                    showToast("পেমেন্ট রিকোয়েস্ট পাঠানো হয়েছে")
                    fetchMoney()
                }
                is CallResult.Fail -> {
                    requestError = when (r.error) {
                        "below_min" -> "ন্যূনতম ${money(r.value)} চাইতে হবে"
                        "exceeds_available" -> "উত্তোলনযোগ্য ${money(r.value)}-এর বেশি চাওয়া যাবে না"
                        "no_pay_number" -> "আগে পেমেন্ট নাম্বার দিন"
                        "invalid_amount" -> "সঠিক পরিমাণ দিন"
                        else -> failText(r)
                    }
                    if (r.error == "exceeds_available" || r.error == "below_min") fetchMoney()
                }
            }
        }
    }
}

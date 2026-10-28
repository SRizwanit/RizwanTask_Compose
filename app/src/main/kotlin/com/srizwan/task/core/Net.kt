package com.srizwan.task.core

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.srizwan.task.nav.Nav
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * common.js `Net`: no-internet / slow-net message bar.
 *  - offline  : red bar + retry button (stays until the connection returns)
 *  - slow     : (no amber bar any more) a request taking > 9 s raises [problem] for the no-internet dialog
 *  - ok       : green "internet is back" bar for 2.5 s (+ [onlineEvents], same as the `kp-online` event)
 */
object Net {
    enum class Mode { OFF, SLOW, OK }
    data class Bar(val mode: Mode, val text: String, val retry: Boolean)

    private val _bar = MutableStateFlow<Bar?>(null)
    val bar: StateFlow<Bar?> = _bar.asStateFlow()

    private val _online = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits every time the connection comes back (window 'kp-online'). */
    val onlineEvents: SharedFlow<Unit> = _online.asSharedFlow()

    /** Screens can handle the retry button themselves (window 'kp-retry' + preventDefault). Return true = handled. */
    private val retryHandlers = CopyOnWriteArrayList<() -> Boolean>()
    fun addRetryHandler(h: () -> Boolean) { retryHandlers.add(h) }
    fun removeRetryHandler(h: () -> Boolean) { retryHandlers.remove(h) }

    @Volatile
    var isOnline: Boolean = true
        private set

    private val _problem = MutableStateFlow(false)
    /**
     * "Real" connection problem flag for the no-internet dialog (Task page).
     * true = a request failed ([fail]) or has been running longer than 9 s (slow timer in [start]),
     * even though the system may think it is online. Cleared by the next successful request (Sb.req).
     */
    val problem: StateFlow<Boolean> = _problem.asStateFlow()

    fun reportProblem() { _problem.value = true }
    fun clearProblem() { _problem.value = false }

    private var started = false
    private var inflight = 0
    private var mode: Mode? = null
    private var slowJob: Job? = null
    private var hideJob: Job? = null
    private val scope get() = AppScope.scope

    fun init(app: Application) {
        if (started) return
        started = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        isOnline = cm.activeNetwork?.let { hasInternet(cm.getNetworkCapabilities(it)) } == true
        if (!isOnline) scope.launch { offline() }
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                // A connected network is not enough: it must also be VALIDATED (the system reached the internet through it).
                // onAvailable fires before validation, so the online state is decided here, not there.
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val now = hasInternet(caps)
                    scope.launch {
                        val was = isOnline
                        isOnline = now
                        if (now && !was) {
                            show(Mode.OK, "ইন্টারনেট ফিরেছে", false, 2500)
                            _online.tryEmit(Unit)
                        } else if (!now && was) offline()
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch { isOnline = false; offline() }
                }
            })
        } catch (e: Exception) {
            // no ACCESS_NETWORK_STATE -> keep working without the bar
        }
    }

    /** Real internet: the network declares INTERNET and the system has VALIDATED it (not a captive portal / dead wifi). */
    private fun hasInternet(c: NetworkCapabilities?): Boolean =
        c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun show(m: Mode, text: String, retry: Boolean, autoHideMs: Long = 0) {
        hideJob?.cancel()
        mode = m
        _bar.value = Bar(m, text, retry)
        if (autoHideMs > 0) hideJob = scope.launch { delay(autoHideMs); hide() }
    }

    fun hide() {
        _bar.value = null
        mode = null
    }

    private fun offline() = show(Mode.OFF, "ইন্টারনেট নেই — সংযোগ ফিরলে নিজে থেকেই আবার চেষ্টা হবে।", true)

    /** A request started. */
    fun start(): Int {
        scope.launch {
            inflight++
            slowJob?.cancel()
            slowJob = scope.launch {
                delay(9000)
                // পুরনো amber "নেট ধীর" বার আর নয় — ৯ সেকেন্ডে সাড়া না এলে নেট-সমস্যা ডায়লগের ফ্ল্যাগ চালু হয়
                if (inflight > 0) reportProblem()
            }
        }
        return 1
    }

    /** A request finished (success or error). */
    fun end() {
        scope.launch {
            inflight = maxOf(0, inflight - 1)
            if (inflight == 0) {
                slowJob?.cancel()
                if (mode == Mode.SLOW) hide()
            }
        }
    }

    /** A request failed because of the network. */
    fun fail() {
        reportProblem()
        scope.launch {
            if (!isOnline) offline()
            else show(Mode.OFF, "সংযোগ পাওয়া যাচ্ছে না — নেট দুর্বল হতে পারে।", true, 7000)
        }
    }

    /** "আবার চেষ্টা" button. */
    fun retry() {
        if (retryHandlers.any { it() }) return
        if (!isOnline) AppToast.show("এখনো ইন্টারনেট নেই", true)
        else if (!Nav.isOnView()) Nav.reload()
    }
}

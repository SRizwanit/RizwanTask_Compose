package com.srizwan.task.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * React `view/toaster.js` -> Kotlin.
 *
 * Toast + custom loading toast ("save and next"). While a loading run is active, normal (success) messages are collected
 * and shown together at the end; an error stops the loading and shows the error toast.
 *
 * Plain toasts go through the app-wide [AppToast] (one ToastHost, same look); only the loading toast has its own state
 * ([loading]), drawn by `ViewLoadingToast` (ui/ViewToasts.kt). Because only that composable collects [loading], a
 * percentage change recomposes just the toast, not the whole page (React used `useSyncExternalStore` for the same reason).
 *
 * Call every method from the main thread (as the React code ran on the UI thread).
 */
class ViewToaster(private val scope: CoroutineScope = AppScope.scope) {

    data class Loading(val show: Boolean = false, val title: String = "লোড হচ্ছে…", val step: String = "", val pct: Float = 0f)

    private val _loading = MutableStateFlow(Loading())
    val loading: StateFlow<Loading> = _loading.asStateFlow()

    private var on = false
    private var finishing = false
    private var total = 1
    private var k = 1
    private var pct = 0f
    private var notes = mutableListOf<String>()
    private var tickJob: Job? = null
    private var doneJob: Job? = null

    val isLoading: Boolean get() = on
    val isFinishing: Boolean get() = finishing

    private fun setLt(f: (Loading) -> Loading) { _loading.value = f(_loading.value) }

    /**
     * Real progress is unknown (until GitHub answers), so during a step the percentage creeps forward slowly (up to 92% of
     * the step); when a step ends it jumps to the start of the next step; at the very end 100%.
     */
    private fun tick() {
        val lo = (k - 1).toFloat() / total * 100f
        val hi = k.toFloat() / total * 100f
        val cap = lo + (hi - lo) * 0.92f
        if (pct < cap) pct = min(cap, pct + max(0.15f, (cap - pct) * 0.07f))
        setLt { it.copy(pct = pct) }
    }

    fun loadingStart(total: Int) {
        tickJob?.cancel(); doneJob?.cancel()
        on = true; finishing = false; this.total = max(1, total); k = 1; pct = 0f; notes = mutableListOf()
        AppToast.hide()
        setLt { it.copy(show = true, pct = 0f) }
        tickJob = scope.launch { while (isActive) { delay(120); tick() } }
    }

    fun loadingStep(k: Int, msg: String) {
        if (!on) return
        this.k = k; pct = max(pct, (k - 1).toFloat() / total * 100f)
        setLt { it.copy(title = msg, step = "ধাপ $k/$total", pct = pct) }
    }

    fun loadingHide() {
        tickJob?.cancel(); doneJob?.cancel()
        on = false; finishing = false
        setLt { it.copy(show = false) }
    }

    /** [ms] null = default: 5 s for an error, 1.8 s otherwise. */
    fun toast(msg: String, err: Boolean = false, ms: Long? = null) {
        if (on) {
            if (!err) { if (!msg.startsWith("সেভ হয়েছে")) notes.add(msg); return }
            loadingHide()                 // error: remove the loading, show the error toast
        }
        AppToast.show(msg, err, ms ?: if (err) 5000L else 1800L)
    }

    /** Success end: show 100% (0.45 s), close the loading, then the success toast. */
    fun loadingDone(msg: String) {
        if (!on) return
        tickJob?.cancel()
        finishing = true; pct = 100f
        setLt { it.copy(title = "সম্পন্ন", pct = 100f) }
        doneJob = scope.launch {
            delay(450)
            val n = notes.toList()
            loadingHide()
            toast(if (n.isNotEmpty()) msg + " • " + n.joinToString(" • ") else msg, false, if (n.isNotEmpty()) 3800L else 2400L)
        }
    }

    fun dispose() { tickJob?.cancel(); doneJob?.cancel() }
}

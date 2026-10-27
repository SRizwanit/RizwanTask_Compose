package com.srizwan.task.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** common.js `toast(msg, bad)` — shown for 3.2 s (or [ms]) by ToastHost (ui/Overlays.kt). */
object AppToast {
    data class Msg(val text: String, val bad: Boolean, val id: Long)

    private val _msg = MutableStateFlow<Msg?>(null)
    val msg: StateFlow<Msg?> = _msg.asStateFlow()
    private var job: Job? = null

    fun show(text: String, bad: Boolean = false, ms: Long = 3200) {
        AppScope.scope.launch {
            _msg.value = Msg(text, bad, System.nanoTime())
            job?.cancel()
            job = launch { delay(ms); _msg.value = null }
        }
    }

    /** Hide the current toast right away (View loading toast takes the place of a plain toast). */
    fun hide() {
        AppScope.scope.launch { job?.cancel(); _msg.value = null }
    }
}

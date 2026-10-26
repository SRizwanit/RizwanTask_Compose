package com.srizwan.task.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** common.js `toast(msg, bad)` — shown for 3.2 s by ToastHost (ui/Toast.kt). */
object AppToast {
    data class Msg(val text: String, val bad: Boolean, val id: Long)

    private val _msg = MutableStateFlow<Msg?>(null)
    val msg: StateFlow<Msg?> = _msg.asStateFlow()
    private var job: Job? = null

    fun show(text: String, bad: Boolean = false) {
        AppScope.scope.launch {
            _msg.value = Msg(text, bad, System.nanoTime())
            job?.cancel()
            job = launch { delay(3200); _msg.value = null }
        }
    }
}

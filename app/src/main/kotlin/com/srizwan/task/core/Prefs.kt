package com.srizwan.task.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for fire-and-forget work (activity log, last-active ping, net bar timers). */
object AppScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** localStorage replacement (keys keep the same names as the React app: kp_session_v1, kp_device_v1, ...). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(app: Application) {
        sp = app.getSharedPreferences("rizwantask_prefs", Context.MODE_PRIVATE)
    }

    fun getString(key: String, def: String? = null): String? = sp.getString(key, def)
    fun putString(key: String, value: String?) = sp.edit { if (value == null) remove(key) else putString(key, value) }
    fun getLong(key: String, def: Long = 0L): Long = sp.getLong(key, def)
    fun putLong(key: String, value: Long) = sp.edit { putLong(key, value) }
    fun remove(key: String) = sp.edit { remove(key) }
}

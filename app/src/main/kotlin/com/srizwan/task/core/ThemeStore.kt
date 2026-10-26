package com.srizwan.task.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { AUTO, LIGHT, DARK }

/** common.js `theme`: auto (system) -> light -> dark, saved under kp_theme_v1. */
object ThemeStore {
    private const val TK = "kp_theme_v1"

    var mode by mutableStateOf(ThemeMode.AUTO)
        private set

    fun load() {
        mode = when (Prefs.getString(TK)) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.AUTO
        }
    }

    fun set(m: ThemeMode) {
        mode = m
        when (m) {
            ThemeMode.AUTO -> Prefs.remove(TK)
            ThemeMode.LIGHT -> Prefs.putString(TK, "light")
            ThemeMode.DARK -> Prefs.putString(TK, "dark")
        }
    }

    fun next(m: ThemeMode = mode): ThemeMode = when (m) {
        ThemeMode.AUTO -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.AUTO
    }

    fun label(m: ThemeMode = mode): String = when (m) {
        ThemeMode.AUTO -> "সিস্টেম অনুযায়ী"
        ThemeMode.LIGHT -> "লাইট মোড"
        ThemeMode.DARK -> "ডার্ক মোড"
    }
}

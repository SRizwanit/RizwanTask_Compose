package com.srizwan.task.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { AUTO, LIGHT, DARK }

/** The look of the app (colours + animated background). Day / night ([ThemeMode]) works on top of every one of them. */
enum class AppPalette(val key: String, val label: String, val desc: String) {
    GARDEN("garden", "বাগান", "সবুজ ফুল ও জোনাকি"),
    OCEAN("ocean", "সাগর", "নীল জল ও বুদবুদ"),
    TWILIGHT("twilight", "গোধূলি", "তারাভরা বেগুনি আকাশ");

    companion object {
        fun of(key: String?): AppPalette = entries.firstOrNull { it.key == key } ?: GARDEN
    }
}

/** common.js `theme`: auto (system) -> light -> dark, saved under kp_theme_v1. */
object ThemeStore {
    private const val TK = "kp_theme_v1"
    private const val PK = "kp_palette_v1"

    /** The chosen design theme (garden / ocean / twilight), saved under kp_palette_v1. */
    var palette by mutableStateOf(AppPalette.GARDEN)
        private set

    fun setPalette(p: AppPalette) {
        palette = p
        if (p == AppPalette.GARDEN) Prefs.remove(PK) else Prefs.putString(PK, p.key)
    }

    var mode by mutableStateOf(ThemeMode.AUTO)
        private set

    fun load() {
        palette = AppPalette.of(Prefs.getString(PK))
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

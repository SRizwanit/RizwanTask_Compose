package com.srizwan.task.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** The "অ্যানিমেশন কমান" choice in the profile (saved under kp_motion_v1). */
enum class MotionPref(val key: String, val label: String) {
    AUTO("auto", "অটো"),
    FULL("full", "পূর্ণ"),
    LOW("low", "কম"),
    OFF("off", "বন্ধ");

    companion object {
        fun of(key: String?): MotionPref = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** What the animated background really does right now. */
enum class MotionLevel {
    /** every frame (the screen's refresh rate) */
    FULL,
    /** slow: only about 8 redraws a second (less CPU / GPU / battery) */
    LOW,
    /** one still picture, no redraw loop at all */
    OFF
}

/**
 * Decides how much the animated background (Theme.kt `RizwanBackground`) moves.
 *  - AUTO: battery saver on -> [MotionLevel.LOW]; the phone's animations switched off (animator scale 0) -> [MotionLevel.OFF]; else FULL.
 *  - FULL / LOW / OFF: the user's own choice, whatever the phone says.
 * [powerSave] and [systemOff] are Compose state, so the background reacts at once when battery saver is switched on or off.
 */
object MotionStore {
    private const val K = "kp_motion_v1"

    /** Milliseconds between two redraws in [MotionLevel.LOW]. */
    const val LOW_FRAME_MS = 125L

    var pref by mutableStateOf(MotionPref.AUTO)
        private set
    var powerSave by mutableStateOf(false)
        private set
    var systemOff by mutableStateOf(false)
        private set

    val level: MotionLevel
        get() = when (pref) {
            MotionPref.FULL -> MotionLevel.FULL
            MotionPref.LOW -> MotionLevel.LOW
            MotionPref.OFF -> MotionLevel.OFF
            MotionPref.AUTO -> if (systemOff) MotionLevel.OFF else if (powerSave) MotionLevel.LOW else MotionLevel.FULL
        }

    private var started = false

    fun init(app: Application) {
        if (started) return
        started = true
        pref = MotionPref.of(Prefs.getString(K))
        refresh(app)
        try {
            ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { refresh(context) }
            }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            // no receiver -> the state is still refreshed in MainActivity.onResume
        }
    }

    /** Reads battery saver + the phone's animation scale again (also called from MainActivity.onResume). */
    fun refresh(ctx: Context) {
        powerSave = try { ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true } catch (_: Exception) { false }
        systemOff = try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) { false }
    }

    fun set(p: MotionPref) {
        pref = p
        if (p == MotionPref.AUTO) Prefs.remove(K) else Prefs.putString(K, p.key)
    }

    /** The small line under the choice in the profile. */
    fun hint(): String = when (pref) {
        MotionPref.AUTO -> when {
            systemOff -> "অটো: ফোনের অ্যানিমেশন বন্ধ — ব্যাকগ্রাউন্ড এখন স্থির।"
            powerSave -> "অটো: ব্যাটারি সেভার চালু — ব্যাকগ্রাউন্ড এখন ধীরে চলছে।"
            else -> "অটো: ব্যাটারি সেভার চালু বা ফোনের অ্যানিমেশন বন্ধ থাকলে ব্যাকগ্রাউন্ড ধীর/স্থির হবে।"
        }
        MotionPref.FULL -> "পূর্ণ: ব্যাকগ্রাউন্ড সবসময় পুরো গতিতে চলবে।"
        MotionPref.LOW -> "কম: ব্যাকগ্রাউন্ড ধীরে চলবে (কম ব্যাটারি খরচ)।"
        MotionPref.OFF -> "বন্ধ: ব্যাকগ্রাউন্ড স্থির ছবি হয়ে থাকবে, কোনো ফ্রেম আঁকা হবে না।"
    }
}

package com.srizwan.task.core

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import java.util.UUID

/**
 * One account per device.
 *
 * The device identity is NOT a random id that lives only in the app's storage any more (that one was lost on
 * "Clear data" / uninstall, so the same phone could register again with another number). It is now derived from
 * Android's `Settings.Secure.ANDROID_ID`, which stays the same after clearing data and after a reinstall
 * (same signing key) and only changes on a factory reset. The raw value is never sent — only a salted SHA-256.
 *
 * Old installs: their account row still holds the old random id (localStorage kp_device_v1 -> SharedPreferences).
 * That id is still accepted ([all] / [matches]) and the row is moved to the new id on the next successful
 * guard check ([Auth.requireAuth]), so nobody is logged out by this update.
 */
object Device {
    private const val DK = "kp_device_v1"
    private const val BAD_ANDROID_ID = "9774d56d682e549c"   // the famous broken value of some old devices

    private var app: Application? = null
    private var hw: String? = null
    private var hwDone = false

    fun init(a: Application) { app = a }

    /** Hashed ANDROID_ID ("hw_…"), or null when the phone gives no usable value. */
    @SuppressLint("HardwareIds")
    private fun hardwareId(): String? {
        if (hwDone) return hw
        val a = app ?: return null
        hwDone = true
        hw = try {
            val raw = Settings.Secure.getString(a.contentResolver, Settings.Secure.ANDROID_ID).orEmpty().trim().lowercase()
            if (raw.isEmpty() || raw == BAD_ANDROID_ID || raw.all { it == '0' }) null
            else "hw_" + Pin.sha256("rizwantask-device-v2:$raw").take(40)
        } catch (e: Exception) {
            null
        }
        return hw
    }

    /** The random id of old installs (null on a fresh install). */
    private fun legacyId(): String? = Prefs.getString(DK)?.trim()?.takeIf { it.isNotEmpty() }

    /** The id that is written to the server (new accounts, login binding). */
    fun id(): String {
        hardwareId()?.let { return it }
        legacyId()?.let { return it }
        // no usable hardware id: fall back to a random one stored in the app (old behaviour)
        val d = UUID.randomUUID().toString()
        Prefs.putString(DK, d)
        return d
    }

    /** Every id this phone may be stored under on the server: the new one + the old random one. */
    fun all(): List<String> = listOfNotNull(hardwareId(), legacyId()).distinct().ifEmpty { listOf(id()) }

    /** True when [stored] (an `app_users.device_id`) belongs to this phone. */
    fun matches(stored: String?): Boolean {
        val s = stored?.trim() ?: return false
        return !isEmpty(s) && s in all()
    }

    /** True when the row is bound to this phone but still under an old id -> should be moved to [id]. */
    fun needsUpgrade(stored: String?): Boolean = matches(stored) && stored?.trim() != id()

    fun isEmpty(d: String?): Boolean = d == null || d.trim().isEmpty() || d.trim().lowercase() == "null"
}

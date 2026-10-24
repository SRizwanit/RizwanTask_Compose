package com.srizwan.task

import android.content.Context
import org.json.JSONObject

/**
 * Keeps the login session (token + user info) on the phone.
 *
 * It also remembers which mobile number this phone belongs to
 * ("device binding"). The binding is kept in a SEPARATE file, so logout
 * clears the session but NOT the binding. That is what stops a second
 * number from logging in on the same phone after a logout.
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext
        .getSharedPreferences("rizwantask_session", Context.MODE_PRIVATE)

    // survives logout()
    private val devicePrefs = appContext
        .getSharedPreferences("rizwantask_device", Context.MODE_PRIVATE)

    init {
        // users who were already logged in before this update
        if (boundPhone() == null) {
            user()?.phone?.takeIf { it.isNotBlank() }?.let { bindPhone(it) }
        }
    }

    fun save(user: AppUser, token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user.toJson().toString())
            .apply()
        if (user.phone.isNotBlank()) bindPhone(user.phone)
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun user(): AppUser? = runCatching {
        prefs.getString(KEY_USER, null)?.let { AppUser.fromJson(JSONObject(it)) }
    }.getOrNull()

    /** Logout: removes token + user only. The device binding stays. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    // ---- device binding -----------------------------------------------------

    fun boundPhone(): String? =
        devicePrefs.getString(KEY_BOUND_PHONE, null)?.takeIf { it.isNotBlank() }

    fun bindPhone(phone: String) {
        devicePrefs.edit().putString(KEY_BOUND_PHONE, normalizePhone(phone)).apply()
    }

    /** Only used when the server says this account no longer exists. */
    fun unbindPhone() {
        devicePrefs.edit().remove(KEY_BOUND_PHONE).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
        const val KEY_BOUND_PHONE = "bound_phone"
    }
}

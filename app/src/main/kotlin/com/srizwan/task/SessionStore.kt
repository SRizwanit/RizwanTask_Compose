package com.srizwan.task

import android.content.Context
import org.json.JSONObject

/** Keeps the login session (token + user info) on the phone. */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rizwantask_session", Context.MODE_PRIVATE)

    fun save(user: AppUser, token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user.toJson().toString())
            .apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun user(): AppUser? = runCatching {
        prefs.getString(KEY_USER, null)?.let { AppUser.fromJson(JSONObject(it)) }
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}

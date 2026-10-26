package com.srizwan.task.core

import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

data class SessionInfo(val id: String, val phone: String?, val name: String?)

/** common.js `session` (localStorage kp_session_v1). */
object Session {
    private const val SK = "kp_session_v1"

    fun get(): SessionInfo? {
        val s = Prefs.getString(SK) ?: return null
        return try {
            val o = JSONObject(s)
            val id = o.opt("id")?.takeIf { it != JSONObject.NULL }?.toString() ?: return null
            SessionInfo(id, o.str("phone"), o.str("name"))
        } catch (e: Exception) {
            null
        }
    }

    fun set(u: AppUser) {
        Prefs.putString(SK, jsonOf("id" to u.id, "phone" to u.phone, "name" to u.fullName).toString())
    }

    fun clear() = Prefs.remove(SK)
}

/** common.js logout() / requireAuth() / blockedScreen() / touchActive(). */
object Auth {
    private val _blocked = MutableStateFlow<AppUser?>(null)
    /** Non-null = show the full-screen "account blocked" page (ui/BlockedScreen). */
    val blocked: StateFlow<AppUser?> = _blocked.asStateFlow()

    fun logout() {
        Session.clear()
        _blocked.value = null
        Nav.go(Routes.LOGIN, replace = true)
    }

    /**
     * Guard: returns the fresh user row, or null when the user was redirected / blocked / the network failed
     * (the React version never resolved in those cases — callers just `?: return`).
     */
    suspend fun requireAuth(admin: Boolean = false): AppUser? {
        val s = Session.get()
        if (s == null) { Nav.go(Routes.LOGIN, replace = true); return null }
        val u: AppUser? = try {
            Users.byId(s.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppToast.show("নেটওয়ার্ক সমস্যা: " + e.message, true)
            return null
        }
        if (u == null || Device.isEmpty(u.deviceId) || u.deviceId != Device.id()) { logout(); return null }
        if (u.isBlocked && !Phone.isAdminPhone(u.phone)) { _blocked.value = u; return null }
        _blocked.value = null
        if (admin && !Phone.isAdminPhone(u.phone)) { Nav.go(Routes.TASK, replace = true); return null }
        touchActive(u)
        return u
    }

    /** Last-active time, at most once per 5 minutes; a missing column / failure is ignored. */
    private fun touchActive(u: AppUser) {
        val k = "kp_touch_v1"
        val n = System.currentTimeMillis()
        if (n - Prefs.getLong(k) < 300000) return
        Prefs.putLong(k, n)
        AppScope.scope.launch {
            try {
                Sb.patchMin("app_users?id=eq.${enc(u.id)}", jsonOf("last_active_at" to Instant.ofEpochMilli(n).toString()))
            } catch (e: Exception) {
                // ignored
            }
        }
    }
}

/** sessionStorage `kp_signup` — phone + PIN verified on the login screen, consumed by the signup screen. */
object PendingSignup {
    var phone: String? = null
        private set
    var pin: String? = null
        private set

    fun set(phone: String, pin: String) { this.phone = phone; this.pin = pin }
    fun clear() { phone = null; pin = null }
    fun isValid() = !phone.isNullOrEmpty() && !pin.isNullOrEmpty()
}

/** common.js log(): activity_log row; a failure never stops the app. */
object ActivityLog {
    suspend fun log(action: String, detail: String = "", whoId: String? = null, whoName: String? = null) {
        try {
            val s = Session.get()
            val id = whoId ?: s?.id
            val name = whoName ?: s?.name ?: ""
            Sb.postQuiet(
                "activity_log",
                jsonOf("user_id" to id, "user_name" to name, "action" to action, "detail" to detail.take(500))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignored
        }
    }
}

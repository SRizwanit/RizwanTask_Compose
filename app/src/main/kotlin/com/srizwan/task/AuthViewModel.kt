package com.srizwan.task

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

const val PHONE_LENGTH = 11
const val PIN_LENGTH = 4

private val PHONE_REGEX = Regex("^01[3-9][0-9]{8}$")

fun isValidPhone(value: String) = PHONE_REGEX.matches(value)
fun isValidPin(value: String) = value.length == PIN_LENGTH && value.all { it in '0'..'9' }

/** "+8801712345678" / "8801712345678" / "1712345678" -> "01712345678" */
fun normalizePhone(value: String): String {
    val digits = value.filter { it in '0'..'9' }
    return when {
        digits.length == 13 && digits.startsWith("880") -> "0" + digits.drop(3)
        digits.length == 10 && !digits.startsWith("0") -> "0$digits"
        else -> digits
    }
}

fun samePhone(a: String, b: String) = normalizePhone(a) == normalizePhone(b)

enum class Screen { Splash, Login, Signup, Task }

enum class HelpReason { WrongPin, ForgotPin, Locked, Blocked, DeviceMismatch, DeviceInUse }

/** [extra]: WrongPin -> attempts left, Locked -> seconds left */
data class HelpDialogState(val reason: HelpReason, val extra: Int = 0)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)

    /** Unique id of this phone, sent with every login / signup. */
    val androidId: String = Settings.Secure
        .getString(application.contentResolver, Settings.Secure.ANDROID_ID)
        .orEmpty()

    var screen by mutableStateOf(Screen.Splash)
        private set
    var user by mutableStateOf(store.user())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var helpDialog by mutableStateOf<HelpDialogState?>(null)
        private set
    var signupNotice by mutableStateOf<String?>(null)
        private set
    var lastPhone by mutableStateOf(store.boundPhone().orEmpty())
        private set

    /** The only number that may log in on this phone (null = nobody yet). */
    var boundPhone by mutableStateOf(store.boundPhone())
        private set

    /** PIN typed on the Login page. Signup does not ask for a PIN, it uses this one. Kept in memory only. */
    private var pendingPin: String? = null

    private var sessionValid: Boolean? = null   // null = still checking
    private var splashDone = false

    init {
        verifyStoredSession()
    }

    // ---- start-up -----------------------------------------------------------

    private fun verifyStoredSession() {
        val token = store.token()
        if (token == null || store.user() == null) {
            sessionValid = false
            return
        }
        viewModelScope.launch {
            when (val result = SupabaseApi.checkSession(token, androidId)) {
                is ApiResult.Success -> {
                    store.save(result.user, token)
                    boundPhone = store.boundPhone()
                    user = result.user
                    sessionValid = true
                }
                is ApiResult.Failure -> when (result.error) {
                    // offline / server problem: keep the saved session
                    AuthError.NETWORK, AuthError.NOT_CONFIGURED, AuthError.UNKNOWN ->
                        sessionValid = true
                    // blocked, device reset by admin, invalid token ...
                    else -> {
                        store.clear()
                        user = null
                        sessionValid = false
                    }
                }
            }
            routeIfReady()
        }
    }

    /** Called by the splash screen after the typewriter + 20 second wait. */
    fun onSplashFinished() {
        splashDone = true
        routeIfReady()
    }

    private fun routeIfReady() {
        if (!splashDone || screen != Screen.Splash) return
        val valid = sessionValid ?: return
        screen = if (valid && user != null) Screen.Task else Screen.Login
    }

    // ---- navigation ---------------------------------------------------------

    /** Login page -> "একাউন্ট খুলুন". The number and PIN typed on Login are used for the new account. */
    fun openSignup(phone: String, pin: String) {
        message = null
        if (boundPhone != null) {
            helpDialog = HelpDialogState(HelpReason.DeviceInUse)
            return
        }
        if (!isValidPhone(phone) || !isValidPin(pin)) {
            message = "একাউন্ট খুলতে আগে মোবাইল নাম্বার ও $PIN_LENGTH ডিজিটের পিন দিন।"
            return
        }
        lastPhone = phone
        pendingPin = pin
        signupNotice = "আপনার দেওয়া মোবাইল নাম্বার ও পিন দিয়েই একাউন্ট খোলা হবে। নিচের তথ্যগুলো পূরণ করুন।"
        screen = Screen.Signup
    }

    fun openLogin() {
        message = null
        signupNotice = null
        pendingPin = null
        screen = Screen.Login
    }

    fun showForgotPin(phone: String) {
        lastPhone = phone
        helpDialog = HelpDialogState(HelpReason.ForgotPin)
    }

    fun dismissHelp() {
        helpDialog = null
    }

    fun clearMessage() {
        message = null
    }

    // ---- login ----------------------------------------------------------------

    fun login(phone: String, pin: String) {
        if (loading) return
        message = null
        lastPhone = phone

        if (!isValidPhone(phone)) return fail(AuthError.INVALID_PHONE)
        if (!isValidPin(pin)) return fail(AuthError.INVALID_PIN)
        if (androidId.isBlank()) {
            message = "এই ডিভাইসের আইডি পাওয়া যায়নি, অ্যাপ আবার চালু করুন।"
            return
        }

        // one phone = one account: a different number can not log in here
        val bound = boundPhone
        if (bound != null && !samePhone(bound, phone)) {
            helpDialog = HelpDialogState(HelpReason.DeviceInUse)
            return
        }

        pendingPin = pin
        loading = true
        viewModelScope.launch {
            val result = SupabaseApi.login(phone, pin, androidId)
            loading = false
            handle(result)
        }
    }

    // ---- signup ---------------------------------------------------------------

    /** No PIN here: the PIN typed on the Login page is used (see [openSignup]). */
    fun signup(
        fullName: String,
        address: String,
        payMethod: String,
        payNumber: String,
        phone: String
    ) {
        if (loading) return
        message = null
        lastPhone = phone

        if (boundPhone != null) {
            helpDialog = HelpDialogState(HelpReason.DeviceInUse)
            return
        }

        if (fullName.trim().length < 2) return fail(AuthError.INVALID_NAME)
        if (address.trim().length < 3) return fail(AuthError.INVALID_ADDRESS)
        if (!isValidPhone(payNumber)) return fail(AuthError.INVALID_PAY_NUMBER)
        if (!isValidPhone(phone)) return fail(AuthError.INVALID_PHONE)

        val pin = pendingPin
        if (pin == null || !isValidPin(pin)) {
            // PIN is gone (e.g. app restarted) -> ask for it on the Login page
            openLogin()
            message = "পিন লগইন পেজে দিতে হয়। মোবাইল নাম্বার ও পিন দিয়ে আবার চেষ্টা করুন।"
            return
        }
        if (androidId.isBlank()) {
            message = "এই ডিভাইসের আইডি পাওয়া যায়নি, অ্যাপ আবার চালু করুন।"
            return
        }

        loading = true
        viewModelScope.launch {
            val result = SupabaseApi.register(
                fullName = fullName.trim(),
                address = address.trim(),
                phone = phone,
                payMethod = payMethod,
                payNumber = payNumber,
                pin = pin,
                androidId = androidId
            )
            loading = false
            handle(result)
        }
    }

    // ---- logout ---------------------------------------------------------------

    fun logout() {
        val token = store.token()
        store.clear()          // session only - the device binding stays
        lastPhone = store.boundPhone() ?: user?.phone.orEmpty()
        user = null
        message = null
        pendingPin = null
        if (token != null) {
            viewModelScope.launch { SupabaseApi.logout(token) }
        }
        screen = Screen.Login
    }

    // ---- result handling -----------------------------------------------------

    private fun fail(error: AuthError) {
        message = error.text()
    }

    private fun handle(result: ApiResult) {
        when (result) {
            is ApiResult.Success -> {
                val token = result.token
                if (token == null) {
                    message = AuthError.UNKNOWN.text()
                    return
                }
                store.save(result.user, token)
                boundPhone = store.boundPhone()
                user = result.user
                message = null
                signupNotice = null
                pendingPin = null
                screen = Screen.Task
            }

            is ApiResult.Failure -> when (result.error) {
                // new user -> open Signup with the typed number
                AuthError.USER_NOT_FOUND -> {
                    // the bound account was deleted on the server -> free this phone
                    val bound = boundPhone
                    if (bound != null && samePhone(bound, lastPhone)) {
                        store.unbindPhone()
                        boundPhone = null
                    }
                    message = null
                    signupNotice = "এই নাম্বারে কোনো একাউন্ট নেই। লগইন পেজে দেওয়া নাম্বার ও পিন দিয়েই নতুন একাউন্ট খোলা হবে, নিচের তথ্যগুলো পূরণ করুন।"
                    screen = Screen.Signup
                }
                AuthError.WRONG_PIN ->
                    helpDialog = HelpDialogState(HelpReason.WrongPin, result.extra)
                AuthError.LOCKED ->
                    helpDialog = HelpDialogState(HelpReason.Locked, result.extra)
                AuthError.BLOCKED ->
                    helpDialog = HelpDialogState(HelpReason.Blocked)
                AuthError.DEVICE_MISMATCH ->
                    helpDialog = HelpDialogState(HelpReason.DeviceMismatch)
                AuthError.DEVICE_IN_USE ->
                    helpDialog = HelpDialogState(HelpReason.DeviceInUse)
                else -> message = result.error.text()
            }
        }
    }
}

fun AuthError.text(): String = when (this) {
    AuthError.INVALID_PHONE -> "সঠিক ১১ ডিজিটের মোবাইল নাম্বার দিন (যেমন 017XXXXXXXX)।"
    AuthError.INVALID_PIN -> "পিন অবশ্যই $PIN_LENGTH ডিজিটের সংখ্যা হতে হবে।"
    AuthError.INVALID_NAME -> "আপনার নাম লিখুন (কমপক্ষে ২ অক্ষর)।"
    AuthError.INVALID_ADDRESS -> "আপনার ঠিকানা লিখুন।"
    AuthError.INVALID_PAY_NUMBER -> "সঠিক ১১ ডিজিটের বিকাশ/নগদ নাম্বার দিন।"
    AuthError.PHONE_EXISTS -> "এই নাম্বারে আগেই একাউন্ট আছে, লগইন করুন।"
    AuthError.NETWORK -> "ইন্টারনেট সংযোগ পাওয়া যায়নি। সংযোগ দেখে আবার চেষ্টা করুন।"
    AuthError.NOT_CONFIGURED -> "Supabase সেটআপ করা নেই। local.properties ফাইলে SUPABASE_URL ও SUPABASE_ANON_KEY দিন।"
    AuthError.INVALID_SESSION -> "সেশন শেষ হয়েছে, আবার লগইন করুন।"
    else -> "কিছু একটা সমস্যা হয়েছে, আবার চেষ্টা করুন।"
}

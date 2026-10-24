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
    var lastPhone by mutableStateOf("")
        private set

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

    fun openSignup(phone: String = "") {
        message = null
        signupNotice = null
        if (phone.isNotBlank()) lastPhone = phone
        screen = Screen.Signup
    }

    fun openLogin() {
        message = null
        signupNotice = null
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

        loading = true
        viewModelScope.launch {
            val result = SupabaseApi.login(phone, pin, androidId)
            loading = false
            handle(result)
        }
    }

    // ---- signup ---------------------------------------------------------------

    fun signup(
        fullName: String,
        address: String,
        payMethod: String,
        payNumber: String,
        phone: String,
        pin: String,
        confirmPin: String
    ) {
        if (loading) return
        message = null
        lastPhone = phone

        if (fullName.trim().length < 2) return fail(AuthError.INVALID_NAME)
        if (address.trim().length < 3) return fail(AuthError.INVALID_ADDRESS)
        if (!isValidPhone(payNumber)) return fail(AuthError.INVALID_PAY_NUMBER)
        if (!isValidPhone(phone)) return fail(AuthError.INVALID_PHONE)
        if (!isValidPin(pin)) return fail(AuthError.INVALID_PIN)
        if (pin != confirmPin) {
            message = "পিন দুটি মিলছে না।"
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
        store.clear()
        user = null
        message = null
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
                user = result.user
                message = null
                signupNotice = null
                screen = Screen.Task
            }

            is ApiResult.Failure -> when (result.error) {
                // new user -> open Signup with the typed number
                AuthError.USER_NOT_FOUND -> {
                    message = null
                    signupNotice = "এই নাম্বারে কোনো একাউন্ট নেই। নিচের ফর্ম পূরণ করে নতুন একাউন্ট খুলুন।"
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

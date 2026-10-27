package com.srizwan.task.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Device
import com.srizwan.task.core.Otp
import com.srizwan.task.core.PendingSignup
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Pin
import com.srizwan.task.core.Sb
import com.srizwan.task.core.SbException
import com.srizwan.task.core.Session
import com.srizwan.task.core.Users
import com.srizwan.task.core.enc
import com.srizwan.task.core.jsonOf
import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * login.html -> Kotlin. Steps: phone -> (new user) OTP -> PIN -> (device problem) device step.
 * The logic is the same as the React Login.jsx.
 */
class LoginViewModel : ViewModel() {
    enum class Step { PHONE, OTP, PIN, DEV }
    enum class PinMode { LOGIN, SETUP_EXISTING, SETUP_NEW }
    enum class Block { BOUND, USED }

    var step by mutableStateOf(Step.PHONE); private set
    var mode by mutableStateOf(PinMode.LOGIN); private set
    var blockReason by mutableStateOf(Block.BOUND); private set
    /** masked phone of the account that already owns this device (shown on the "device used" step) */
    var boundHint by mutableStateOf(""); private set

    var phoneIn by mutableStateOf("")
    var otp by mutableStateOf("")
    var pin by mutableStateOf("")
    var pin2 by mutableStateOf("")

    var err by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    var otpCode by mutableStateOf(""); private set
    var showOtpDialog by mutableStateOf(false)
    /** seconds left until "OTP আবার পাঠান" works again */
    var left by mutableIntStateOf(0); private set

    /** verified phone number */
    var phone = ""; private set
    private var user: AppUser? = null
    private var timer: Job? = null
    private var sessionChecked = false

    // The device already has a login -> go straight to Task
    fun checkExistingSession() {
        if (sessionChecked) return
        sessionChecked = true
        if (Session.get() == null) return
        viewModelScope.launch {
            Auth.requireAuth()?.let { Nav.go(Routes.TASK, replace = true) }
        }
    }

    override fun onCleared() {
        timer?.cancel()
    }

    private fun show(s: Step) { step = s; err = "" }
    private fun pinStep(m: PinMode) { mode = m; pin = ""; pin2 = ""; show(Step.PIN) }
    private fun devStep(reason: Block, owner: AppUser? = null) {
        blockReason = reason
        boundHint = if (reason == Block.USED) Phone.mask(owner?.phone) else ""
        show(Step.DEV)
    }

    private fun isDuplicate(e: Exception): Boolean =
        e is SbException && Regex("duplicate|23505", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())

    // user exists: PIN or device block?
    // Rule: one device = one account. The account may only be opened on the device it is bound to, and a device that
    // already belongs to another account cannot be used for this one (checked here, before the PIN is even asked).
    private suspend fun afterUserKnown(u: AppUser) {
        val stored = u.deviceId
        if (!Device.isEmpty(stored) && !Device.matches(stored)) return devStep(Block.BOUND)
        if (Device.isEmpty(stored)) {
            val other = Users.findByDevice(Device.id(), u.id)
            if (other != null) return devStep(Block.USED, other)
        }
        pinStep(if (Pin.hasPin(u)) PinMode.LOGIN else PinMode.SETUP_EXISTING)
    }

    private fun startResend() {
        timer?.cancel()
        left = 120
        timer = viewModelScope.launch {
            var s = 120
            while (s > 0) {
                delay(1000)
                s -= 1
                left = maxOf(0, s)
            }
        }
    }

    private suspend fun startOtp() {
        val code = Otp.request(phone)
        show(Step.OTP)
        otp = ""
        otpCode = code
        showOtpDialog = true
        startResend()
    }

    private fun launchBusy(block: suspend () -> Unit) {
        busy = true
        err = ""
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e.message ?: ""
            } finally {
                busy = false
            }
        }
    }

    fun onPhone() {
        val v = Phone.validate(phoneIn)
        if (!v.ok) { err = v.msg; return }
        phone = v.phone
        launchBusy {
            user = Users.findByPhone(phone)
            val u = user
            if (u != null && u.isBlocked && !Phone.isAdminPhone(u.phone)) {
                err = "আপনার অ্যাকাউন্ট ব্লক করা হয়েছে" +
                    (u.blockedReason?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "") +
                    "। অ্যাডমিনের সাথে যোগাযোগ করুন।"
                return@launchBusy
            }
            if (u != null) { afterUserKnown(u); return@launchBusy }
            // new number: if this device already has an account, stop now (no OTP, no PIN)
            val owner = Users.findByDevice(Device.id())
            if (owner != null) { devStep(Block.USED, owner); return@launchBusy }
            startOtp()
        }
    }

    fun onResend() = launchBusy { startOtp() }

    fun onOtp() {
        val c = otp.trim()
        if (c.length != 6) { err = "৬ ডিজিটের কোড দিন"; return }
        launchBusy {
            if (!Otp.verify(phone, c)) { err = "দয়া করে সঠিক ওটিপি লিখুন"; return@launchBusy }
            val owner = Users.findByDevice(Device.id())
            if (owner != null) { devStep(Block.USED, owner); return@launchBusy }
            pinStep(PinMode.SETUP_NEW)
        }
    }

    private suspend fun finish(u: AppUser) {
        Session.set(u)
        ActivityLog.log("login", "", u.id, u.fullName)
        Nav.go(Routes.TASK, replace = true)
    }

    fun onPin() {
        if (pin.length != 4) { err = "৪ ডিজিটের PIN দিন"; return }
        if (mode != PinMode.LOGIN && pin != pin2) { err = "দুটি PIN মিলছে না"; return }
        val dev = Device.id()
        launchBusy {
            when (mode) {
                PinMode.LOGIN -> {
                    val u = user ?: return@launchBusy
                    if (!Pin.verify(u, pin)) { pin = ""; err = "PIN সঠিক নয়"; return@launchBusy }
                    if (Device.isEmpty(u.deviceId)) {
                        val other = Users.findByDevice(dev, u.id)
                        if (other != null) { devStep(Block.USED, other); return@launchBusy }
                        try {
                            Sb.patch("app_users?id=eq.${enc(u.id)}", jsonOf("device_id" to dev))
                        } catch (e: Exception) {
                            // the server's unique rule refused: another account got this device in the meantime
                            if (isDuplicate(e)) { devStep(Block.USED, Users.findByDevice(dev, u.id)); return@launchBusy }
                            throw e
                        }
                        u.deviceId = dev
                    }
                    finish(u)
                }
                PinMode.SETUP_EXISTING -> {
                    val u = user ?: return@launchBusy
                    val other = Users.findByDevice(dev, u.id)
                    if (other != null) { devStep(Block.USED, other); return@launchBusy }
                    val salt = Pin.newSalt()
                    val hash = Pin.hashPin(pin, salt)
                    try {
                        Sb.patch(
                            "app_users?id=eq.${enc(u.id)}",
                            jsonOf("password_salt" to salt, "password_hash" to hash, "device_id" to dev)
                        )
                    } catch (e: Exception) {
                        if (isDuplicate(e)) { devStep(Block.USED, Users.findByDevice(dev, u.id)); return@launchBusy }
                        throw e
                    }
                    u.deviceId = dev
                    finish(u)
                }
                PinMode.SETUP_NEW -> {
                    PendingSignup.set(phone, pin)
                    Nav.go(Routes.SIGNUP)
                }
            }
        }
    }

    fun onRetry() {
        launchBusy {
            if (blockReason == Block.BOUND) {
                val u = Users.findByPhone(phone).also { user = it }
                if (u == null) { err = "একাউন্ট খুঁজে পাওয়া যায়নি"; return@launchBusy }
                if (u.isBlocked && !Phone.isAdminPhone(u.phone)) { err = "আপনার অ্যাকাউন্ট ব্লক করা হয়েছে"; return@launchBusy }
                val st = u.deviceId
                if (Device.isEmpty(st) || Device.matches(st)) {
                    AppToast.show("ডিভাইস মিলেছে, এখন লগইন করুন")
                    pinStep(if (Pin.hasPin(u)) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                } else {
                    err = "এখনো অ্যাডমিন ডিভাইস রিসেট করেননি, একটু পর আবার চেষ্টা করুন"
                }
            } else if (Users.findByDevice(Device.id(), user?.id) != null) {
                err = "এই ডিভাইসে এখনো অন্য একাউন্ট বাঁধা আছে"
            } else {
                AppToast.show("ডিভাইস এখন খালি আছে")
                val u = user
                if (u != null) pinStep(if (Pin.hasPin(u)) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                else pinStep(PinMode.SETUP_NEW)
            }
        }
    }

    // "অন্য নাম্বার ব্যবহার করবেন?"
    fun onChange() {
        timer?.cancel()
        phone = ""
        user = null
        phoneIn = ""
        show(Step.PHONE)
    }

    fun waForgotPin() =
        "আসসালামু আলাইকুম, আমি কিতাব প্রুফিং অ্যাপে আমার PIN ভুলে গিয়েছি। আমার ফোন নাম্বার: $phone। দয়া করে সাহায্য করুন।"

    fun waDeviceReset() =
        "আসসালামু আলাইকুম, আমি কিতাব প্রুফিং অ্যাপে অন্য একটি ডিভাইস ব্যবহার করতে চাই। আমার ফোন নাম্বার: $phone। দয়া করে আমার একাউন্টের ডিভাইস রিসেট করে দিন।"
}

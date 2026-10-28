package com.srizwan.task.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.Cfg
import com.srizwan.task.core.Device
import com.srizwan.task.core.PendingSignup
import com.srizwan.task.core.Phone
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Pin
import com.srizwan.task.core.Sb
import com.srizwan.task.core.SbException
import com.srizwan.task.core.Session
import com.srizwan.task.core.Users
import com.srizwan.task.core.jsonOf
import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.BrandHeader
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.OkBadge
import com.srizwan.task.ui.PrimaryButton
import com.srizwan.task.ui.SegChoice
import com.srizwan.task.ui.Spinner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PM_LABEL = mapOf("bkash" to "বিকাশ", "nagad" to "নগদ")

/** signup.html -> Kotlin: after OTP/PIN verification create the profile (name + payment method and number). */
class SignupViewModel : ViewModel() {
    // { phone, pin } saved by the login step (React: sessionStorage kp_signup)
    val ctxPhone: String? = PendingSignup.phone
    private val ctxPin: String? = PendingSignup.pin

    var name by mutableStateOf("")
    var pm by mutableStateOf("bkash")
    var pnum by mutableStateOf("")
    var err by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    val valid: Boolean get() = !ctxPhone.isNullOrEmpty() && !ctxPin.isNullOrEmpty()

    fun submit() {
        val phone = ctxPhone ?: return
        val pin = ctxPin ?: return
        val nm = name.trim()
        val v = Phone.validate(pnum)
        if (nm.isEmpty()) { err = "পূর্ণ নাম দিন"; return }
        if (!v.ok) { err = (PM_LABEL[pm] ?: pm) + " নাম্বার: " + v.msg; return }
        busy = true
        err = ""
        viewModelScope.launch {
            try {
                val dev = Device.id()
                if (Users.findByDevice(dev) != null) {
                    throw SbException("এই ডিভাইসে ইতিমধ্যে একটি একাউন্ট যুক্ত আছে। সাহায্যের জন্য অ্যাডমিনের সাথে যোগাযোগ করুন: " + Cfg.ADMIN_PHONE)
                }
                val salt = Pin.newSalt()
                val hash = Pin.hashPin(pin, salt)
                val rows = Sb.post(
                    "app_users",
                    jsonOf(
                        "phone" to phone, "full_name" to nm, "payment_method" to pm, "payment_number" to v.phone,
                        "password_salt" to salt, "password_hash" to hash, "device_id" to dev
                    )
                )
                PendingSignup.clear()
                val u = AppUser(rows.getJSONObject(0))
                Session.set(u)
                ActivityLog.log("signup", "নতুন অ্যাকাউন্ট", u.id, nm)
                Nav.go(Routes.TASK, replace = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = e.message ?: ""
                err = if (Regex("duplicate|23505", RegexOption.IGNORE_CASE).containsMatchIn(m)) {
                    "এই নাম্বার বা ডিভাইসে ইতিমধ্যে একাউন্ট আছে"
                } else m
                busy = false
            }
        }
    }
}

@Composable
fun SignupScreen(vm: SignupViewModel = viewModel()) {
    val cs = MaterialTheme.colorScheme

    // no verified phone/PIN (e.g. app restarted) -> back to login
    LaunchedEffect(Unit) { if (!vm.valid) Nav.go(Routes.LOGIN, replace = true) }
    if (!vm.valid) return

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                BrandHeader(
                    title = "প্রোফাইল তৈরি করুন",
                    subtitle = "কাজ শুরু করতে আপনার তথ্য দিন",
                    icon = "user",
                    extra = { Box(Modifier.padding(top = 6.dp)) { OkBadge("যাচাইকৃত নাম্বার: " + vm.ctxPhone) } }
                )

                AppCard {
                    Text("ইউজার ডিটেইলস", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FieldLabel("পূর্ণ নাম *")
                    AppField(
                        value = vm.name, onValueChange = { vm.name = it },
                        placeholder = "আপনার নাম", imeAction = ImeAction.Next
                    )
                    FieldLabel("পেমেন্ট মাধ্যম *")
                    SegChoice(PM_LABEL.toList(), vm.pm, onSelect = { vm.pm = it })
                    FieldLabel((PM_LABEL[vm.pm] ?: vm.pm) + " নাম্বার *")
                    AppField(
                        value = vm.pnum, onValueChange = { vm.pnum = it },
                        placeholder = "01XXXXXXXXX", keyboardType = KeyboardType.Phone, maxLength = 18,
                        onAction = vm::submit
                    )
                    Column(Modifier.padding(top = 22.dp)) {
                        PrimaryButton("প্রোফাইল সাবমিট করুন", onClick = vm::submit, enabled = !vm.busy)
                    }
                    Spinner(vm.busy)
                    ErrText(vm.err)
                }

                Box(Modifier.padding(top = 14.dp, bottom = 20.dp)) {
                    HintText("আপনার তথ্য নিরাপদে সংরক্ষিত থাকবে", center = true, onBg = true)
                }
            }
        }
    }
}

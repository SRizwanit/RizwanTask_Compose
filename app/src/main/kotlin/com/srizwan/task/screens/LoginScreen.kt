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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.openWhatsApp
import com.srizwan.task.ui.AnekBangla
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.BlockButton
import com.srizwan.task.ui.BrandHeader
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.LinkButton
import com.srizwan.task.ui.PrimaryButton
import com.srizwan.task.ui.Spinner
import com.srizwan.task.screens.LoginViewModel.PinMode
import com.srizwan.task.screens.LoginViewModel.Step

private fun mmss(s: Int) = (s / 60).toString().padStart(2, '0') + ":" + (s % 60).toString().padStart(2, '0')
private fun digitsOnly(v: String) = v.filter { it.isDigit() }

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { vm.checkExistingSession() }

    val phoneFocus = remember { FocusRequester() }
    val otpFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }

    // when the step changes, focus the matching field (not on the first load)
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(vm.step, vm.mode) {
        if (firstRun) { firstRun = false; return@LaunchedEffect }
        try {
            when (vm.step) {
                Step.PHONE -> phoneFocus.requestFocus()
                Step.OTP -> otpFocus.requestFocus()
                Step.PIN -> pinFocus.requestFocus()
                Step.DEV -> Unit
            }
        } catch (e: IllegalStateException) {
            // the field is not on screen yet
        }
    }

    val setup = vm.mode != PinMode.LOGIN
    val pinLabel = when (vm.mode) {
        PinMode.LOGIN -> "৪ ডিজিটের PIN দিন"
        PinMode.SETUP_EXISTING -> "আপনার অ্যাকাউন্টের জন্য নতুন ৪ ডিজিটের PIN সেট করুন"
        PinMode.SETUP_NEW -> "আপনার নতুন একাউন্টের জন্য ৪ ডিজিটের PIN সেট করুন"
    }
    val pinBtn = when (vm.mode) {
        PinMode.LOGIN -> "লগইন করুন"
        PinMode.SETUP_EXISTING -> "PIN সেট করুন"
        PinMode.SETUP_NEW -> "পরবর্তী ধাপ"
    }
    val devMsg = if (vm.blockReason == LoginViewModel.Block.BOUND)
        "এই একাউন্টটি অন্য একটি ডিভাইসে যুক্ত আছে। অ্যাডমিন ডিভাইস রিসেট করে দিলে নিচের \u2018আবার চেষ্টা করুন\u2019 বাটনে চাপ দিন।"
    else
        "এই মোবাইলে ইতিমধ্যে অন্য একটি একাউন্ট ব্যবহার করা হচ্ছে। একটি ডিভাইসে একটি মাত্র একাউন্ট চালানো যায়।"

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                BrandHeader(title = "কিতাব প্রুফিং", subtitle = "মোবাইল নাম্বার দিয়ে লগইন করুন")

                AppCard {
                    when (vm.step) {
                        // STEP: phone
                        Step.PHONE -> Column {
                            FieldLabel("ফোন নাম্বার", first = true)
                            AppField(
                                value = vm.phoneIn, onValueChange = { vm.phoneIn = it },
                                placeholder = "017XXXXXXXX", keyboardType = KeyboardType.Phone, maxLength = 18,
                                onAction = vm::onPhone, focusRequester = phoneFocus
                            )
                            Column(Modifier.padding(top = 16.dp)) {
                                PrimaryButton("পরবর্তী", onClick = vm::onPhone, enabled = !vm.busy)
                            }
                        }
                        // STEP: otp
                        Step.OTP -> Column {
                            FieldLabel("৬ ডিজিটের OTP কোড দিন", first = true)
                            AppField(
                                value = vm.otp, onValueChange = { vm.otp = digitsOnly(it) },
                                placeholder = "------", keyboardType = KeyboardType.Number, maxLength = 6, pin = true,
                                onAction = vm::onOtp, focusRequester = otpFocus
                            )
                            Column(Modifier.padding(top = 16.dp)) {
                                PrimaryButton("ভেরিফাই করুন", onClick = vm::onOtp, enabled = !vm.busy)
                            }
                            LinkButton(
                                if (vm.left > 0) "OTP আবার পাঠান (" + mmss(vm.left) + ")" else "OTP আবার পাঠান",
                                onClick = vm::onResend, enabled = vm.left == 0
                            )
                        }
                        // STEP: pin
                        Step.PIN -> Column {
                            FieldLabel(pinLabel, first = true)
                            AppField(
                                value = vm.pin, onValueChange = { vm.pin = digitsOnly(it) },
                                placeholder = "----", keyboardType = KeyboardType.NumberPassword, maxLength = 4,
                                pin = true, password = true, onAction = vm::onPin, focusRequester = pinFocus
                            )
                            if (setup) {
                                FieldLabel("PIN আবার দিন")
                                AppField(
                                    value = vm.pin2, onValueChange = { vm.pin2 = digitsOnly(it) },
                                    placeholder = "----", keyboardType = KeyboardType.NumberPassword, maxLength = 4,
                                    pin = true, password = true, onAction = vm::onPin
                                )
                            }
                            Column(Modifier.padding(top = 16.dp)) {
                                PrimaryButton(pinBtn, onClick = vm::onPin, enabled = !vm.busy)
                            }
                            if (!setup) {
                                LinkButton(
                                    "PIN ভুলে গেছেন? অ্যাডমিনকে WhatsApp করুন",
                                    onClick = { openWhatsApp(ctx, vm.waForgotPin()) }
                                )
                            }
                        }
                        // STEP: device blocked
                        Step.DEV -> Column {
                            Text(
                                devMsg, color = cs.onSurface, fontSize = 14.sp, lineHeight = 24.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            )
                            Column(Modifier.padding(top = 16.dp)) {
                                BlockButton("আবার চেষ্টা করুন", onClick = vm::onRetry, enabled = !vm.busy)
                            }
                            Column(Modifier.padding(top = 10.dp)) {
                                PrimaryButton(
                                    "অ্যাডমিনকে WhatsApp এ জানান",
                                    onClick = { openWhatsApp(ctx, vm.waDeviceReset()) }, enabled = !vm.busy
                                )
                            }
                        }
                    }
                    Spinner(vm.busy)
                    ErrText(vm.err)
                    if (vm.step != Step.PHONE) {
                        LinkButton("অন্য নাম্বার ব্যবহার করবেন?", onClick = vm::onChange, muted = true)
                    }
                }

                Box(Modifier.padding(top = 16.dp, bottom = 20.dp)) {
                    HintText("নতুন ইউজার? OTP ভেরিফাই করার পর প্রোফাইল তৈরি করুন", center = true)
                }
            }
        }
    }

    // OTP dialog (`#otpDlg`)
    if (vm.showOtpDialog) {
        AlertDialog(
            onDismissRequest = { vm.showOtpDialog = false },
            title = { Text("আপনার OTP কোড", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        vm.otpCode,
                        color = cs.primary, fontFamily = AnekBangla, fontWeight = FontWeight.Bold,
                        fontSize = 32.sp, letterSpacing = 19.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                    HintText("২ মিনিটের মধ্যে কোডটি লিখুন", center = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showOtpDialog = false }) { Text("ঠিক আছে", fontWeight = FontWeight.Bold) }
            }
        )
    }
}

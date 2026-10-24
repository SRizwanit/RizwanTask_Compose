package com.srizwan.task

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * New account form:
 * name, address, bKash / Nagad number, mobile number, PIN.
 * After success the Task page opens.
 */
@Composable
fun SignupScreen(vm: AuthViewModel) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current

    var fullName by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var payMethod by rememberSaveable { mutableStateOf("bkash") }
    var payNumber by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf(vm.lastPhone) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }

    BackHandler { vm.openLogin() }

    fun submit() {
        focus.clearFocus()
        vm.signup(fullName, address, payMethod, payNumber, phone, pin, confirmPin)
    }

    RizwanBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            LogoBadge(size = 88)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "নতুন একাউন্ট",
                color = colors.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "আপনার তথ্য দিয়ে একাউন্ট তৈরি করুন",
                color = colors.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            vm.signupNotice?.let { notice ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(14.dp),
                        color = colors.onSurface,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AuthCard {
                AuthField(
                    value = fullName,
                    onValueChange = {
                        fullName = it.take(60)
                        vm.clearMessage()
                    },
                    label = "আপনার নাম",
                    enabled = !vm.loading
                )

                AuthField(
                    value = address,
                    onValueChange = {
                        address = it.take(150)
                        vm.clearMessage()
                    },
                    label = "ঠিকানা",
                    enabled = !vm.loading
                )

                Text(
                    text = "পেমেন্ট মাধ্যম",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = payMethod == "bkash",
                        onClick = { payMethod = "bkash" },
                        label = { Text("বিকাশ") },
                        enabled = !vm.loading
                    )
                    FilterChip(
                        selected = payMethod == "nagad",
                        onClick = { payMethod = "nagad" },
                        label = { Text("নগদ") },
                        enabled = !vm.loading
                    )
                }

                AuthField(
                    value = payNumber,
                    onValueChange = {
                        payNumber = it.filter(Char::isDigit).take(PHONE_LENGTH)
                        vm.clearMessage()
                    },
                    label = if (payMethod == "bkash") "বিকাশ নাম্বার" else "নগদ নাম্বার",
                    keyboardType = KeyboardType.Phone,
                    enabled = !vm.loading
                )

                AuthField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter(Char::isDigit).take(PHONE_LENGTH)
                        vm.clearMessage()
                    },
                    label = "মোবাইল নাম্বার (লগইনের জন্য)",
                    keyboardType = KeyboardType.Phone,
                    enabled = !vm.loading
                )

                AuthField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(PIN_LENGTH)
                        vm.clearMessage()
                    },
                    label = "পিন ($PIN_LENGTH ডিজিট)",
                    keyboardType = KeyboardType.NumberPassword,
                    isPassword = true,
                    enabled = !vm.loading
                )

                AuthField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it.filter(Char::isDigit).take(PIN_LENGTH)
                        vm.clearMessage()
                    },
                    label = "পিন আবার লিখুন",
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    isPassword = true,
                    enabled = !vm.loading,
                    onDone = { submit() }
                )

                vm.message?.let { ErrorText(it) }

                PrimaryButton(
                    text = "একাউন্ট তৈরি করুন",
                    loading = vm.loading,
                    onClick = { submit() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "আগে থেকেই একাউন্ট আছে?",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = { vm.openLogin() }) {
                        Text("লগইন", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    vm.helpDialog?.let { state ->
        AdminHelpDialog(
            state = state,
            userPhone = phone,
            onDismiss = { vm.dismissHelp() }
        )
    }
}

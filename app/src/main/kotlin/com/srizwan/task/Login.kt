package com.srizwan.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
 * Login with mobile number + PIN.
 *  - new number  -> Signup opens automatically
 *  - wrong / forgotten PIN -> custom dialog (WhatsApp admin)
 */
@Composable
fun LoginScreen(vm: AuthViewModel) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current

    // if this phone already has an account, only that number can log in here
    val lockedPhone = vm.boundPhone

    var typedPhone by rememberSaveable { mutableStateOf(vm.lastPhone) }
    var pin by rememberSaveable { mutableStateOf("") }
    val phone = lockedPhone ?: typedPhone

    fun submit() {
        focus.clearFocus()
        vm.login(phone, pin)
    }

    RizwanBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // status bar + navigation bar + keyboard in one - nothing gets cut
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            LogoBadge()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "স্বাগতম",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "মোবাইল নাম্বার ও পিন দিয়ে লগইন করুন",
                color = colors.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            AuthCard {
                AuthField(
                    value = phone,
                    onValueChange = {
                        typedPhone = it.filter(Char::isDigit).take(PHONE_LENGTH)
                        vm.clearMessage()
                    },
                    label = "মোবাইল নাম্বার",
                    keyboardType = KeyboardType.Phone,
                    enabled = !vm.loading,
                    readOnly = lockedPhone != null
                )

                if (lockedPhone != null) {
                    Text(
                        text = "এই মোবাইলে শুধু এই নাম্বারের একাউন্টেই লগইন করা যাবে।",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                AuthField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(PIN_LENGTH)
                        vm.clearMessage()
                    },
                    label = "পিন ($PIN_LENGTH ডিজিট)",
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    isPassword = true,
                    enabled = !vm.loading,
                    onDone = { submit() }
                )

                vm.message?.let { ErrorText(it) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { vm.showForgotPin(phone) }) {
                        Text("পিন ভুলে গেছেন?")
                    }
                }

                PrimaryButton(
                    text = "লগইন",
                    loading = vm.loading,
                    onClick = { submit() }
                )

                // a phone that already has an account can not open another one
                if (lockedPhone == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "নতুন ইউজার?",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = { vm.openSignup(phone, pin) }) {
                            Text("একাউন্ট খুলুন", fontWeight = FontWeight.SemiBold)
                        }
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

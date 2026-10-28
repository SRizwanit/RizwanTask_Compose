package com.srizwan.task.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Pin
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.openUrl
import com.srizwan.task.nav.Nav
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.OutlineButton
import com.srizwan.task.ui.errColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Privacy policy page of the app (shown in the browser). */
const val PRIVACY_POLICY_URL = "https://rizwantask.vercel.app/privacypolicy"

/**
 * "অ্যাকাউন্ট মুছুন" screen (opened from the Profile page, route `deleteaccount`).
 *
 * The user reads what is lost, types the 4-digit PIN (checked against the fresh row from the server, [Pin.verify]) and confirms in a last
 * dialog. Then the `app_users` row is deleted ([Sb.del], the same call the admin's user delete uses), the action is logged
 * (`account_deleted`), the session is cleared and the login screen opens. The admin account cannot be deleted from here.
 * The privacy policy link opens in the browser.
 */
@Composable
fun DeleteAccountScreen() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<AppUser?>(null) }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = !busy) { Nav.go("profile") }
    LaunchedEffect(Unit) { me = Auth.requireAuth() }

    val isAdmin = me?.let { Phone.isAdminPhone(it.phone) } == true

    /** Step 1: check the PIN, then ask the final question. */
    fun start() {
        val u = me ?: return
        if (busy) return
        if (pin.length != 4) { err = "৪ ডিজিটের PIN দিন"; return }
        if (!Pin.verify(u, pin)) { pin = ""; err = "PIN সঠিক নয়"; return }
        err = ""
        confirmOpen = true
    }

    /** Step 2 (after "হ্যাঁ, মুছুন"): delete the row, log, sign out. */
    fun doDelete() {
        val u = me ?: return
        confirmOpen = false
        busy = true
        scope.launch {
            try {
                ActivityLog.log("account_deleted", u.fullName.orEmpty() + " " + u.phone.orEmpty(), whoId = u.id, whoName = u.fullName.orEmpty())
                Sb.del("app_users?id=eq.${enc(u.id)}")
                AppToast.show("আপনার অ্যাকাউন্ট মুছে ফেলা হয়েছে")
                Auth.logout()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                err = e.message ?: "অ্যাকাউন্ট মুছা যায়নি"
            } finally { busy = false }
        }
    }

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // header: back + title
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(cs.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, cs.outline.copy(alpha = 0.6f), CircleShape)
                        .clickable(enabled = !busy) { Nav.go("profile") },
                    contentAlignment = Alignment.Center
                ) { AppIcon("left", size = 18.dp, tint = cs.onSurface) }
                com.srizwan.task.screens.task.HeaderTitle("অ্যাকাউন্ট মুছুন", Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 440.dp).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(errColor().copy(alpha = 0.14f))
                                    .border(1.dp, errColor().copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { AppIcon("trash", size = 28.dp, tint = errColor()) }
                            Text(
                                "আপনার অ্যাকাউন্ট স্থায়ীভাবে মুছে যাবে", color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                            )
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Point("অ্যাকাউন্ট ও লগইন তথ্য (নাম, মোবাইল নাম্বার, PIN, পেমেন্ট নাম্বার) মুছে যাবে।")
                                Point("এই কাজ ফেরানো যাবে না — আবার আসতে চাইলে নতুন করে সাইন আপ করতে হবে।")
                                Point("উত্তোলন না করা পয়েন্টস থাকলে আগে অ্যাডমিনের সাথে যোগাযোগ করে নিন।")
                            }
                            if (me != null && isAdmin) {
                                Text(
                                    "অ্যাডমিন অ্যাকাউন্ট এখান থেকে মুছা যাবে না।", color = errColor(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp)
                                )
                            } else {
                                FieldLabel("নিশ্চিত করতে আপনার PIN দিন", Modifier.padding(top = 4.dp))
                                AppField(
                                    value = pin,
                                    onValueChange = { pin = it.filter { c -> c.isDigit() }; err = "" },
                                    placeholder = "••••",
                                    keyboardType = KeyboardType.NumberPassword,
                                    maxLength = 4,
                                    imeAction = ImeAction.Done,
                                    onAction = { start() },
                                    pin = true,
                                    password = true,
                                    enabled = !busy && me != null
                                )
                                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                OutlineButton(
                                    if (busy) "মুছে ফেলা হচ্ছে…" else "অ্যাকাউন্ট মুছুন", onClick = { start() }, color = errColor(),
                                    modifier = Modifier.padding(top = 16.dp), enabled = !busy && me != null, icon = "trash"
                                )
                            }
                            OutlineButton("বাতিল — প্রোফাইলে ফিরুন", onClick = { Nav.go("profile") }, color = cs.primary, modifier = Modifier.padding(top = 10.dp), enabled = !busy, icon = "left")
                            HintText("আমরা কীভাবে আপনার তথ্য ব্যবহার করি জানতে প্রাইভেসি পলিসি দেখুন।", Modifier.padding(top = 14.dp), center = true)
                            OutlineButton("প্রাইভেসি পলিসি", onClick = { openUrl(ctx, PRIVACY_POLICY_URL) }, color = cs.primary, modifier = Modifier.padding(top = 8.dp), icon = "shield")
                        }
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("সত্যিই মুছে ফেলবেন?", fontWeight = FontWeight.Bold) },
            text = { Text("আপনার অ্যাকাউন্ট স্থায়ীভাবে মুছে যাবে। এটি ফেরানো যাবে না।") },
            confirmButton = { TextButton(onClick = { doDelete() }) { Text("হ্যাঁ, মুছুন", color = errColor(), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("না") } }
        )
    }
}

@Composable
private fun Point(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(errColor()))
        Text(text, color = cs.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

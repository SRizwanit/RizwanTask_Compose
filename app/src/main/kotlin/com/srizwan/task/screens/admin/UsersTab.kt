package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Device
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Pin
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.BlockButton
import com.srizwan.task.ui.errColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/** React admin/UsersTab.jsx — search, stats, jumps to review / notice / log, PIN + device reset, block / unblock, delete. */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserStats(vm: AdminViewModel, u: JSONObject) {
    val id = u.idStr()
    val sm = vm.userSum(id)
    val x = vm.pst[id]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val m = Modifier.widthIn(min = 105.dp)
        Tile(Fmt.bn(sm.pages), "প্রুফড পেইজ", m, small = true)
        Tile(Fmt.bn(sm.approved), "অনুমোদিত", m, small = true)
        Tile(Fmt.bn(sm.pending), "রিভিউ বাকি", m, small = true)
        Tile(Fmt.bn(sm.correction), "সংশোধন", m, small = true)
        Tile(Fmt.money(sm.earned), "মোট আয়", m, small = true)
        Tile(Fmt.money(sm.available), "উত্তোলনযোগ্য", m, small = true)
        Tile(Fmt.money(sm.paid), "পরিশোধিত", m, small = true)
        Tile(Fmt.bn(x?.today ?: 0) + " / " + Fmt.bn(x?.w7 ?: 0), "আজ / ৭ দিন", m, small = true)
    }
}

/** PIN reset dialog: clear the PIN (user sets a new one on next login) or set a new 4-digit PIN directly. */
@Composable
private fun PinDialog(target: JSONObject?, onClose: () -> Unit, onDone: () -> Unit) {
    val t = target ?: return
    var pin by remember(t) { mutableStateOf("") }
    var err by remember(t) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val name = t.str("full_name").orEmpty()

    fun clear() = scope.launch {
        try {
            Sb.patch("app_users?id=eq.${enc(t.idStr())}", jsonOf("password_salt" to null, "password_hash" to null))
            AppToast.show("PIN মুছে ফেলা হয়েছে")
            ActivityLog.log("admin_pin", "$name (মুছে ফেলা)"); onDone(); onClose()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { err = e.message.orEmpty() }
    }

    fun set() = scope.launch {
        if (pin.length != 4) { err = "৪ ডিজিটের PIN দিন"; return@launch }
        try {
            val s = Pin.newSalt()
            val h = Pin.hashPin(pin, s)
            Sb.patch("app_users?id=eq.${enc(t.idStr())}", jsonOf("password_salt" to s, "password_hash" to h))
            AppToast.show("নতুন PIN সেট হয়েছে")
            ActivityLog.log("admin_pin", "$name (নতুন PIN)"); onDone(); onClose()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { err = e.message.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("PIN রিসেট", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$name (${t.str("phone").orEmpty()})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                BlockButton("PIN মুছুন (ইউজার পরের লগইনে নতুন PIN দেবে)", onClick = { clear() })
                Text("অথবা সরাসরি নতুন ৪ ডিজিটের PIN দিন", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                AppField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit); err = "" },
                    placeholder = "----", keyboardType = KeyboardType.NumberPassword, maxLength = 4, pin = true,
                    onAction = { set() }
                )
                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { set() }) { Text("নতুন PIN সেট") } },
        dismissButton = { TextButton(onClick = onClose) { Text("বন্ধ") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsersTab(vm: AdminViewModel, me: AppUser, ask: AskController, openKey: Int) {
    var q by rememberSaveable { mutableStateOf("") }
    var pinTarget by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    // A tab re-fetches its data each time it is opened (React openKey).
    LaunchedEffect(openKey) { if (openKey > 0) { vm.loadUsers(); vm.ensureMoney() } }

    val ql = q.trim().lowercase()
    val list = vm.users.filter {
        ql.isEmpty() || it.str("full_name").orEmpty().lowercase().contains(ql) || it.str("phone").orEmpty().contains(ql)
    }

    /** Runs a server write on the app scope (so a tab switch does not cancel it); errors become a toast via [mapErr]. */
    fun serverCall(mapErr: (String?) -> String = { it.orEmpty() }, block: suspend () -> Unit) {
        AppScope.scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppToast.show(mapErr(e.message), true) }
        }
    }

    fun toggleDevice(u: JSONObject) = scope.launch {
        val n = u.str("full_name").orEmpty()
        if (!ask.confirm("$n এর ডিভাইস রিসেট করবেন? ইউজার অন্য ফোনে লগইন করতে পারবে।")) return@launch
        serverCall {
            Sb.patch("app_users?id=eq.${enc(u.idStr())}", jsonOf("device_id" to null))
            AppToast.show("ডিভাইস রিসেট হয়েছে"); ActivityLog.log("admin_device", n); vm.loadUsers()
        }
    }

    fun toggleBlock(u: JSONObject) = scope.launch {
        val n = u.str("full_name").orEmpty()
        if (u.optBoolean("is_blocked", false)) {
            if (!ask.confirm("$n কে আনব্লক করবেন?")) return@launch
            serverCall(mapErr = ::blkErr) {
                Sb.patch("app_users?id=eq.${enc(u.idStr())}", jsonOf("is_blocked" to false, "blocked_reason" to null))
                AppToast.show("আনব্লক করা হয়েছে"); ActivityLog.log("admin_unblock", n); vm.loadUsers()
            }
        } else {
            val why = ask.ask(title = "$n কে ব্লক করুন", label = "ব্লকের কারণ (ইউজার দেখতে পাবে)", ok = "ব্লক করুন") ?: return@launch
            serverCall(mapErr = ::blkErr) {
                Sb.patch("app_users?id=eq.${enc(u.idStr())}", jsonOf("is_blocked" to true, "blocked_reason" to why.ifEmpty { null }))
                AppToast.show("ব্লক করা হয়েছে"); ActivityLog.log("admin_block", n + if (why.isNotEmpty()) " • $why" else ""); vm.loadUsers()
            }
        }
    }

    fun deleteUser(u: JSONObject) = scope.launch {
        val n = u.str("full_name").orEmpty()
        if (!ask.confirm("$n কে মুছে ফেলবেন? এটি ফেরানো যাবে না।", ok = "মুছুন")) return@launch
        serverCall {
            Sb.del("app_users?id=eq.${enc(u.idStr())}")
            AppToast.show("ইউজার ডিলিট হয়েছে"); ActivityLog.log("admin_delete_user", n); vm.loadUsers()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppField(value = q, onValueChange = { q = it }, modifier = Modifier.weight(1f), placeholder = "নাম বা নাম্বার দিয়ে খুঁজুন")
            Badge("মোট ${Fmt.bn(vm.users.size)} জন")
            SmBtn("", "refresh") { scope.launch { vm.loadUsers() } }
        }

        if (list.isEmpty()) Empty("কোনো ইউজার নেই")

        list.forEach { u ->
            val id = u.idStr()
            val bound = !Device.isEmpty(u.str("device_id"))
            val self = id == me.id
            val adm = Phone.isAdminPhone(u.str("phone"))
            val blocked = u.optBoolean("is_blocked", false)
            val la = vm.lastActive(u)
            val hasPin = Pin.hasPin(AppUser(u))

            LiCard {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(u.str("full_name").orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (adm) Badge("অ্যাডমিন", "ok")
                    if (blocked) Badge("ব্লকড", "bad")
                    Badge(if (bound) "ডিভাইস যুক্ত" else "ডিভাইস খালি", if (bound) "ok" else "warn")
                    Badge(if (hasPin) "PIN আছে" else "PIN নেই", if (hasPin) "" else "warn")
                }
                val meta = buildString {
                    append("মোবাইল: ${u.str("phone").orEmpty()}\n")
                    append("${PM[u.str("payment_method")] ?: "পেমেন্ট"}: ${u.str("payment_number")?.takeIf { it.isNotEmpty() } ?: "-"}\n")
                    append("যোগ দিয়েছেন: ${if (u.str("created_at") != null) fmtFull(u.str("created_at")) else "-"}\n")
                    append("সর্বশেষ সক্রিয়: ${Fmt.fmtDT(if (la > 0) Instant.ofEpochMilli(la).toString() else null)}")
                    if (blocked && !u.str("blocked_reason").isNullOrEmpty()) append("\nব্লকের কারণ: ${u.str("blocked_reason")}")
                }
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)

                if (vm.proofsLoaded) UserStats(vm, u)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmBtn("রিভিউ", "check") { vm.reviewUser(id, false) }
                    SmBtn("বার্তা", "bell") { vm.messageUser(id) }
                    SmBtn("লগ", "list") { vm.logUser(id) }
                    SmBtn("PIN রিসেট", "key") { pinTarget = u }
                    SmBtn("ডিভাইস রিসেট", "device") { toggleDevice(u) }
                    if (!self && !adm) SmBtn(if (blocked) "আনব্লক" else "ব্লক", "ban", tone = "warn") { toggleBlock(u) }
                    if (!self) SmBtn("ডিলিট", "trash", tone = "bad") { deleteUser(u) }
                }
            }
        }
    }

    PinDialog(pinTarget, onClose = { pinTarget = null }, onDone = { scope.launch { vm.loadUsers() } })
}

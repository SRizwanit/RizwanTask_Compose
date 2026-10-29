package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.PagerBar
import com.srizwan.task.ui.PrimaryButton
import com.srizwan.task.ui.pageSlice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** React admin/NoticeTab.jsx — send a notice to everyone or one user + the list of sent notices. */
@Composable
fun NoticeTab(vm: AdminViewModel, ask: AskController, openKey: Int) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var listErr by remember { mutableStateOf("") }
    val titleFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val to = vm.noticeTo

    suspend fun load() {
        try {
            list = Sb.get("notices?order=created_at.desc&limit=200").objects(); listErr = ""
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            list = null
            listErr = if (noTbl(e)) "notices টেবিল নেই — $SQLHINT" else e.message.orEmpty()
        }
    }

    LaunchedEffect(openKey) { if (openKey > 0) load() }
    // "বার্তা" button on the Users tab: jump here and focus the title
    LaunchedEffect(Unit) {
        if (vm.takeNoticeFocus()) {
            androidx.compose.runtime.withFrameNanos { }   // wait until the field is laid out
            runCatching { titleFocus.requestFocus() }
        }
    }

    fun send() {
        scope.launch {
            val t = title.trim()
            val b = body.trim()
            if (t.isEmpty()) { err = "শিরোনাম লিখুন"; return@launch }
            err = ""
            if (to.isEmpty() && !ask.confirm("এই ঘোষণা সব ইউজারের কাছে যাবে। পাঠাবেন?", ok = "পাঠান")) return@launch
            sending = true
            val target = to
            AppScope.scope.launch {
                try {
                    sendNotice(target.ifEmpty { null }, t, b, "notice")
                    title = ""; body = ""
                    AppToast.show("নোটিশ পাঠানো হয়েছে")
                    ActivityLog.log("admin_notice", (if (target.isNotEmpty()) vm.uname(target) else "সবাই") + " • " + t)
                    load()
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    err = if (noTbl(e)) "notices টেবিল নেই — $SQLHINT" else e.message.orEmpty()
                } finally { sending = false }
            }
        }
    }

    fun del(n: JSONObject) {
        scope.launch {
            if (!ask.confirm("এই নোটিশ মুছে ফেলবেন?", ok = "মুছুন")) return@launch
            AppScope.scope.launch {
                try {
                    Sb.del("notices?id=eq.${enc(n.idStr())}")
                    AppToast.show("মুছে ফেলা হয়েছে"); load()
                } catch (e: CancellationException) { throw e } catch (e: Exception) { AppToast.show(e.message.orEmpty(), true) }
            }
        }
    }

    val userOptions = listOf("" to "সবাই (ঘোষণা)") + vm.users.map { it.idStr() to "${it.str("full_name").orEmpty()} (${it.str("phone").orEmpty()})" }

    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        AppCard {
            Text("নতুন ঘোষণা / নোটিশ পাঠান", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            HintText("ইউজারের Task পেইজে একটি ডায়ালগ হয়ে আসবে (একবার দেখানোর পর আর আসবে না; প্রোফাইলে \"নোটিশ ও ঘোষণা\" থেকে আবার দেখা যায়)।")
            FieldLabel("কাকে")
            AdminSelect(to, userOptions, onPick = { vm.updateNoticeTo(it) })
            FieldLabel("শিরোনাম *")
            AppField(value = title, onValueChange = { title = it }, maxLength = 120, focusRequester = titleFocus)
            FieldLabel("বিস্তারিত")
            AdminTextArea(body, { body = it }, rows = 5)
            if (err.isNotEmpty()) Column(Modifier.padding(top = 6.dp)) { ErrText(err) }
            Column(Modifier.padding(top = 12.dp)) { PrimaryButton("পাঠান", onClick = { send() }, enabled = !sending) }
        }

        Text(
            "পাঠানো নোটিশ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        val l = list
        var page by remember { mutableStateOf(0) }
        when {
            listErr.isNotEmpty() -> NoticeBox(listErr)
            l == null -> {}
            l.isEmpty() -> Empty("এখনো কোনো নোটিশ পাঠানো হয়নি")
            else -> l.pageSlice(page).forEach { n ->
                val uid = n.str("user_id")?.takeIf { it.isNotEmpty() }
                LiCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n.str("title").orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Badge(if (uid != null) vm.uname(uid) else "সবাই", if (uid != null) "warn" else "ok")
                    }
                    val bd = n.str("body").orEmpty()
                    if (bd.isNotEmpty()) Text(bd, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Fmt.fmtDT(n.str("created_at"))} • ${n.str("kind")?.takeIf { it.isNotEmpty() } ?: "notice"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f)
                        )
                        SmBtn("মুছুন", "trash", tone = "bad") { del(n) }
                    }
                }
            }
        }
        if (l != null && l.isNotEmpty()) PagerBar(page = page, total = l.size, onPage = { page = it })
    }
}

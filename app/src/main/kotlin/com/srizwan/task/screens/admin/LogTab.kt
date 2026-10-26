package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import com.srizwan.task.ui.BlockButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val PAGE = 100

/** React admin/LogTab.jsx — activity log filtered by user and action, 100 rows at a time with "আরও দেখান". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogTab(vm: AdminViewModel, openKey: Int) {
    val filter = vm.logFilter
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(offset: Int, user: String, act: String): List<JSONObject>? = try {
        Sb.get(
            "activity_log?select=*" + (if (user.isNotEmpty()) "&user_id=eq.${enc(user)}" else "") +
                (if (act.isNotEmpty()) "&action=eq.${enc(act)}" else "") +
                "&order=created_at.desc,id.desc&limit=$PAGE&offset=$offset"
        ).objects()
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        err = if (noTbl(e)) "activity_log টেবিল নেই — $SQLHINT" else e.message.orEmpty()
        more = false
        null
    }

    // Opening the tab or changing a filter reloads from the start; a newer change cancels an older load.
    LaunchedEffect(openKey, filter.user, filter.act) {
        if (openKey > 0) {
            busy = true
            try {
                val r = fetch(0, filter.user, filter.act)
                if (r != null) { rows = r; more = r.size == PAGE; err = "" } else rows = emptyList()
            } finally { busy = false }
        }
    }

    fun reload() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val r = fetch(0, filter.user, filter.act)
                if (r != null) { rows = r; more = r.size == PAGE; err = "" } else rows = emptyList()
            } finally { busy = false }
        }
    }

    fun loadMore() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val r = fetch(rows.size, filter.user, filter.act)
                if (r != null) { rows = rows + r; more = r.size == PAGE; err = "" }
            } finally { busy = false }
        }
    }

    val userOptions = listOf("" to "সব ইউজার") + vm.users.map { it.idStr() to "${it.str("full_name").orEmpty()} (${it.str("phone").orEmpty()})" }
    val actOptions = listOf("" to "সব কাজ") + ACT.map { it.key to it.value }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminSelect(filter.user, userOptions, onPick = { vm.updateLogFilter(filter.copy(user = it)) })
            AdminSelect(filter.act, actOptions, onPick = { vm.updateLogFilter(filter.copy(act = it)) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(Fmt.bn(rows.size) + (if (more) "+" else "") + " টি")
                SmBtn("", "refresh") { reload() }
            }
        }

        if (err.isNotEmpty()) {
            NoticeBox(err)
        } else if (rows.isEmpty()) {
            Empty("কোনো লগ নেই", pad = true)
        } else {
            rows.forEach { x ->
                val action = x.str("action").orEmpty()
                LiCard {
                    FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Badge(ACT[action] ?: action, if (action.startsWith("admin_")) "gold" else "")
                        Text(
                            x.str("user_name")?.takeIf { it.isNotEmpty() } ?: vm.uname(x.str("user_id")),
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp
                        )
                    }
                    val d = x.str("detail").orEmpty()
                    if (d.isNotEmpty()) Text(d, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)
                    Text(Fmt.fmtDT(x.str("created_at")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
        }
        if (more && err.isEmpty()) BlockButton("আরও দেখান", onClick = { loadMore() }, modifier = Modifier.padding(top = 2.dp))
    }
}

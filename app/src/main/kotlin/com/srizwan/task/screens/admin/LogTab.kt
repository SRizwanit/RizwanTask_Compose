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
import com.srizwan.task.ui.PAGE_SIZE
import com.srizwan.task.ui.PagerBarOpen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** React admin/LogTab.jsx — activity log filtered by user and action, 20 rows per page (server-side paging with a vector page bar). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogTab(vm: AdminViewModel, openKey: Int) {
    val filter = vm.logFilter
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 0-based page; a new user / action filter starts again from page 1
    var page by remember(filter.user, filter.act) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    /** One page from the server: PAGE_SIZE + 1 rows are asked for, so the extra row tells whether a next page exists. */
    suspend fun fetch(p: Int, user: String, act: String): List<JSONObject>? = try {
        Sb.get(
            "activity_log?select=*" + (if (user.isNotEmpty()) "&user_id=eq.${enc(user)}" else "") +
                (if (act.isNotEmpty()) "&action=eq.${enc(act)}" else "") +
                "&order=created_at.desc,id.desc&limit=${PAGE_SIZE + 1}&offset=${p * PAGE_SIZE}"
        ).objects()
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        err = if (noTbl(e)) "activity_log টেবিল নেই — $SQLHINT" else e.message.orEmpty()
        more = false
        null
    }

    suspend fun loadPage(p: Int) {
        busy = true
        try {
            val r = fetch(p, filter.user, filter.act)
            if (r != null) { rows = r.take(PAGE_SIZE); more = r.size > PAGE_SIZE; err = "" } else rows = emptyList()
        } finally { busy = false }
    }

    // Opening the tab, changing a filter or turning the page loads that page; a newer change cancels an older load.
    LaunchedEffect(openKey, filter.user, filter.act, page) { if (openKey > 0) loadPage(page) }

    fun reload() {
        if (busy) return
        scope.launch { loadPage(page) }
    }

    val userOptions = listOf("" to "সব ইউজার") + vm.users.map { it.idStr() to "${it.str("full_name").orEmpty()} (${it.str("phone").orEmpty()})" }
    val actOptions = listOf("" to "সব কাজ") + ACT.map { it.key to it.value }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminSelect(filter.user, userOptions, onPick = { vm.updateLogFilter(filter.copy(user = it)) })
            AdminSelect(filter.act, actOptions, onPick = { vm.updateLogFilter(filter.copy(act = it)) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("পেইজ ${Fmt.bn(page + 1)} • ${Fmt.bn(rows.size)}টি")
                SmBtn("", "refresh") { reload() }
            }
        }

        if (err.isNotEmpty()) {
            NoticeBox(err)
        } else if (rows.isEmpty()) {
            Empty("কোনো লগ নেই", pad = true, art = "list")
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
        if (err.isEmpty()) PagerBarOpen(page = page, hasNext = more, onPage = { page = it }, busy = busy)
    }
}

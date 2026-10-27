package com.srizwan.task.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Fmt
import com.srizwan.task.nav.Nav
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.ThemeToggleButton
import com.srizwan.task.ui.errColor

/** (key, icon, label, badge?) — React TABS. The "Supp" tab (Firebase tickets) is not converted: Support is a WebView now. */
private class AdminTab(val key: String, val icon: String, val label: String, val badge: String? = null, val step: String)

private val TABS = listOf(
    AdminTab("Dash", "chart", "ড্যাশবোর্ড", step = "২-ঞ"),
    AdminTab("Users", "users", "ব্যবহারকারী", step = "২-গ"),
    AdminTab("Review", "check", "রিভিউ", badge = "review", step = "২-ছ"),
    AdminTab("Pay", "points", "পেমেন্ট", badge = "pay", step = "২-চ"),
    AdminTab("Books", "book", "কিতাব", step = "২-জ"),
    AdminTab("Notice", "bell", "নোটিশ", step = "২-ঙ"),
    AdminTab("Log", "list", "লগ", step = "২-ঙ"),
    AdminTab("Set", "settings", "সেটিংস", step = "২-ঘ")
)

@Composable
fun AdminScreen(vm: AdminViewModel = viewModel()) {
    // Guard: session + device + not blocked + admin phone (requireAuth({ admin: true }))
    var me by remember { mutableStateOf<AppUser?>(null) }
    LaunchedEffect(Unit) { me = Auth.requireAuth(admin = true) }
    LaunchedEffect(me) { if (me != null) vm.start() }

    val ask = rememberAskController()

    val badges = mapOf(
        "review" to vm.proofs.count { status(it) == "pending" },
        "pay" to vm.pays.count { it.optString("status") == "requested" }
    )

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            AdminHeader()
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                AdminTabBar(vm, badges)
            }
            if (me != null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                    // key(tab): a fresh scroll position and fresh tab state each time a tab is opened (React openKey).
                    androidx.compose.runtime.key(vm.tab) {
                        Column(
                            Modifier.widthIn(max = 980.dp).fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, end = 14.dp, bottom = 24.dp)
                        ) {
                            val u = me
                            when {
                                vm.tab == "Dash" -> DashTab(vm, ask, vm.openKey("Dash"), onReviewUser = { vm.reviewUser(it, true) })
                                vm.tab == "Users" && u != null -> UsersTab(vm, u, ask, vm.openKey("Users"))
                                vm.tab == "Set" -> SettingsTab(vm, vm.openKey("Set"))
                                vm.tab == "Notice" -> NoticeTab(vm, ask, vm.openKey("Notice"))
                                vm.tab == "Log" -> LogTab(vm, vm.openKey("Log"))
                                vm.tab == "Pay" -> PayTab(vm, ask, vm.openKey("Pay"))
                                vm.tab == "Review" -> ReviewTab(vm, ask, vm.openKey("Review"))
                                vm.tab == "Books" -> BooksTab(vm, ask, vm.openKey("Books"))
                                else -> TabContent(vm, vm.tab)
                            }
                        }
                    }
                }
            }
        }
        AskDialog(ask)
    }
}

@Composable
private fun AdminHeader() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallBtn("টাস্ক", "left") { Nav.go("task") }
        Text(
            "অ্যাডমিন প্যানেল", color = cs.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), maxLines = 1
        )
        ThemeToggleButton()
        SmallBtn("লগআউট", "logout") { Auth.logout() }
    }
}

@Composable
private fun SmallBtn(text: String, icon: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AppIcon(icon, size = 16.dp, tint = cs.onSurface)
        Text(text, color = cs.onSurface, fontSize = 13.sp)
    }
}

/** `.tabs`: horizontally scrollable tab buttons; red badge for pending reviews / payment requests (0 = hidden, >99 = ৯৯+). */
@Composable
private fun AdminTabBar(vm: AdminViewModel, badges: Map<String, Int>) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TABS.forEach { t ->
            val on = vm.tab == t.key
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier.clip(shape)
                    .then(
                        if (on) Modifier.background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
                        else Modifier.background(cs.surfaceVariant.copy(alpha = 0.55f))
                    )
                    .border(1.dp, if (on) cs.primary else cs.outline.copy(alpha = 0.6f), shape)
                    .clickable { vm.openTab(t.key) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppIcon(t.icon, size = 16.dp, tint = if (on) cs.onPrimary else cs.onSurface)
                Text(t.label, color = if (on) cs.onPrimary else cs.onSurface, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                val n = t.badge?.let { badges[it] } ?: 0
                if (n > 0) {
                    Box(Modifier.clip(CircleShape).background(errColor()).padding(horizontal = 6.dp)) {
                        Text(if (n > 99) "৯৯+" else Fmt.bn(n), color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Fallback placeholder + data check. Every tab (২-গ … ২-ঞ) has its own route above, so this is not reached any more. */
@Composable
private fun TabContent(vm: AdminViewModel, key: String) {
    val t = TABS.first { it.key == key }
    Column(Modifier.fillMaxWidth()) {
        AppCard {
            Text(t.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            HintText("এই ট্যাব ধাপ ${t.step}-তে আসবে")
            HintText(
                "লোড হয়েছে — ব্যবহারকারী ${Fmt.bn(vm.users.size)}, কিতাব ${Fmt.bn(vm.books.size)}" +
                    ", সেটিংস ${if (vm.S.length() > 0) "আছে" else "নেই"}"
            )
        }
    }
}

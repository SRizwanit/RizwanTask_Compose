package com.srizwan.task.screens.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.Auth
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.Net
import com.srizwan.task.core.Phone
import com.srizwan.task.nav.Nav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.srizwan.task.ui.OutlineButton
import com.srizwan.task.ui.errColor

/**
 * React Task.jsx — 2-ট: the EMPTY structure of the Task page. No data, no logic yet.
 *
 * Mirrors the JSX skeleton:
 *   header.top            -> [TaskHeader]  (TaskNav.kt, 3-ক-১: logo, title, count chip, theme toggle, profile / admin / logout)
 *   main.wrap.wide        -> scrollable content, max width, bottom padding 90 (room for the bottom bar)
 * Data: [TaskData] (TaskData.kt, 3-ক-২: books, counts, my proofs / payments / settings, loaders).
 *     section#pTask       -> [TaskPane]   (3-খ: dashboard, search, book grid)
 *     PayPane             -> [PayPane]    (3-গ)
 *     RankPane            -> [RankPane]   (3-ঘ)
 *   nav.bottom            -> [TaskBottomNav] (TaskNav.kt, 3-ক-১: task / payment / rank; `switchTab` is here)
 *   dialogs               -> goal, request, notice, guide, hidden-book and "in use" dialogs (3-খ … 3-ঙ)
 *
 * It already runs the React guard (requireAuth: session, device, blocked) so login / signup / logout can be tested.
 * The temporary logout button now shows only for non-admin users and is removed in 3-চ-১ (Profile has the real one).
 */
@Composable
fun TaskScreen() {
    val data = remember { TaskData() }
    val me = data.me
    val scope = rememberCoroutineScope()

    // React `tab`: task | pay | rank.
    var tab by remember { mutableStateOf("task") }
    val scroll = rememberScrollState()
    val isAdmin = me?.let { Phone.isAdminPhone(it.phone) } == true
    // badge = unseen notices (3-খ-৩ / 3-ছ-১)
    val badge = 0

    /** React `switchTab`: change the tab, run that tab's loader, scroll to the top. */
    fun switchTab(t: String) {
        tab = t
        when (t) {
            "pay" -> scope.launch { data.loadMoney() }
            "rank" -> { /* 3-ঘ: loadRank(currentPeriod) */ }
            else -> scope.launch { data.loadCounts() }
        }
        scope.launch { scroll.scrollTo(0) }
    }

    /** React `logout`: log the action, then sign out after a short delay. */
    fun logout() {
        scope.launch { ActivityLog.log("logout", ""); delay(150); Auth.logout() }
    }

    // React init: login check, then the first load; a #pay / #rank hash opens that tab at once.
    // `hash` is kept for 3-ঙ-২: "guide" opens the guide dialog.
    var hash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val u = Auth.requireAuth() ?: return@LaunchedEffect
        data.me = u
        hash = Nav.consumeHash()
        scope.launch { data.loadCount() }
        scope.launch { data.load() }
        when (hash) {
            "prof" -> Nav.go("profile", replace = true)   // old link: the profile is a separate page now
            "pay" -> switchTab("pay")
            "rank" -> switchTab("rank")
        }
    }

    // React visibilitychange / pageshow: coming back to the app refreshes everything (the first ON_START is skipped).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, me) {
        var ready = false   // addObserver replays the current state once: ignore that replay
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && ready && data.me != null) scope.launch { data.refreshAll() }
        }
        lifecycle.addObserver(obs); ready = true
        onDispose { lifecycle.removeObserver(obs) }
    }
    // React kp-online: the connection came back -> retryAll.
    LaunchedEffect(me) {
        if (me == null) return@LaunchedEffect
        Net.onlineEvents.collect { data.retryAll() }
    }
    // React kp-retry: the net bar's retry button reloads the data instead of the whole page.
    DisposableEffect(me) {
        val h: () -> Boolean = { if (data.me == null) false else { scope.launch { data.retryAll() }; true } }
        Net.addRetryHandler(h)
        onDispose { Net.removeRetryHandler(h) }
    }

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TaskHeader(
                tab, data.myCount, badge, isAdmin,
                onProfile = { Nav.go("profile") }, onAdmin = { Nav.go("admin") }, onLogout = { logout() }
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 980.dp).fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(start = 14.dp, end = 14.dp, bottom = 90.dp)
                ) {
                    when (tab) {
                        "pay" -> PayPane()
                        "rank" -> RankPane()
                        else -> TaskPane()
                    }
                }
                // temporary: non-admin users have no other logout until the Profile page (3-চ-১); admins use the header button
                if (me != null && !isAdmin) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(24.dp).widthIn(max = 280.dp)) {
                        OutlineButton("লগআউট (সাময়িক)", onClick = { logout() }, color = errColor())
                    }
                }
            }
            if (me != null) TaskBottomNav(tab, onPick = { switchTab(it) })
        }
        // dialogs (goal, request, notice, guide, hidden-book, book-in-use) are added in 3-খ … 3-ঙ
    }
}

/** section#pTask — dashboard, search + refresh, book grid (3-খ). */
@Composable
private fun TaskPane() { /* 3-খ */ }

/** PayPane — earnings, payment list, request (3-গ). */
@Composable
private fun PayPane() { /* 3-গ */ }

/** RankPane — leaderboard (3-ঘ). */
@Composable
private fun RankPane() { /* 3-ঘ */ }

package com.srizwan.task

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/*
 * ---------------------------------------------------------------------------
 *  Task page  (port of React pages/Task.jsx)
 *
 *   header      : logo, title, finished-task count, theme switch, profile (+ new notice badge),
 *                 admin (admin only), logout
 *   bottom tabs : টাস্ক | পেমেন্ট | র‍্যাংক
 *   dialogs     : daily goal, payment request, notices, first-time guide
 *
 *  Pages that are not built yet (Profile / Admin / View) are connected through the
 *  three callbacks below. Until they are passed, a small message is shown.
 * ---------------------------------------------------------------------------
 */
@Composable
fun TaskScreen(
    vm: AuthViewModel,
    onOpenProfile: (() -> Unit)? = null,
    onOpenAdmin: (() -> Unit)? = null,
    onOpenBook: ((bookId: String, stem: String) -> Unit)? = null
) {
    val user = vm.user ?: return
    val context = LocalContext.current
    // one ViewModel per user, so a second account on the same phone never sees old data
    val tvm: TaskViewModel = viewModel(key = "task_${user.id}")

    LaunchedEffect(user) { tvm.bind(user) }
    LaunchedEffect(tvm.sessionExpired) { if (tvm.sessionExpired) vm.onSessionExpired() }

    // coming back to the app -> fresh data (same as the web page's visibilitychange)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tvm.refreshAll() }

    // notices are checked again every 3 minutes while the page is on screen
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(180_000)
                tvm.loadNotices()
            }
        }
    }

    BackHandler(enabled = tvm.tab != TaskTab.Task) { tvm.selectTab(TaskTab.Task) }

    val soon = { what: String ->
        Toast.makeText(context, "$what পরের ধাপে আসছে", Toast.LENGTH_SHORT).show()
    }
    val openBook: (String, String) -> Unit = { id, stem ->
        if (onOpenBook != null) onOpenBook(id, stem) else soon("কিতাব দেখার পেইজ")
    }

    // scroll positions survive tab switches
    val bookListState = rememberLazyListState()
    val payScroll = rememberScrollState()
    val rankScroll = rememberScrollState()

    TaskBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TaskHeader(
                title = tvm.tab.title,
                count = tvm.myCount,
                badge = tvm.badge,
                isAdmin = user.isAdmin,
                themeMode = AppTheme.mode,
                onTheme = {
                    val next = AppTheme.mode.next()
                    AppTheme.set(context, next)
                    tvm.showToast("থিম: ${next.label}")
                },
                onProfile = { if (onOpenProfile != null) onOpenProfile() else soon("প্রোফাইল") },
                onAdmin = { if (onOpenAdmin != null) onOpenAdmin() else soon("অ্যাডমিন প্যানেল") },
                onLogout = { vm.logout() }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tvm.tab) {
                    TaskTab.Task -> BooksTab(tvm, bookListState, openBook)
                    TaskTab.Pay -> PayTab(tvm, user, payScroll, openBook)
                    TaskTab.Rank -> RankTab(tvm, user, rankScroll)
                }
            }

            TaskBottomNav(selected = tvm.tab, onSelect = { tvm.selectTab(it) })
        }

        ToastHost(
            toast = tvm.toast,
            onDone = { tvm.clearToast(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }

    // ---- dialogs ------------------------------------------------------------------
    if (tvm.goalDialogOpen) {
        GoalDialog(
            initial = tvm.goal,
            error = tvm.goalError,
            onCancel = { tvm.closeGoalDialog() },
            onSave = { tvm.saveGoal(it) }
        )
    }

    tvm.requestInfo?.let { info ->
        RequestDialog(
            info = info,
            error = tvm.requestError,
            sending = tvm.requestSending,
            onCancel = { tvm.closeRequest() },
            onSend = { tvm.sendRequest(it) }
        )
    }

    if (tvm.guideOpen) {
        GuideDialog(onClose = { tvm.closeGuide() })
    } else if (tvm.noticeQueue.isNotEmpty()) {
        NoticeDialog(
            list = tvm.noticeQueue,
            index = tvm.noticeIndex,
            onIndex = { tvm.noticeIndex = it },
            onClose = { tvm.closeNotices() }
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun TaskHeader(
    title: String,
    count: Int?,
    badge: Int,
    isAdmin: Boolean,
    themeMode: ThemeMode,
    onTheme: () -> Unit,
    onProfile: () -> Unit,
    onAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface.copy(alpha = 0.97f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = colors.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.rizwantask),
                        contentDescription = "RizwanTask",
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Text(
                text = title,
                color = colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (count != null) {
                Surface(shape = RoundedCornerShape(50), color = colors.primary.copy(alpha = 0.14f)) {
                    Text(
                        text = "✓ ${bn(count)} টি সম্পন্ন",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            IconPill(icon = themeMode.icon, onClick = onTheme)
            IconPill(icon = "👤", onClick = onProfile, badge = badge)
            if (isAdmin) IconPill(icon = "🛡️", onClick = onAdmin)
            // The profile page (next step) will hold the logout button for normal users;
            // until it exists, everyone gets it here so nobody is stuck.
            IconPill(icon = "⎋", onClick = onLogout)
        }
    }
}

/** Round emoji button with an optional red number badge. */
@Composable
fun IconPill(icon: String, onClick: () -> Unit, badge: Int = 0) {
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (badge > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error
            ) {
                Text(
                    text = bn(badge),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom tabs
// ---------------------------------------------------------------------------
private val NAV_ITEMS = listOf(
    Triple(TaskTab.Task, "📋", "টাস্ক"),
    Triple(TaskTab.Pay, "👛", "পেমেন্ট"),
    Triple(TaskTab.Rank, "🏆", "র‍্যাংক")
)

@Composable
private fun TaskBottomNav(selected: TaskTab, onSelect: (TaskTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NAV_ITEMS.forEach { (tab, icon, label) ->
                val on = tab == selected
                Surface(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (on) colors.primary.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = icon, fontSize = 20.sp, color = colors.onSurface)
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

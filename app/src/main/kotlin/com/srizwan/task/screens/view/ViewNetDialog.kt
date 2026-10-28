package com.srizwan.task.screens.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.srizwan.task.core.Net
import com.srizwan.task.core.ViewBoot
import com.srizwan.task.screens.task.NetErrorDialog
import com.srizwan.task.ui.NetBarGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Check your internet connection" dialog for the View page - the same dialog as the Task page ([NetErrorDialog]).
 * One host for both phases of the page (only one of them is ever on screen):
 *  - the loading screen ([boot] not done, [st] == null or the first page still coming up): shown when the internet is gone ([ViewBoot.Issue.OFFLINE]),
 *    or a request failed / is slow while the app reports a connection problem ([Net.problem]);
 *  - the page is open ([boot] done): shown when the CURRENT page's image or text could not be loaded and the phone is offline / has a connection problem.
 * Back press leaves the page (to the task page) - the user is never trapped; outside tap does not close it.
 * "Refresh" = a new try: loading screen -> [ViewBoot.requestRetry]; open page -> [ViewPageState.loadPage] (the cached images on disk are NOT cleared).
 * While the dialog is open the red NetBar is hidden ([NetBarGate]); it comes back when the dialog closes or the page is left.
 * Nothing here is an emoji or an XML drawable (vector art inside [NetErrorDialog]).
 */
@Composable
fun ViewNetDialog(boot: ViewBoot, st: ViewPageState?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val problem by Net.problem.collectAsState()
    var online by remember { mutableStateOf(Net.isOnline) }
    LaunchedEffect(Unit) { while (true) { online = Net.isOnline; delay(500) } }
    var busy by remember { mutableStateOf(false) }

    val loadingNeed = !boot.done && (boot.issue == ViewBoot.Issue.OFFLINE ||
        (problem && (boot.issue == ViewBoot.Issue.FAILED || boot.issue == ViewBoot.Issue.SLOW)))
    val pageFailed = st != null && boot.done && (st.loadErr || (st.image == null && st.imgMsg.isNotEmpty()))
    val pageNeed = pageFailed && (!online || problem)
    // `busy` keeps the dialog up while a retry runs (the retry clears the issue for a moment - no flicker)
    val show = loadingNeed || pageNeed || busy

    DisposableEffect(show) {
        NetBarGate.hidden.value = show
        onDispose { NetBarGate.hidden.value = false }
    }
    if (!show) return

    NetErrorDialog(busy = busy, onBack = onBack, onRefresh = {
        if (!busy) scope.launch {
            busy = true
            try {
                if (!boot.done) { boot.requestRetry(); delay(1500) }
                else st?.loadPage()
            } finally { busy = false }
        }
    })
}

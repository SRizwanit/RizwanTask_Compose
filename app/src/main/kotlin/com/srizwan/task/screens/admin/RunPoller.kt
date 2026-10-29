package com.srizwan.task.screens.admin

import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.finiteNum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * React admin/BooksTab.jsx status polling (`schedulePoll`, `pollRuns`, `autoDone`, `visibilitychange`).
 *
 * - Polls GitHub only while the Books tab is the open tab, the app is in the foreground ([visible], React
 *   `document.visibilityState`) and a GitHub token exists; otherwise it just checks again after 8 s.
 * - Every 8 s while some run is active, every 15 s otherwise. A run that has completed is never fetched again.
 * - When a run finishes with success the book's pages are counted automatically (once per run): silently when the
 *   run was first seen already finished and the book has no total pages yet, with a toast when it was seen running.
 */
class RunPoller(private val vm: AdminViewModel, private val scope: CoroutineScope) {
    private var job: Job? = null

    /** App in the foreground (set by BooksTab from the lifecycle). */
    @Volatile var visible: Boolean = true

    /** `bookId:runId-or-time` of runs whose automatic page count was already started (React `autoDone`). */
    private val autoDone = HashSet<String>()

    /** React `schedulePoll(ms)`: the next poll after [ms] (restarts the timer). */
    fun schedule(ms: Long = 8000) {
        job?.cancel()
        job = scope.launch { delay(ms); poll() }
    }

    /** React direct `pollRuns()` call: poll right now (tab opened, app came back, books reloaded). */
    fun pollNow() {
        job?.cancel()
        job = scope.launch { poll() }
    }

    fun stop() { job?.cancel(); job = null }

    private suspend fun poll() {
        val s0 = vm.S
        if (!visible || vm.tab != "Books" || vm.books.none { ghTok(s0, it).isNotEmpty() }) { schedule(8000); return }
        var any = false
        try {
            coroutineScope {
                vm.books.map { b ->
                    async {
                        if (pollOne(s0, b)) any = true
                    }
                }.awaitAll()
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* a single failure must not stop the loop */ }
        schedule(if (any) 8000 else 15000)
    }

    /** One book. Returns true when its run is (still) active. */
    private suspend fun pollOne(s0: org.json.JSONObject, b: org.json.JSONObject): Boolean {
        val info = RunStore.getRun(b) ?: return false
        if (ghTok(s0, b).isEmpty()) return false          // this book's GitHub account has no token (same as before: nothing is polled)
        val id = b.idStr()
        val old = RunStore.stateOf(id)
        if (old != null && old.status == "completed") return false          // finished runs are not fetched again
        val st: RunState = try {
            fetchRunState(s0, b, info) { rid -> vm.saveRun(b, RunInfo(rid, info.at)) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            RunState("error", err = e.message.orEmpty())
        }
        RunStore.setRun(id, st)
        if (st.status == "completed" && st.conclusion == "success") {
            val ak = id + ":" + (info.id ?: info.at)
            val wasActive = old != null && isActiveRun(old)
            val noPages = (b.finiteNum("total_pages") ?: 0.0) <= 0.0
            if (ak !in autoDone && (wasActive || noPages)) {
                autoDone.add(ak)
                // own coroutine: a new schedule() must not cancel a count that has already been marked as started
                scope.launch {
                    val n = autoCount(vm, b, !wasActive)
                    if (n != null && wasActive) AppToast.show("ছবি তৈরি শেষ — স্বয়ংক্রিয় পেইজ গণনা: ${Fmt.bn(n)}")
                }
            }
        }
        return isActiveRun(st)
    }
}

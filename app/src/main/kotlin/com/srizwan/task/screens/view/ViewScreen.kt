package com.srizwan.task.screens.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srizwan.task.core.ViewApiFlag
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewLock
import com.srizwan.task.core.ViewToaster
import com.srizwan.task.ui.ViewLoadingToast
import com.srizwan.task.ui.ViewRippleHost
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Net
import com.srizwan.task.core.ViewBoot
import com.srizwan.task.core.ViewCtx
import com.srizwan.task.core.ViewEntry
import com.srizwan.task.core.ViewEntryResult
import com.srizwan.task.nav.Nav
import com.srizwan.task.nav.Routes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.srizwan.task.ui.viewColors
import androidx.compose.ui.unit.sp

/**
 * React `View()` wrapper — route `view?book=<id>&page=<stem>`.
 * [bookId] = `?book=`, [wantPage] = `?page=` (the "needs correction" link from the task page opens that page directly).
 * [ViewEntryLoading] covers the screen from the first frame until the first page is ready: one percentage (Bangla digits) for the whole way
 * (book + settings here, repo / page list / first image + text in [ViewPage] -> [ViewBootEffect]); never a blank page. No internet / slow
 * internet / a failed request show a custom toast on it, and when the internet is back the loading starts again by itself.
 */
@Composable
fun ViewScreen(bookId: String?, wantPage: String?) {
    var ctx by remember { mutableStateOf<ViewCtx?>(null) }
    var hiddenMsg by remember { mutableStateOf<String?>(null) }
    val boot = remember { ViewBoot() }

    // clock of the loading screen: the creeping number + the problem toast (offline / slow / back)
    LaunchedEffect(boot) { while (!boot.done) { boot.tick(Net.isOnline); delay(250) } }
    // the app-wide net bar's "আবার চেষ্টা" button also retries this screen while it is loading
    DisposableEffect(boot) {
        val h: () -> Boolean = {
            if (boot.done) false
            else { if (Net.isOnline) boot.requestRetry() else AppToast.show("এখনো ইন্টারনেট নেই", true); true }
        }
        Net.addRetryHandler(h)
        onDispose { Net.removeRetryHandler(h) }
    }

    LaunchedEffect(bookId, boot.retryTick) {
        if (ctx != null) return@LaunchedEffect                 // the book is loaded: a retry now belongs to the page (ViewBootEffect)
        when (val r = ViewEntry.load(bookId, boot)) {
            is ViewEntryResult.Ready -> ctx = r.ctx
            is ViewEntryResult.Hidden -> hiddenMsg = r.message         // the dialog opens over the loading screen
            ViewEntryResult.Stay -> Unit
            ViewEntryResult.Failed -> Unit                     // the loading screen shows the problem; it retries by itself when the internet is back
        }
    }

    var lockedBy by remember { mutableStateOf<String?>(null) }

    val toaster = remember { ViewToaster() }
    DisposableEffect(toaster) { onDispose { toaster.dispose() } }

    Box(Modifier.fillMaxSize()) {
        val c = ctx
        if (c != null) {
            val apiFlag = remember(c) { ViewApiFlag(c.settings, toaster) }
            ViewLockEffect(c) { lockedBy = it }
            ViewApiFlagEffect(apiFlag)
            val env = remember(c) { ViewEnv(c, toaster, apiFlag) }
            ViewPage(env, wantPage, boot)
            ViewLoadingToast(toaster)
        }
        // one instance for the whole way (book load -> page init -> first page), so the card never restarts
        ViewEntryLoading(boot, onBack = { Nav.go(Routes.TASK, replace = true) })
    }

    lockedBy?.let { who ->
        AlertDialog(
            onDismissRequest = { },
            text = { Text("$who এই কিতাবটির কাজ করছেন...\nউনি কাজ অফ করলে আপনি করতে পারবেন।") },
            confirmButton = { TextButton(onClick = { lockedBy = null; Nav.go(Routes.TASK, replace = true) }) { Text("ঠিক আছে") } }
        )
    }

    hiddenMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { hiddenMsg = null; Nav.go(Routes.TASK, replace = true) }) { Text("ঠিক আছে") } }
        )
    }
}

/**
 * "কাজ চলছে" signal while the page is open: beat now + every 20 s, again when the app comes back to the foreground
 * (React `visibilitychange`), release when leaving. [onTaken] = somebody else is active on this book.
 */
@Composable
private fun ViewLockEffect(ctx: ViewCtx, onTaken: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val lock = remember(ctx) { ViewLock(ctx) { onTaken(it) } }
    LaunchedEffect(lock) { lock.loop() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(lock, owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) scope.launch { lock.beat() } }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob); lock.stop() }
    }
}

/**
 * Re-reads the admin's API on/off every 30 s while the page is visible, and when the app returns to the foreground
 * (React `setInterval(refreshApiFlag, 30000)` + `visibilitychange`). The first read is the one done at page load.
 */
@Composable
private fun ViewApiFlagEffect(flag: ViewApiFlag) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(flag, owner) {
        var first = true
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!first) flag.refresh()
            first = false
            while (true) { delay(30_000); flag.refresh() }
        }
    }
}

/**
 * The real page (images, text editor, GitHub save, Gemini, TTS) comes in the next steps; until then a placeholder.
 * ধাপ ৪-ঘ-১: [ViewPageState] (page list, image, text, `loadDone`, `loadPage`, `goPage`) is created here; the lightbox lives in it
 * (`st.openLightbox()` on image tap; `loadPage` closes it). The `init` step (4-ঘ-৫) fills `st.putImages(..)` and calls `st.goPage`/`loadDone`.
 */
@Composable
fun ViewPage(env: ViewEnv, wantPage: String?, boot: ViewBoot? = null) {
    val ctx = env.ctx
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember(env) { ViewPageState(env, scope) }
    // image box scroll + "where I stopped" save (React `saveView`: `box.scrollTop/Left`); the text half (4-ঞ-৩-ঘ) takes over `tt` / `c`
    val imgScroll = rememberViewImageScroll()
    val textEd = rememberViewTextEditor(st)
    // React `window.confirm` -> a dialog (4-ঞ-৩-ঙ); the page's `st.confirm` and the auto-OCR dialog check use it
    val confirmState = remember(st) { ViewConfirmState() }
    DisposableEffect(st, confirmState) {
        st.confirm = { confirmState.ask(it) }
        onDispose { st.confirm = { true }; confirmState.answer(false) }
    }
    val dens = LocalDensity.current.density
    DisposableEffect(st, imgScroll, textEd) {
        st.viewPos = {
            val (t, l) = imgScroll.posDp(dens)
            ViewPos(t, l, (textEd.scroll.value / dens).roundToInt(), textEd.tfv.selection.start)   // React `ta.scrollTop`, `ta.selectionStart`
        }
        onDispose { st.viewPos = { null } }
    }
    val api = remember(st) { ViewApi(env, st, scope, context.applicationContext) }   // Gemini (4-chha-2); the buttons / dialogs of the UI come in 4-nga-3 and 4-chha-3
    DisposableEffect(api) { onDispose { api.cancelAuto() } }   // React cleanup `clearTimeout(autoT.current)`
    // Reading aloud (React `tts = new TtsController({...})`, 4-জ-১): the frame + state; the engines / highlight / start come in 4-জ-২ … 4-ঝ-৩.
    val tts = remember(st) {
        TtsController(TtsDeps(
            appCtx = context.applicationContext,
            scope = scope,
            getCfg = { env.cfg.cfg },
            getText = { st.text },
            getSel = { st.textSelection() },
            canPlay = { st.cur >= 0 && !st.taLoading },
            setCaret = { st.onSetCaret(it) },
            scrollRatio = { st.onScrollRatio(it) },
            toast = { m, err, ms -> env.toaster.toast(m, err, ms) }
        ))
    }
    // the service picker "কোন সার্ভিসে শুনবেন?" (React `tsOpen` / `askTts` / `onTsGo` / `onTsClose`, 4-ঝ-৩)
    val picker = remember(tts) { TtsPicker(env, tts) }
    // the settings dialog (admin only) — React `settingsOpen` / `openSettings` / `onSettingsClose` / `saveSettings` (4-ঞ-১)
    val settings = remember(tts) { ViewSettings(env, st, tts, scope, wantPage) }
    // Google Lens in its own screen (React `openLens`, 4-ঞ-২) — the header button of 4-ঞ-৩ calls `lens.open(context)`
    val lens = remember(st) { ViewLens(env, st, scope) }
    DisposableEffect(tts) {
        // the phone's own engine (4-জ-২); its start makes the voice list known (React `voiceschanged` -> `refreshVoices`)
        val dev = TtsDevice(context) { tts.refreshVoices() }
        tts.device = dev
        tts.ask = { cb -> picker.ask(cb) }             // "শুনুন" asks for a service first (React `ask: askTts`)
        api.anyDialogOpen = { picker.open || settings.open || confirmState.open }   // auto-OCR waits while a dialog is up (the confirm dialog 4-ঞ-৩ joins here)
        st.onOpenSettings = { settings.openSettings() }        // admin without token at the start / connection failed (React `openSettings()`)
        st.onTtsStale = { tts.markStale() }
        st.onStopTts = { tts.stop() }                  // page change / leaving / background: `tts.stop()`
        tts.refreshVoices()                            // React `useEffect(() => { refreshVoices(); ... })`
        onDispose { tts.stop(); st.onStopTts = {}; st.onOpenSettings = {}; tts.device = null; tts.ask = { cb -> cb() }; api.anyDialogOpen = { false }; dev.shutdown() }
    }
    if (boot != null) ViewBootEffect(st, boot, wantPage)       // percentage / problems of the loading screen
    ViewLeaveEffect(st)
    ViewProofSyncEffect(st)
    LaunchedEffect(st) { st.start(wantPage) }
    // keyboard shortcuts: the root takes the keys before the text box (React `document.addEventListener('keydown', onKey)`)
    ViewRippleHost { Box(Modifier.fillMaxSize().background(viewColors().bg)) { Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().onPreviewKeyEvent { st.onKey(it) }) {   // status / navigation bar safe area + keyboard (adjustResize) never cover the page
          // React `useRipple()` — green ripple look for every button of the page
        // header (4-ঞ-৩-ক) and status bar (খ); image / text panes (গ, ঘ) and the footer (ঙ) come next and take the rest of the column
        ViewHeader(env, st, tts, api, settings, onBack = { Nav.go(Routes.TASK, replace = true) }, onReload = { scope.launch { st.reload(wantPage) } })
        ViewStatusBar(env, st, api, lens)
        // React `<main id="main">` — two equal halves (image | text, swapped by `cfg.swap`) on the panel colour; 10 dp gap and sides (6 dp on a phone)
        env.cfg.rev
        val swap = env.cfg.cfg.optBoolean("swap", false)
        val gap = if (LocalConfiguration.current.screenWidthDp <= 640) 6.dp else 10.dp
        Row(Modifier.weight(1f).fillMaxWidth().background(viewColors().panel).padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val imagePane = Modifier.weight(1f).fillMaxHeight()
            if (swap) { ViewTextPane(env, st, tts, api, textEd, imagePane); ViewImagePane(env, st, imgScroll, imagePane) }
            else { ViewImagePane(env, st, imgScroll, imagePane); ViewTextPane(env, st, tts, api, textEd, imagePane) }
        }
        ViewFooter(env, st)
    } } }
    // download button: React `imagesRef.current[curRef.current].name` (null when no page is open)
    ApiDialogs(env, api, st.images.getOrNull(st.cur)?.stem)     // React `<ApiDialogs …/>` (4-chha-3)
    TtsDialog(env, tts, picker)                                 // React `<TtsDialog … />` (4-jha-3)
    ViewAiOpenDialog(st)                                        // after the AI prompt copy: open the AI chat in the browser?
    ViewConfirmDialog(confirmState)                             // React `window.confirm` (4-nya-3-ngo)
    ViewSaveWaitDialog(st)                                      // "সেভ ও পরের": ৩০ সেকেন্ডের কাউন্টডাউন ডায়ালগ
    SettingsDialog(env, tts, settings)                          // React `<SettingsDialog … />` (4-nya-1) — admin only
    ViewLightbox(st.lightbox) { scope.launch { env.downloadCurrent(context, st.currentName ?: st.lightbox.title.ifEmpty { null }) } }
}

/** What [ViewBootEffect] watches in [ViewPageState] while the first page comes up. */
private data class BootSnap(
    val initing: Boolean, val failed: Boolean, val error: String, val hasList: Boolean, val opened: Boolean,
    val imgDone: Boolean, val imgPct: Float, val txtDone: Boolean, val pageErr: Boolean
)

/**
 * Feeds the loading screen ([ViewBoot]) from the page's own state, from 45% to 100%:
 * 45-80 repo + page list, 80-88 owners / opening the page, 88-100 the first page image (its real download %) and text.
 * A failed init -> the problem toast with the reason; the loading screen is left only when the first page is really there
 * (or when nothing is going to load: admin settings dialog first). A page that failed while the phone is offline keeps the screen,
 * so the retry (button, or the internet coming back) reloads everything ([ViewPageState.reload]).
 */
@Composable
private fun ViewBootEffect(st: ViewPageState, boot: ViewBoot, wantPage: String?) {
    LaunchedEffect(st, boot) {
        snapshotFlow {
            BootSnap(
                initing = st.initing, failed = st.initFailed, error = st.initError,
                hasList = st.images.isNotEmpty(), opened = st.cur >= 0,
                imgDone = !st.ldrOn && (st.image != null || st.imgMsg.isNotEmpty()),
                imgPct = if (st.image != null) 100f else (st.ldrPct ?: 0f),
                txtDone = !st.taLoading || st.loadErr,
                pageErr = st.loadErr || (st.image == null && st.imgMsg.isNotEmpty())
            )
        }.collectLatest { s ->
            if (boot.done) return@collectLatest
            val pageEnd = s.opened && s.imgDone && s.txtDone
            when {
                pageEnd -> {
                    if (s.pageErr && !Net.isOnline) boot.fail("ছবি বা লেখা আনা যায়নি। ইন্টারনেট ফিরলে আবার লোড হবে।")
                    else boot.finish()
                }
                s.failed -> boot.fail(s.error)
                s.initing && !s.hasList -> { boot.clearFail(); boot.stage(45f, 80f, "পেইজ তালিকা আনা হচ্ছে…") }
                s.initing && s.hasList && !s.opened -> boot.stage(80f, 88f, "পেইজ প্রস্তুত করা হচ্ছে…")
                s.opened -> {
                    boot.stage(88f, 99f, "পেইজের ছবি ও লেখা আনা হচ্ছে…")
                    boot.reach(88f + 8f * (s.imgPct / 100f).coerceIn(0f, 1f) + (if (s.txtDone) 3f else 0f))
                }
                else -> { delay(1200); boot.finish() }          // nothing is loading (admin's settings dialog, or no page is left for this user): show the page
            }
        }
    }
    // a retry started from the loading screen (button / internet back) while the page is the one loading
    var seen by remember { mutableIntStateOf(boot.retryTick) }
    LaunchedEffect(boot.retryTick) {
        if (boot.retryTick != seen) {
            seen = boot.retryTick
            if (!boot.done) st.reload(wantPage)
        }
    }
}

/**
 * Other users proofing pages while this one is open: every 60 s while the app is in front, the owners are read again and applied
 * (React `setInterval(syncProofs, 60000)`); on returning to the app the same runs from [ViewLeaveEffect] (`visibilitychange`).
 * Pages other users proofed disappear from the list; if the open page became theirs, the reader moves on ([ViewPageState.applyProofs]).
 */
@Composable
private fun ViewProofSyncEffect(st: ViewPageState) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(st, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(60_000); st.syncProofs() }
        }
    }
}

/**
 * Keep everything when the page goes away: app to background (React `visibilitychange` hidden / `pagehide` -> `flush()` + `tts.stop()`)
 * and when the screen is left (React unmount -> `flush(); tts.stop()` + timers cleared).
 */
@Composable
private fun ViewLeaveEffect(st: ViewPageState) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(st, owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { st.flush(); st.onStopTts() } else if (e == Lifecycle.Event.ON_START) { scope.launch { st.loadDone() }; scope.launch { st.syncProofs() } } }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob); st.onLeave() }
    }
}

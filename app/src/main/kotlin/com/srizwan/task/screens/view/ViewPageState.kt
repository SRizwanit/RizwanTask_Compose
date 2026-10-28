package com.srizwan.task.screens.view

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.MotionStore
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.Sb
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewToaster
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.core.copyText
import com.srizwan.task.core.enc
import com.srizwan.task.core.str
import com.srizwan.task.screens.task.resumeKey
import com.srizwan.task.ui.ViewDropdownOption
import com.srizwan.task.ui.ViewDropdowns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Where the reader is now (React `box.scrollTop/Left`, `ta.scrollTop`, `ta.selectionStart`): image scroll top/left, text scroll top, cursor. */
class ViewPos(val it: Int, val il: Int, val tt: Int, val c: Int)

/** Result of `refreshProofs()` (4-ঙ-১): [Ok] = true, [NoTable] = 'none' (page_proofs table missing), [Failed] = false. */
enum class ProofRefresh { Ok, NoTable, Failed }

/** React `imagesRef.current[i]` = `{ name, stem }` (the page image file name and its name without extension). */
class ViewImageRef(val name: String, val stem: String)

/**
 * Page-level state + page loading of the View screen (React `View.jsx` `ViewPage` hooks/refs):
 * the page list (`imagesRef`, `cur`), the shown image (`imgRef` + `ldr` + `imgMsg`), the text editor value
 * (`ta.value`, `orig`, `sha`, `taLoading`, `loadErr`, `placeholder`), `loadDone` (my finished-task count), `loadPage`, `goPage`.
 * The UI of the image area / editor (4-ঘ-৫ … 4-চ) only READS these values; later steps fill the hooks below.
 * Supabase: `rpc/my_proof_count(p_user text)`, `page_proofs` (rizwantask_full_schema.sql). Ids are Strings.
 */
class ViewPageState(private val env: ViewEnv, private val scope: CoroutineScope) {
    private val ctx = env.ctx

    val lightbox = ViewLightboxState()

    // ---------------- pages ----------------
    var images by mutableStateOf<List<ViewImageRef>>(emptyList()); private set
    /** React `cur` (-1 = no page open). */
    var cur by mutableIntStateOf(-1); private set
    /** Set by the `init` step (4-ঘ-৫) after the page list is read from the repo. */
    fun putImages(list: List<ViewImageRef>) { images = list }
    fun putCur(i: Int) { cur = i }
    val currentName: String? get() = images.getOrNull(cur)?.name

    // ---------------- image area ----------------
    var image: ImageBitmap? by mutableStateOf(null); private set
    /** React `imgMsg` (error line shown instead of the image). */
    var imgMsg by mutableStateOf(""); private set
    /** React `ldr`: [ldrOn] false = no loader (undefined); [ldrPct] null = unknown %, else 0..100. */
    var ldrOn by mutableStateOf(false); private set
    var ldrPct: Float? by mutableStateOf(null); private set
    /** React `ldrKey` — restarts the loader animation from 0 for every page. */
    var ldrKey by mutableIntStateOf(0); private set
    /** Last saved image scroll (React `pv.il/it`) to apply once the image is shown; [imgScrollTick] bumps for every new image (React `scrollTo(0,0)`). */
    var imgScrollX by mutableIntStateOf(0); private set
    var imgScrollY by mutableIntStateOf(0); private set
    var imgScrollTick by mutableIntStateOf(0); private set

    // ---------------- text editor ----------------
    /** React `ta.value` (the editor reads and writes this). */
    var text by mutableStateOf("")
    /** React `orig` (repo text, the base of every comparison) and `sha`. */
    var orig by mutableStateOf(""); private set
    var sha: String? by mutableStateOf(null); private set      // state: the status line (সেভড / টেক্সট নেই) follows it
    var taLoading by mutableStateOf(true); private set
    var loadErr by mutableStateOf(false); private set
    var placeholder by mutableStateOf(""); private set
    /** Restore of the last cursor / scroll after a page load (React `ta.setSelectionRange` + `ta.scrollTop`); [textRestoreTick] bumps on every change. */
    var textCursor by mutableIntStateOf(0); private set
    var textScroll by mutableIntStateOf(0); private set
    var textRestoreTick by mutableIntStateOf(0); private set

    /** React `busy` — a save is running (set by the save step 4-ঘ-২). */
    var busy by mutableStateOf(false)
    var viewReady by mutableStateOf(false); private set
    var restoreOnce = true; private set

    /** React `doneCnt` (null = not known yet). */
    var doneCnt by mutableStateOf<Int?>(null); private set

    // ---------------- hooks filled by later steps ----------------
    /** `tts.stop()` — 4-জ-১. */
    var onStopTts: () -> Unit = {}
    /** Set by the image area / editor UI: current image scroll, text scroll and cursor (null while the UI is not there). */
    var viewPos: () -> ViewPos? = { null }
    /** `hiddenStem(stem)` — page proofed by another user; filled in `init {}` below with [hiddenStemNow] (4-ঙ-২). */
    var hiddenStem: (String) -> Boolean = { false }
    /** `updateStatus()` — status line (4-ঞ-৩). */
    var onStatus: () -> Unit = {}
    /** `refreshProofs()` — who proofed which page (Supabase) — 4-ঙ-১. */
    var refreshProofs: suspend () -> ProofRefresh = { ProofRefresh.Ok }
    /** `showNone()` — every page is hidden for this user — [showNone] (4-ঙ-২). */
    var onShowNone: () -> Unit = {}
    /** `closeDropdown()` — the page-list dropdown closes when the list is re-arranged; filled in `init {}` with [ViewDropdowns.close] (4-চ-৪). */
    var onCloseDropdown: () -> Unit = {}
    /** `openSettings()` — admin settings dialog; filled in [ViewPage] with [ViewSettings.openSettings] (4-ঞ-১). */
    var onOpenSettings: () -> Unit = {}
    /** `ownerOk(stem)` — false (with its own toast) when another user already proofed the page; 4-ঙ-১. */
    var ownerOk: suspend (String) -> Boolean = { true }
    /** `applyProofs()` after a save was blocked by another owner — [applyProofs] (4-ঙ-২). */
    var onBlocked: () -> Unit = {}
    /** `window.confirm(msg)` — a yes / no dialog (the UI shows it, 4-ঞ-৩); until then it answers yes. Used by [proofAndNext]. */
    var confirm: suspend (String) -> Boolean = { true }
    /** Selection of the text editor (start, end; equal = only a cursor) — the editor UI sets this (4-ঞ-৩); until then the whole text counts. */
    var textSelection: () -> Pair<Int, Int> = { 0 to 0 }
    /** React `taRef.current.setSelectionRange(p, p)` — put the editor cursor without opening the keyboard; used by [TtsController] (4-জ-১). The editor UI sets it (4-ঞ-৩). */
    var onSetCaret: (Int) -> Unit = {}
    /** React `tts.markStale()` — the user edited the text while reading aloud (4-জ-৩); set in `ViewPage`. */
    var onTtsStale: () -> Unit = {}
    /** React `ta.scrollTop = ta.scrollHeight * r - ta.clientHeight * 0.3` — scroll the editor to the part [r] (0..1) of the text; used by [TtsController]. The editor UI sets it (4-ঞ-৩). */
    var onScrollRatio: (Float) -> Unit = {}
    /** Auto heading (React `markHeadings` result: new text + number of headings marked). Filled in `init {}` with [ViewUtils.markHeadings] (4-চ-২). */
    class HeadResult(val text: String, val n: Int)
    var markHeadings: (String) -> HeadResult = { HeadResult(it, 0) }
    /** The editor selects its whole text when this changes (React `ta.focus(); ta.select()` when the clipboard cannot be read). */
    var selectAllTick by mutableIntStateOf(0); private set
    /** `maybeAutoOcr()` — 4-ছ-৪. */
    var onPageLoaded: () -> Unit = {}

    // ---------------- connection / init state ----------------
    /** React `proofedRef` (stems with a proofed mark) and `ownersRef` (stem -> user id) — filled here, used by 4-ঙ. */
    var proofed: MutableSet<String> = mutableSetOf()
    var owners: MutableMap<String, String> = mutableMapOf()
    /** Bumped when [images] / [proofed] / [owners] change in place (React `repaint`). */
    var listRev by mutableIntStateOf(0)
    var repoPrivate: Boolean? = null; private set
    var defaultBranch: String = ""; private set
    /** The proofed list was read from the old place (repo root) — the first save writes the new folder (4-ঙ-৩). */
    var legacyProof = false; private set
    /** React `proofDbRef` — the `page_proofs` table exists (false = old behaviour, GitHub list only). */
    var proofDb = true; private set
    /** React `reviewsRef`: stem -> review status ("pending" / "approved" / "correction") + admin note. */
    class Review(val s: String, val note: String)
    var reviews: MutableMap<String, Review> = mutableMapOf()
    /** React `ownerNamesRef` — user id -> name (admin only). */
    val ownerNames: MutableMap<String, String> = mutableMapOf()
    var initing by mutableStateOf(false); private set
    var initFailed by mutableStateOf(false); private set
    /** Reason text of the last failed [init] (shown by the loading screen's problem toast). */
    var initError by mutableStateOf(""); private set
    var forceInit = false
    /** React `autoTried` — pages API-1 already ran on in this session (4-ছ-৪). */
    val autoTried: MutableSet<String> = mutableSetOf()
    private var connSig: String = ViewConfig.connSig(env.cfg.cfg)

    private var seq = 0
    private var doneBusy = false
    private var doneAgain = false

    init {
        env.onImgProgress = { n, p -> imgProg(n, p) }
        refreshProofs = { refreshProofsNow() }
        ownerOk = { ownerOkNow(it) }
        hiddenStem = { hiddenStemNow(it) }
        markHeadings = { v -> ViewUtils.markHeadings(v).let { HeadResult(it.text, it.n) } }
        onShowNone = { showNone() }
        onCloseDropdown = { ViewDropdowns.close() }
        onBlocked = { applyProofs() }
    }

    /** Pages whose `.txt` exists (React `textSetRef`, filled by `init`). */
    var textSet: MutableSet<String> = mutableSetOf()
    /** React `override` — temporary status-line text ("সেভ হচ্ছে…"); null = normal status. */
    var statusOverride: String? by mutableStateOf(null); private set
    /** React `updateStatus = () => setOverride(null)` (+ the status line hook). */
    fun updateStatus() { statusOverride = null; onStatus() }

    /** React `isDirty` — the text differs from the repo text. */
    fun isDirty(): Boolean = cur >= 0 && !taLoading && text != orig

    // ---------------- drafts ----------------
    /** Bumped when drafts change, so the status line / settings count (reads [hasDraft], [nDrafts]) recompose (React `repaint`). */
    var draftRev by mutableIntStateOf(0); private set
    private var quotaWarned = false
    private var draftJob: Job? = null

    /** React `dk(stem)` — `<repo>[@book]/<stem>`. */
    private fun dk(stem: String) = ViewConfig.draftKey(env.cfg.cfg, stem)

    /** React `persistDrafts` — save the drafts map to the phone; warns once if the storage refuses. */
    fun persistDrafts(): Boolean {
        val ok = env.cfg.saveDrafts()
        if (!ok && !quotaWarned) { quotaWarned = true; env.toaster.toast("লোকাল স্টোরেজে খসড়া রাখা যাচ্ছে না (জায়গা শেষ?) — দ্রুত সেভ করুন", true) }
        return ok
    }

    /** Draft exists for [stem] (status line dot • ). */
    fun hasDraft(stem: String): Boolean { draftRev; return env.cfg.drafts.has(dk(stem)) }

    /** Number of drafts of this repo (React `nDrafts`, shown in settings). */
    fun nDrafts(): Int {
        draftRev
        val pre = env.cfg.cfg.str("repo").orEmpty() + "/"
        var n = 0
        val it = env.cfg.drafts.keys()
        while (it.hasNext()) if (it.next().startsWith(pre)) n++
        return n
    }

    /** Write a draft directly (React `draftsRef.current[dk(stem)] = { t, ts }; persistDrafts()` — used by the API step). */
    fun putDraft(stem: String, value: String) {
        env.cfg.drafts.put(dk(stem), JSONObject().put("t", value).put("ts", System.currentTimeMillis()))
        persistDrafts(); draftRev++
    }

    /** React `stashDraft` — current text differs from the repo text -> keep it as a draft; equal -> remove the draft. */
    fun stashDraft() {
        draftJob?.cancel(); draftJob = null
        val i = cur
        if (i < 0 || taLoading) return               // while loading / after a failed load the drafts are not touched
        val d = env.cfg.drafts
        val k = dk(images[i].stem); val v = text
        if (v != orig) d.put(k, JSONObject().put("t", v).put("ts", System.currentTimeMillis()))
        else if (d.has(k)) d.remove(k)
        else return
        persistDrafts(); draftRev++
    }

    /** React `onInput` — the editor calls this on every change: the draft is saved 0.3 s after typing stops. */
    fun onTextChange(v: String) {
        text = v
        onTtsStale()                                  // React `tts.markStale()`
        draftJob?.cancel()
        draftJob = scope.launch { delay(300); draftJob = null; stashDraft() }
    }

    /** Page closing (React cleanup `clearTimeout(draftT.current)`). */
    fun cancelDraftTimer() { draftJob?.cancel(); draftJob = null }

    // ---------------- last position (scroll / cursor) ----------------
    private var viewJob: Job? = null

    /** React `saveView` — remember where I stopped (image scroll, text scroll, cursor) in the config (silent: no recomposition). */
    fun saveView() {
        viewJob?.cancel(); viewJob = null
        val i = cur
        val pos = viewPos()
        if (i < 0 || !viewReady || pos == null) return
        val stem = images[i].stem
        env.cfg.setCfg(silent = true) { c ->
            val n = JSONObject(c.toString())
            n.put("view", JSONObject().put("stem", stem).put("it", pos.it).put("il", pos.il).put("tt", pos.tt).put("c", pos.c))
            n
        }
    }

    /** React `lazyView` — scroll / key / tap events: save 0.4 s after the last one. */
    fun lazyView() {
        viewJob?.cancel()
        viewJob = scope.launch { delay(400); viewJob = null; saveView() }
    }

    /** React `flush` = `stashDraft(); saveView()` — when changing page, app going to background, leaving. */
    fun flush() { stashDraft(); saveView() }

    /** React cleanup of the page (`useLayoutEffect` return): keep everything on the phone, stop reading aloud, clear timers. */
    fun onLeave() {
        flush(); onStopTts()
        cancelDraftTimer(); viewJob?.cancel(); viewJob = null
    }

    // ---------------- helpers (React one-liners) ----------------
    fun isHid(i: Int): Boolean = i >= 0 && i < images.size && hiddenStem(images[i].stem)

    /** First visible page from [from] in direction [dir] (+1 / -1); -1 when none. */
    fun stepVis(from: Int, dir: Int): Int {
        var i = from + dir
        while (i >= 0 && i < images.size) { if (!isHid(i)) return i; i += dir }
        return -1
    }

    /** React `putText`: the text box normalises new lines itself, so the normalised value is what we keep. */
    fun putText(v: String): String { val nv = ViewUtils.normNl(v); text = nv; return nv }

    private fun showImgMsg(t: String) { ldrOn = false; imgMsg = t }
    private fun ldrShow(pct: Float?) { ldrKey++; imgMsg = ""; ldrOn = true; ldrPct = pct }

    /** React `imgProg(name, pct)` — only for the image that is open now, and only while the loader is shown. */
    private fun imgProg(name: String, pct: Float?) {
        if (images.getOrNull(cur)?.name == name && ldrOn) ldrPct = pct
    }

    /** React `openLightbox` — tap on the image. */
    fun openLightbox() { if (cur >= 0) lightbox.openLightbox(image, currentName) }

    // ---------------- my finished tasks ----------------
    /** React `loadDone` — my proofed-page count (all books). Calls made during a run are merged into one more run afterwards. */
    suspend fun loadDone() {
        if (doneBusy) { doneAgain = true; return }
        doneBusy = true
        try {
            var n: Int
            try {   // fast path: Supabase function
                val r = Sb.post("rpc/my_proof_count", JSONObject().put("p_user", ctx.uid))
                val v = r.opt(0)
                val d = (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull() ?: throw IllegalStateException("bad")
                if (!d.isFinite()) throw IllegalStateException("bad")
                n = d.toInt()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {   // function missing: count directly
                n = 0
                var off = 0
                while (true) {
                    val r = Sb.get("page_proofs?user_id=eq.${enc(ctx.uid)}&select=stem&order=stem.asc&limit=1000&offset=$off")
                    n += r.length()
                    if (r.length() < 1000) break
                    off += 1000
                }
            }
            doneCnt = n
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            // table missing / network problem: keep the old number
        } finally {
            doneBusy = false
            if (doneAgain) { doneAgain = false; scope.launch { loadDone() } }
        }
    }

    // ---------------- page load ----------------
    /** React `loadPage` — loads the image (cache / GitHub) and the text (`<textsDir>/<stem>.txt`) of page [cur] in parallel. */
    suspend fun loadPage() {
        onStopTts()                                  // page changes: stop reading aloud
        val my = ++seq
        val im = images.getOrNull(cur) ?: return
        // the last closing position is restored only on the very first load
        val cv = env.cfg.cfg.optJSONObject("view")
        val pv = if (restoreOnce && cv != null && cv.str("stem") == im.stem) cv else null
        restoreOnce = false
        viewReady = false; loadErr = false
        lightbox.closeLightbox()
        putText(""); taLoading = true; placeholder = ""; orig = ""; sha = null
        // a page that is already decoded in memory shows at once, without the loading ring
        val hit = env.imgCache.peekBitmap(im.name)
        image = hit
        if (hit == null) ldrShow(if (env.imgCache.has(im.name)) env.imgCache.pct(im.name) else 0f)
        else { imgMsg = ""; ldrOn = false }
        updateStatus()

        coroutineScope {
            val imgP = async {
                try {
                    val bmp = env.imgCache.getBitmap(im.name)      // bitmap cache -> disk -> GitHub; decoded off the main thread
                    if (my != seq) return@async
                    if (ldrOn) ldrPct = 100f
                    if (bmp == null) showImgMsg("ছবি দেখানো যায়নি") else {
                        image = bmp
                        showImgMsg("")
                        imgScrollX = pv?.optInt("il", 0) ?: 0; imgScrollY = pv?.optInt("it", 0) ?: 0; imgScrollTick++
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (my == seq) showImgMsg("ছবি লোড ব্যর্থ: " + (e.message ?: ""))
                }
            }
            val txtP = async { loadText(my, im) }
            awaitAll(imgP, txtP)
        }
        if (my != seq) return
        if (pv != null && !taLoading) {
            textCursor = minOf(pv.optInt("c", 0), text.length)
            textScroll = pv.optInt("tt", 0); textRestoreTick++
        }
        viewReady = true
        onPageLoaded()                               // empty text: API-1 may start by itself
        // pre-load around this page: the next one is also decoded (instant page turn); the one after it and the previous one only
        // go to the disk cache. With battery saver on only the next page is loaded (less network / battery).
        val nx = stepVis(cur, 1)
        if (nx >= 0) env.imgCache.prefetch(images[nx].name, decode = true)
        if (!MotionStore.powerSave) {
            val nx2 = if (nx >= 0) stepVis(nx, 1) else -1
            if (nx2 >= 0) env.imgCache.prefetch(images[nx2].name, decode = false)
            val pi = stepVis(cur, -1)
            if (pi >= 0) env.imgCache.prefetch(images[pi].name, decode = false)
        }
    }

    private suspend fun loadText(my: Int, im: ViewImageRef) {
        try {
            val gh = env.gh
            val r = gh.apiText("/contents/" + ViewUtils.epath(ViewConfig.repoPaths(env.cfg.cfg).textsDir + "/" + im.stem + ".txt") + gh.refQ())
            if (my != seq) return
            var ok = true
            if (r.code == 404) { sha = null; putText(""); orig = "" }              // file / folder missing -> empty box
            else if (r.ok) {
                val j = r.json()
                sha = j.str("sha")
                val c = j.str("content").orEmpty()
                orig = putText(if (c.isNotEmpty()) ViewUtils.fromB64(c) else "")    // the normalised value is the comparison base
            } else { ok = false; env.toaster.toast(gh.errMsg(r), true) }
            if (my != seq) return
            if (ok) {
                val k = ViewConfig.draftKey(env.cfg.cfg, im.stem)
                val d = env.cfg.drafts.optJSONObject(k)
                if (d != null && ViewUtils.normNl(d.str("t")) != orig) {
                    putText(d.str("t").orEmpty())
                    env.toaster.toast("অসংরক্ষিত খসড়া ফিরিয়ে আনা হয়েছে — সেভ করলে সংরক্ষিত হবে", false, 3500)
                } else if (d != null) { env.cfg.drafts.remove(k); persistDrafts() }
                taLoading = false; textScroll = 0; textCursor = 0; textRestoreTick++
                placeholder = "এই পেইজের টেক্সট এখনো নেই — এখানে টেক্সট পেস্ট করে সেভ করুন"
            } else loadErr = true
            updateStatus()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (my == seq) { loadErr = true; env.toaster.toast("টেক্সট লোড ব্যর্থ: " + (e.message ?: ""), true); updateStatus() }
        }
    }

    /** React `goPage(i)` — open page [i]: guards, keep the current draft, remember last page (config + the Task page's "continue" button), load. */
    suspend fun goPage(i: Int) {
        if (i < 0 || i >= images.size || i == cur) return
        if (isHid(i)) { env.toaster.toast("এই পেইজ অন্য ইউজার প্রুফড করেছেন — আপনি দেখতে পারবেন না", true); return }
        if (busy) { env.toaster.toast("সেভ চলছে, একটু অপেক্ষা করুন"); return }
        onStopTts()
        flush()                                      // the draft + position of the current page stay on the phone — nothing is lost
        cur = i
        val stem = images[i].stem
        env.cfg.setCfg(silent = true) { c ->
            val n = JSONObject(c.toString())
            n.put("last", stem)
            val v = c.optJSONObject("view")
            n.put("view", if (restoreOnce && v != null && v.str("stem") == stem) v else JSONObject.NULL)
            n
        }
        // for the Task page "continue" button: last book + page (this device only)
        try { Prefs.putString(resumeKey(ctx.uid), JSONObject().put("b", ctx.bookId).put("s", stem).put("t", System.currentTimeMillis()).toString()) } catch (_: Exception) { }
        loadPage()
    }

    // ---------------- save ----------------
    /**
     * React `saveText` — PUT `<textsDir>/<stem>.txt` to GitHub (the folder is created by GitHub if missing).
     * Returns true when saved or nothing to save, false otherwise. Failures keep the text safe in the draft.
     */
    suspend fun saveText(): Boolean {
        val i = cur
        if (i < 0 || taLoading) return false
        if (!isDirty()) return true
        if (busy) return false
        stashDraft()                                    // even if the network fails the text stays on the phone
        busy = true; statusOverride = "সেভ হচ্ছে…"
        var blocked = false
        try {
            if (!ownerOk(images[i].stem)) { blocked = true; return false }   // somebody else proofed it meanwhile
            val im = images[i]; val value = text
            val gh = env.gh
            val body = JSONObject().put("message", "Proofread " + im.stem + ".txt").put("content", ViewUtils.toB64(value))
            sha?.let { body.put("sha", it) }
            env.cfg.cfg.str("branch")?.takeIf { it.isNotEmpty() }?.let { body.put("branch", it) }
            val r = gh.apiText(
                "/contents/" + ViewUtils.epath(ViewConfig.repoPaths(env.cfg.cfg).textsDir + "/" + im.stem + ".txt"),
                method = "PUT", headers = mapOf("Content-Type" to "application/json"), body = body.toString()
            )
            if (!r.ok) {
                env.toaster.toast(
                    gh.errMsg(r) + (if (r.code == 409 || r.code == 422) " — রিলোড বাটন চেপে আবার চেষ্টা করুন (আপনার লেখা খসড়ায় নিরাপদ আছে)" else ""),
                    true, 7000
                )
                return false
            }
            val j = r.json()
            sha = j.getJSONObject("content").getString("sha"); orig = value; textSet.add(im.stem)
            stashDraft()                                // typed again during the save: stays a draft, else the draft is removed
            env.toaster.toast("সেভ হয়েছে")
            return true
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            env.toaster.toast("সেভ ব্যর্থ: " + (e.message ?: "") + " (লেখা খসড়ায় নিরাপদ আছে)", true)
            return false
        } finally {
            busy = false; updateStatus()
            if (blocked) onBlocked()
        }
    }

    // ---------------- init ----------------
    private fun isNoImg(n: String) = !Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(n)

    /**
     * React `init` — connect to the repo: token / permission check, file list (one `git/trees?recursive=1` call), page images, which `.txt`
     * exist, the old `proofed.json`, who proofed what (Supabase), then open the start page ([wantPage] link, else the last page, else the first
     * visible one). Errors: "সংযোগ ব্যর্থ: …" + settings dialog hook.
     */
    suspend fun init(wantPage: String?) {
        if (initing) return
        initing = true; initFailed = false; initError = ""
        autoTried.clear()
        flush()
        statusOverride = "লোড হচ্ছে…"
        try {
            val gh = env.gh
            val cfg = { env.cfg.cfg }
            if (cfg().str("token").isNullOrEmpty()) env.toaster.toast("টোকেন নেই — শুধু দেখা যাবে, সেভ হবে না (সেটিংস থেকে টোকেন দিন)", true, 4500)

            // default branch and write permission
            val ri = gh.apiText("")
            if (!ri.ok) throw Exception(gh.errMsg(ri))
            val info = ri.json()
            repoPrivate = info.optBoolean("private", false)
            defaultBranch = info.str("default_branch").orEmpty()
            val perms = info.optJSONObject("permissions")
            if (!cfg().str("token").isNullOrEmpty() && perms != null && perms.has("push") && !perms.optBoolean("push", true))
                env.toaster.toast(
                    if (ctx.isAdmin) "এই টোকেনে রিপোতে লেখার অনুমতি নেই (Contents: Read and write দিন)" else "এই টোকেনে সেভ করার অনুমতি নেই — টোকেন বদলে নিন",
                    true, 6000
                )
            val br = cfg().str("branch").takeUnless { it.isNullOrEmpty() } ?: defaultBranch

            // the whole file list in one request (works for more than 1000 pages too)
            val rt = gh.apiText("/git/trees/" + enc(br) + "?recursive=1")
            if (!rt.ok) throw Exception(gh.errMsg(rt))
            val tj = rt.json()
            if (tj.optBoolean("truncated", false))
                env.toaster.toast(if (ctx.isAdmin) "সতর্কতা: রিপো খুব বড়, ফাইল তালিকা অসম্পূর্ণ হতে পারে" else "সতর্কতা: পেইজ তালিকা অসম্পূর্ণ হতে পারে", true)
            val tree = tj.optJSONArray("tree")
            val blobs = ArrayList<String>()
            if (tree != null) for (k in 0 until tree.length()) {
                val e = tree.optJSONObject(k) ?: continue
                if (e.optString("type") == "blob") blobs.add(e.optString("path"))
            }

            val P = ViewConfig.repoPaths(cfg())
            val pp = P.pagesDir + "/"; val tp = P.textsDir + "/"
            fun direct(path: String, pre: String) = path.startsWith(pre) && path.indexOf('/', pre.length) < 0

            val imgs = blobs.filter { direct(it, pp) && !isNoImg(it) }
                .map { val name = it.substring(pp.length); ViewImageRef(name, name.replace(Regex("\\.[^.]+$"), "")) }
                .sortedWith { a, b -> ViewUtils.natCmp(a.name, b.name) }
            if (imgs.isEmpty()) throw Exception(P.pagesDir + "/ ফোল্ডারে কোনো ছবি নেই")

            val ts = mutableSetOf<String>()
            for (b in blobs) if (direct(b, tp) && b.endsWith(".txt", ignoreCase = true)) ts.add(b.substring(tp.length).replace(Regex("\\.txt$", RegexOption.IGNORE_CASE), ""))
            if (blobs.none { it.startsWith(tp) })
                env.toaster.toast(
                    if (ctx.isAdmin) P.textsDir + "/ ফোল্ডার এখনো নেই — প্রথম সেভ করলেই নিজে থেকে তৈরি হবে" else "এখনো কোনো টেক্সট সেভ হয়নি — প্রথম সেভ করলেই শুরু হবে",
                    false, 4500
                )

            var ps = mutableSetOf<String>()
            suspend fun readProof(path: String): MutableSet<String>? {
                val r3 = gh.apiText("/contents/" + ViewUtils.epath(path) + gh.refQ())
                if (!r3.ok) return null
                val out = mutableSetOf<String>()
                try {
                    val arr = JSONObject(ViewUtils.fromB64(r3.json().str("content").orEmpty())).optJSONArray("proofed")
                    if (arr != null) for (k in 0 until arr.length()) out.add(arr.optString(k))
                } catch (_: Exception) { /* ignore */ }
                return out
            }
            legacyProof = false
            if (blobs.any { it == P.proofPath }) {
                readProof(P.proofPath)?.let { ps = it }
            } else if (P.proofDirName.isNotEmpty() && blobs.any { it == P.legacyProofPath }) {
                // old place (repo root): the first save creates the file in the new folder
                readProof(P.legacyProofPath)?.let { ps = it; legacyProof = true }
            }

            images = imgs; textSet = ts; proofed = ps
            owners = mutableMapOf()
            val pr = refreshProofs()                                      // who proofed which page (Supabase)
            if (pr == ProofRefresh.Failed) throw Exception("প্রুফড মালিকানা আনা যায়নি (নেটওয়ার্ক) — রিলোড চাপুন")
            if (pr == ProofRefresh.NoTable)
                env.toaster.toast(
                    if (ctx.isAdmin) "Supabase-এ page_proofs টেবিল নেই — rizwantask_full_schema.sql চালান; তার আগে ইউজার-ভিত্তিক লুকানো কাজ করবে না" else "প্রুফড মালিকানা সক্রিয় নেই (অ্যাডমিনকে জানান)",
                    true, 7000
                )
            cur = -1; listRev++
            val want = wantPage.takeUnless { it.isNullOrEmpty() } ?: cfg().str("last").orEmpty()   // link page, else where you were
            var start = imgs.indexOfFirst { it.stem == want }
            if (start < 0 || isHid(start)) start = stepVis(-1, 1)         // else the first visible page
            if (start < 0) { onShowNone(); return }
            restoreOnce = true
            goPage(start)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            initFailed = true; initError = e.message ?: ""
            statusOverride = ""
            env.toaster.toast("সংযোগ ব্যর্থ: " + (e.message ?: ""), true)
            onOpenSettings()
        } finally { initing = false }
    }

    /**
     * React `applyConnChange` — repo / branch / book / folders changed: reset last page + position, clear the image cache, force a new
     * connection (so an old list never saves into the wrong folder).
     */
    fun applyConnChange() {
        val sig = ViewConfig.connSig(env.cfg.cfg)
        if (sig != connSig) {
            env.cfg.setCfg { c -> JSONObject(c.toString()).put("last", "").put("view", JSONObject.NULL) }
            connSig = sig
            env.imgCache.clear(); forceInit = true
        }
    }

    /** The logic of React `onSettingsClose` (dialog closed): connect if the connection changed, or the first time (no list yet). */
    suspend fun afterSettingsClosed(wantPage: String?) {
        applyConnChange()
        if (forceInit || (images.isEmpty() && !initFailed && !initing)) { forceInit = false; init(wantPage) }
    }

    /** React mount effect: `loadDone(); admin without token -> openSettings() else init()`. */
    suspend fun start(wantPage: String?) {
        scope.launch { loadDone() }
        if (env.cfg.cfg.str("token").isNullOrEmpty() && ctx.isAdmin) onOpenSettings() else init(wantPage)
    }

    /** React reload button: `imgCache.clear(); init()` — here also the disk copies are dropped, so the pages come fresh from GitHub. */
    suspend fun reload(wantPage: String?) { env.imgCache.clearAll(); init(wantPage) }

    // ---------------- who proofed which page (Supabase: page_proofs) ----------------
    // A page has exactly one owner. Whoever proofed it sees it (and the admin); for everybody else it is hidden from the list,
    // buttons and "next unproofed".

    /** React `isNoTable` — error text of a missing table. */
    private fun isNoTable(e: Exception) = Regex("PGRST205|PGRST200|42P01|relation .*page_proofs|404", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())

    /** React `fetchProofRows` — every proofed page of this book, 1000 per request (older tables without review columns still work). */
    private suspend fun fetchProofRows(): List<JSONObject> {
        val rows = ArrayList<JSONObject>(); val page = 1000; var off = 0
        while (true) {
            val q = "page_proofs?book_id=eq." + enc(ctx.bookId) + "&order=stem.asc&limit=" + page + "&offset=" + off
            val r = try { Sb.get("$q&select=stem,user_id,review_status,review_note") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (Regex("review_|42703|PGRST204", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) Sb.get("$q&select=stem,user_id") else throw e
            }
            for (k in 0 until r.length()) r.optJSONObject(k)?.let { rows.add(it) }
            if (r.length() < page) break
            off += page
        }
        return rows
    }

    /**
     * React `refreshProofs` — read the owners again: [ProofRefresh.Ok] updated, [ProofRefresh.Failed] could not (network),
     * [ProofRefresh.NoTable] the table is missing. Pages whose owner removed the mark leave [proofed]; the admin also gets user names.
     */
    suspend fun refreshProofsNow(): ProofRefresh {
        val rows = try { fetchProofRows() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (isNoTable(e)) { proofDb = false; owners = mutableMapOf(); listRev++; return ProofRefresh.NoTable }
            return ProofRefresh.Failed
        }
        proofDb = true
        val m = mutableMapOf<String, String>(); val rv = mutableMapOf<String, Review>()
        for (r in rows) {
            val stem = r.str("stem") ?: continue
            m[stem] = r.str("user_id").orEmpty(); proofed.add(stem)
            val st = r.str("review_status")
            if (!st.isNullOrEmpty()) rv[stem] = Review(st, r.str("review_note").orEmpty())
        }
        reviews = rv
        for (stem in owners.keys.toList()) if (!m.containsKey(stem)) proofed.remove(stem)   // the owner took the mark away
        owners = m
        if (ctx.isAdmin) {
            try {
                val us = Sb.get("app_users?select=id,full_name")
                for (k in 0 until us.length()) us.optJSONObject(k)?.let { ownerNames[it.str("id").orEmpty()] = it.str("full_name").orEmpty() }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* ignore */ }
        }
        listRev++
        return ProofRefresh.Ok
    }

    /**
     * React `ownerOk(stem)` — before save / proof: check with fresh data that nobody else proofed this page meanwhile
     * (a network failure does not block). Admin and the old no-table mode always pass.
     */
    suspend fun ownerOkNow(stem: String): Boolean {
        if (!proofDb || ctx.isAdmin) return true
        try {
            val r = Sb.get("page_proofs?book_id=eq." + enc(ctx.bookId) + "&stem=eq." + enc(stem) + "&select=user_id&limit=1").optJSONObject(0)
            if (r != null && r.str("user_id") != ctx.uid) {
                owners[stem] = r.str("user_id").orEmpty(); proofed.add(stem)
                env.toaster.toast("এই পেইজ অন্য ইউজার প্রুফড করেছেন — সেভ হয়নি (আপনার লেখা খসড়ায় নিরাপদ আছে)", true, 6000)
                return false
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* ignore */ }
        return true
    }

    // ---------------- applying the owners (4-ঙ-২) ----------------

    /** React `hiddenStem(stem)` — a page proofed by another user is hidden (the admin sees everything; no table = nothing hidden). */
    fun hiddenStemNow(stem: String): Boolean = !ctx.isAdmin && proofDb && owners.containsKey(stem) && owners[stem] != ctx.uid

    /** React `ownerTag(stem)` — the admin sees the owner's name on another user's page (": নাম"). */
    fun ownerTag(stem: String): String {
        if (!ctx.isAdmin) return ""
        val o = owners[stem] ?: return ""
        if (o == ctx.uid) return ""
        val n = ownerNames[o]
        return if (n.isNullOrEmpty()) "" else ": $n"
    }

    /** React `visIdx()` — indexes of the pages this user may see. */
    fun visIdx(): List<Int> = images.indices.filter { !isHid(it) }

    /**
     * React `showNone` — nothing is left for this user (every page belongs to somebody else): stop reading, empty the editor and the image,
     * show the message where the image goes. [cur] = -1 (no page is open); a late answer of the old page is dropped (`seq`).
     */
    fun showNone() {
        ++seq; onStopTts(); cur = -1; viewReady = false
        lightbox.closeLightbox()
        putText(""); taLoading = true; placeholder = ""; orig = ""; sha = null
        image = null
        showImgMsg("আপনার জন্য কোনো পেইজ বাকি নেই — সব পেইজ অন্য ইউজার প্রুফড করেছেন")
        updateStatus()
    }

    /**
     * React `applyProofs` — the owners changed: the list is arranged again (`listRev`), the page-list dropdown closes; if the open page became
     * somebody else's, move to the nearest visible page (next, else previous) — the unsaved text stays in the draft (`goPage` → `flush`) —
     * or [showNone] when no page is left. Not `suspend`: the page change runs in the page's scope (React does not wait for `goPage` either).
     */
    fun applyProofs() {
        onCloseDropdown(); listRev++
        val c = cur
        if (c >= 0 && isHid(c)) {
            val nx = if (stepVis(c, 1) >= 0) stepVis(c, 1) else stepVis(c, -1)
            env.toaster.toast("এই পেইজ অন্য ইউজার প্রুফড করেছেন — অন্য পেইজে নেওয়া হলো (আপনার অসংরক্ষিত লেখা খসড়ায় আছে)", true, 6000)
            if (nx >= 0) scope.launch { goPage(nx) } else showNone()
        } else updateStatus()
    }

    /**
     * React `syncProofs` (inside the visibility / 60 s effect) — when the app is back in front, and every 60 s while it is: read the owners again
     * (other users proofed more pages) and apply them. Skipped while connecting, saving, or before the page list exists.
     */
    suspend fun syncProofs() {
        if (initing || busy || images.isEmpty()) return
        if (refreshProofs() == ProofRefresh.Ok) applyProofs()
    }

    // ---------------- proofed mark: set / remove (4-ঙ-৩) ----------------

    /**
     * React `syncProofJson` — add / remove just this one page in the GitHub proofed list (`proofed.json`). The fresh file is read every time, so
     * other users' entries are never wiped. A new file starts from the list known now. Retries once only on a `sha` clash (409 / 422).
     * Throws with the GitHub error text when it fails.
     */
    private suspend fun syncProofJson(stem: String, v: Boolean) {
        var lastErr: Exception? = null
        for (att in 0 until 2) {
            val P = ViewConfig.repoPaths(env.cfg.cfg)
            val r0 = env.gh.apiText("/contents/" + ViewUtils.epath(P.proofPath) + env.gh.refQ())
            val set: MutableSet<String>; var sh: String? = null
            if (r0.ok) {
                val j = r0.json(); sh = j.str("sha"); set = mutableSetOf()
                try {
                    val arr = JSONObject(ViewUtils.fromB64(j.str("content").orEmpty())).optJSONArray("proofed")
                    if (arr != null) for (k in 0 until arr.length()) set.add(arr.optString(k))
                } catch (_: Exception) { /* ignore */ }
            } else if (r0.code == 404) set = proofed.toMutableSet()          // new file: start from the list known now
            else throw Exception(env.gh.errMsg(r0))
            if (v) set.add(stem) else set.remove(stem)
            val list = set.sortedWith { a, b -> ViewUtils.natCmp(a, b) }
            val body = JSONObject()
                .put("message", "Proofing status: $stem")
                .put("content", ViewUtils.toB64(JSONObject().put("proofed", JSONArray(list)).toString(2) + "\n"))
            if (sh != null) body.put("sha", sh)
            val br = env.cfg.cfg.str("branch").orEmpty()
            if (br.isNotEmpty()) body.put("branch", br)
            val r = env.gh.apiText("/contents/" + ViewUtils.epath(P.proofPath), method = "PUT", headers = mapOf("Content-Type" to "application/json"), body = body.toString())
            if (r.ok) {
                if (legacyProof) {
                    legacyProof = false
                    if (ctx.isAdmin) env.toaster.toast("প্রুফড তালিকা এখন " + P.proofPath + "-এ সংরক্ষিত — রিপোর রুটের পুরনো " + P.legacyProofPath + " চাইলে মুছে ফেলতে পারেন", false, 7000)
                }
                return
            }
            lastErr = Exception(env.gh.errMsg(r))
            if (r.code != 409 && r.code != 422) break                     // retry only on a sha clash
        }
        throw lastErr ?: Exception("প্রুফড তালিকা সেভ ব্যর্থ")
    }

    /**
     * React `setProofedFlag(stem, val)` — put / take away the proofed mark. Returns true when done.
     * With the table ([proofDb]): the row in `page_proofs` is the truth — insert (a duplicate means somebody else was first: the page leaves this
     * user's list, [applyProofs]) or delete (a user cannot remove the mark of an admin-approved page; the admin may remove any). Then the GitHub
     * list is updated; with the table, a failure there only warns (the mark itself is saved), in the old mode (no table) it is an error.
     */
    suspend fun setProofedFlag(stem: String, v: Boolean): Boolean {
        val refresh = { listRev++; updateStatus() }
        if (proofDb) {
            try {
                if (v) {
                    try {
                        Sb.post("page_proofs", JSONObject().put("book_id", ctx.bookId).put("stem", stem).put("user_id", ctx.uid))
                        owners[stem] = ctx.uid
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        if (!Regex("409|23505|duplicate", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) throw e
                        val r = Sb.get("page_proofs?book_id=eq." + enc(ctx.bookId) + "&stem=eq." + enc(stem) + "&select=user_id&limit=1").optJSONObject(0)
                        if (r != null) owners[stem] = r.str("user_id").orEmpty()
                        if (r != null && r.str("user_id") != ctx.uid && !ctx.isAdmin) {      // somebody else proofed it first
                            proofed.add(stem); applyProofs()
                            env.toaster.toast("এই পেইজ অন্য ইউজার আগেই প্রুফড করেছেন — আপনি এটি প্রুফড করতে পারবেন না", true, 6000)
                            return false
                        }
                    }
                } else {
                    if (!ctx.isAdmin && reviews[stem]?.s == "approved") {
                        env.toaster.toast("এই পেইজ অ্যাডমিন অনুমোদন করেছেন — প্রুফড চিহ্ন তোলা যাবে না", true, 5000)
                        return false
                    }
                    Sb.del("page_proofs?book_id=eq." + enc(ctx.bookId) + "&stem=eq." + enc(stem) + (if (ctx.isAdmin) "" else "&user_id=eq." + enc(ctx.uid)))
                    owners.remove(stem)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                env.toaster.toast("প্রুফড তালিকা সেভ ব্যর্থ: " + (e.message ?: ""), true)
                refresh(); return false
            }
        }
        var jerr: Exception? = null
        try { syncProofJson(stem, v) } catch (e: CancellationException) { throw e } catch (e: Exception) { jerr = e }
        if (jerr != null && !proofDb) {                                  // old mode: GitHub is the only storage
            env.toaster.toast("প্রুফড তালিকা সেভ ব্যর্থ: " + (jerr.message ?: ""), true)
            refresh(); return false
        }
        if (v) proofed.add(stem) else proofed.remove(stem)
        if (proofDb) {
            scope.launch { loadDone() }
            scope.launch { ActivityLog.log(if (v) "proof" else "unproof", (ctx.bookName.ifEmpty { ctx.bookId }) + " / " + stem) }
        }
        if (!v) reviews.remove(stem) else if (!reviews.containsKey(stem)) reviews[stem] = Review("pending", "")
        if (jerr != null) env.toaster.toast("প্রুফড চিহ্ন সংরক্ষিত হয়েছে, কিন্তু প্রুফড তালিকা হালনাগাদ হয়নি: " + (jerr.message ?: ""), true, 6000)
        refresh()
        return true
    }

    /** React `toggleProofClick` — the proofed button: flips the mark of the open page (nothing when no page is open). */
    suspend fun toggleProofClick() {
        if (cur < 0) return
        val stem = images[cur].stem
        setProofedFlag(stem, !proofed.contains(stem))
    }

    // ---------------- "save and next" (4-ঙ-৪) ----------------

    /** React `proofBusy` — the "সেভ ও পরের" button shows busy while [proofAndNext] runs (also stops a second tap: `proofRunning`). */
    var proofBusy by mutableStateOf(false); private set

    /**
     * React `proofAndNext` — "সেভ ও পরের": save the text, put the proofed mark, open the next visible page, with the loading toast
     * (steps 1..3). Stops quietly (toast hidden) when a step fails. When the mark is already there and the admin had asked for a correction on
     * the user's own page, saving again sends it for review again (`review_status = pending`, note and time cleared, log `resubmit`).
     * An empty page needs a yes first ([confirm]). On the last visible page there is no next step: "শেষ পেইজ — সেভ ও প্রুফড হয়েছে".
     */
    suspend fun proofAndNext() {
        if (cur < 0 || taLoading || proofBusy) return
        if (sha == null && text.trim().isEmpty() && !confirm("এই পেইজে কোনো টেক্সট নেই। তবুও প্রুফড চিহ্নিত করবেন?")) return
        proofBusy = true
        val t = env.toaster
        try {
            // custom dialog: "৩০ সেকেন্ড অপেক্ষা করুন...।" with a countdown; the work below starts only when it reaches zero
            if (!runSaveWait()) { t.toast("সেভ ও পরের — বাতিল করা হয়েছে"); return }
            if (cur < 0 || taLoading) return                  // (the page can change meanwhile, e.g. another user proofed it)
            proofRun(t)
        } finally {
            proofBusy = false
        }
    }

    /** Seconds of the "সেভ ও পরের" countdown dialog. */
    companion object { const val SAVE_WAIT_SECONDS = 30 }

    /** Seconds left while the countdown dialog is open (null = no dialog); shown by `ViewSaveWaitDialog`. */
    var saveWait by mutableStateOf<Int?>(null); private set
    private var saveWaitCancel: CompletableDeferred<Unit>? = null

    /** Back button on the countdown dialog: stop waiting, nothing is saved. */
    fun cancelSaveWait() { saveWaitCancel?.complete(Unit) }

    /** Counts [SAVE_WAIT_SECONDS] .. 1 (one second each); true = finished, false = cancelled. */
    private suspend fun runSaveWait(): Boolean {
        val cancel = CompletableDeferred<Unit>()
        saveWaitCancel = cancel
        try {
            for (sec in SAVE_WAIT_SECONDS downTo 1) {
                saveWait = sec
                if (withTimeoutOrNull(1000L) { cancel.await() } != null) return false
            }
            return true
        } finally {
            saveWait = null
            saveWaitCancel = null
        }
    }

    /** The real work of [proofAndNext] (after the countdown): save text, proofed mark, next page. */
    private suspend fun proofRun(t: ViewToaster) {
        val i = cur; val stem = images[i].stem; val nxt = stepVis(i, 1); val isLast = nxt < 0; val needProof = !proofed.contains(stem)
        var k = 0
        t.loadingStart(1 + (if (needProof) 1 else 0) + (if (isLast) 0 else 1))
        try {
            t.loadingStep(++k, if (isDirty()) "টেক্সট সেভ হচ্ছে…" else "সেভ যাচাই হচ্ছে…")
            if (!saveText()) { t.loadingHide(); return }
            if (needProof) {
                t.loadingStep(++k, "প্রুফড চিহ্নিত হচ্ছে…")
                if (!setProofedFlag(stem, true)) { t.loadingHide(); return }
            } else {
                val rv = reviews[stem]
                if (proofDb && rv != null && rv.s == "correction" && owners[stem] == ctx.uid) {
                    // the admin said "needs correction" -> fixed and sent again: the review is "pending" again
                    try {
                        Sb.patch(
                            "page_proofs?book_id=eq." + enc(ctx.bookId) + "&stem=eq." + enc(stem) + "&user_id=eq." + enc(ctx.uid),
                            JSONObject().put("review_status", "pending").put("review_note", JSONObject.NULL).put("reviewed_at", JSONObject.NULL)
                        )
                        reviews[stem] = Review("pending", ""); listRev++; updateStatus()
                        scope.launch { ActivityLog.log("resubmit", (ctx.bookName.ifEmpty { ctx.bookId }) + " / " + stem) }
                        t.toast("সংশোধিত পেইজ আবার রিভিউয়ের জন্য জমা হয়েছে")
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { t.toast("পুনরায় জমা ব্যর্থ: " + (e.message ?: ""), true) }
                }
            }
            if (!isLast) {
                t.loadingStep(++k, "পরের পেইজ লোড হচ্ছে…")
                goPage(nxt)
                t.loadingDone("সেভ ও প্রুফড হয়েছে • পরের পেইজ খোলা হয়েছে")
            } else t.loadingDone("শেষ পেইজ — সেভ ও প্রুফড হয়েছে")
        } finally {
            if (t.isLoading && !t.isFinishing) t.loadingHide()
        }
    }

    // ---------------- copy / paste / AI prompt (4-চ-১) ----------------

    /**
     * React `insertAtStart(value, replaceAll, quiet)` — put [value] into the editor: [replaceAll] = the whole old text is replaced, else the
     * selection (or the cursor place) is. The cursor then stays at the start of the inserted text. With auto heading on, headings get a leading
     * `1` first. Returns the number of headings marked (-1 = auto heading off or nothing inserted). Not quiet: toast with the result.
     * (Undo is the editor's own; the draft is saved 0.3 s later like for typing.)
     */
    fun insertAtStart(value0: String, replaceAll: Boolean, quiet: Boolean = false): Int {
        if (value0.isEmpty()) return -1
        var value = value0; var hn = -1
        if (env.cfg.cfg.optBoolean("autoHead", false)) { val r = markHeadings(value); value = r.text; hn = r.n }
        val old = text
        val sel = if (replaceAll) 0 to old.length else textSelection().let { (a, b) -> minOf(a, b).coerceIn(0, old.length) to maxOf(a, b).coerceIn(0, old.length) }
        val s = sel.first; val e = sel.second
        onTextChange(ViewUtils.normNl(old.substring(0, s) + value + old.substring(e)))
        textCursor = s                                               // cursor at the start of the inserted text
        textScroll = if (s == 0) 0 else (viewPos()?.tt ?: textScroll)
        textRestoreTick++
        if (!quiet) {
            if (replaceAll) env.toaster.toast("আগের টেক্সট মুছে নতুন টেক্সট বসানো হয়েছে" + (if (hn >= 0) " • ${hn}টি হেডিং চিহ্নিত (1 বসানো)" else ""))
            else if (hn >= 0) env.toaster.toast("${hn}টি হেডিং চিহ্নিত হয়েছে (1 বসানো)")
        }
        lazyView()
        return hn
    }

    /** React `onTaPaste` — normal paste into the text box (long press / keyboard): replaces the selection, cursor at the start. */
    fun onEditorPaste(t: String) { if (t.isNotEmpty()) insertAtStart(t, false) }

    /** React `pasteClick` — paste button: the clipboard text replaces the whole old text. If the clipboard cannot be read, the whole text is selected so a hand paste replaces it. */
    fun pasteClick(context: Context) {
        if (cur < 0 || taLoading) return
        try {
            val cm = context.getSystemService(ClipboardManager::class.java)
            val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (t.isEmpty()) { env.toaster.toast("ক্লিপবোর্ড খালি", true); return }
            insertAtStart(t, true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            selectAllTick++
            env.toaster.toast("ক্লিপবোর্ড পড়া যায়নি — পুরো টেক্সট নির্বাচিত আছে, টেক্সট বক্সে চেপে ধরে পেস্ট দিন (আগের টেক্সট মুছে নতুনটি বসবে)", true, 6500)
        }
    }

    /** React `copyClick` — copy button: the selected part, or the whole text when nothing is selected. */
    fun copyClick(context: Context) {
        if (cur < 0) return
        val (a0, b0) = textSelection()
        val a = minOf(a0, b0).coerceIn(0, text.length); val b = maxOf(a0, b0).coerceIn(0, text.length)
        val part = a != b
        val value = if (part) text.substring(a, b) else text
        if (value.isEmpty()) { env.toaster.toast("কপি করার মতো টেক্সট নেই", true); return }
        val ok = copyText(context, value)
        env.toaster.toast(if (ok) (if (part) "নির্বাচিত অংশ কপি হয়েছে" else "পুরো টেক্সট কপি হয়েছে") else "কপি ব্যর্থ", !ok)
    }

    /** React `discardClick` — the বাতিল button: the unsaved changes / draft of this page are dropped and the repo text comes back (after a yes). */
    suspend fun discardClick() {
        if (cur < 0 || taLoading) return
        if (!isDirty()) { env.toaster.toast("বাতিল করার মতো অসংরক্ষিত পরিবর্তন নেই"); return }
        if (!confirm("এই পেইজের অসংরক্ষিত পরিবর্তন/খসড়া বাতিল করে রিপোর টেক্সট ফিরিয়ে আনবেন?")) return
        putText(orig); stashDraft(); updateStatus(); env.toaster.toast("খসড়া বাতিল হয়েছে")
    }

    /** React `buildAiPrompt(im)` — GitHub download part + the raw link of this page's image + the OCR prompt (API-1 prompt from the settings, else the default). */
    fun buildAiPrompt(im: ViewImageRef, withLink: Boolean = true): String =
        if (!withLink) aiOnlyPrompt() else listOf(
            "# UNIVERSAL MASTER PROMPT (v8.1): GitHub Public Image Fetch + Faithful OCR / Retyping, Any Language, Plain Text",
            "",
            "## ROLE",
            "You are an expert at fetching public images from GitHub and an expert OCR transcriber for ANY language and script (Bangla, Arabic, Urdu, Persian, English etc.). You download the image first, then retype it with 100% fidelity.",
            "",
            "## TASK",
            "1. Download the image from the provided GitHub URL.",
            "2. If the URL is a `github.com/.../blob/...` link, you MUST convert it to `raw.githubusercontent.com` link first. Handle URL-encoded Bengali folder names correctly (keep %E0%A6... encoding).",
            "3. Carefully read the downloaded image and retype ALL of the body content into clean, editable digital text. Do not summarize, translate, or correct.",
            "",
            "Input URL for this task:",
            "`" + env.gh.rawImageUrl(im.name, defaultBranch) + "`"
        ).joinToString("\n") + "\n\n" +
            (env.cfg.cfg.optJSONObject("api1")?.str("prompt").orEmpty().ifBlank { ViewConfig.API_DEF.getValue(1).prompt }).trim() + "\n"

    /** The OCR prompt alone (no GitHub download part, no image link) — used when the admin switched the GitHub link off. */
    private fun aiOnlyPrompt(): String =
        (env.cfg.cfg.optJSONObject("api1")?.str("prompt").orEmpty().ifBlank { ViewConfig.API_DEF.getValue(1).prompt }).trim() + "\n"

    /** React `aiCopy` — the AI prompt button: copies the prompt of the open page. A private repo gets a warning (the AI cannot open the link). */
    fun aiCopy(context: Context) {
        if (cur < 0) { env.toaster.toast("কোনো ছবি খোলা নেই", true); return }
        val im = images[cur]
        // admin switch (settings tab): GitHub link on = link + prompt, off = the prompt only; read fresh, then copy
        scope.launch {
            val withLink = env.apiFlag.refreshAiLink()
            aiCopyDo(context, im, withLink)
        }
    }

    private fun aiCopyDo(context: Context, im: ViewImageRef, withLink: Boolean) {
        if (!copyText(context, buildAiPrompt(im, withLink))) { env.toaster.toast("কপি ব্যর্থ — ক্লিপবোর্ডে লেখা যাচ্ছে না", true); return }
        // one fixed message for every case — no GitHub / link / repo details are shown to the user (security)
        env.toaster.toast("প্রম্পট কপি করা হয়েছে।", false, 3000)
        // after the copy: ask whether to open the AI site in the browser (unless "আর দেখাবে না" was chosen)
        if (Prefs.getString(AI_ASK_OFF_KEY) != "1") aiAsk = true
    }

    /** "Open in browser?" dialog after [aiCopy] (shown by `ViewAiOpenDialog`). */
    var aiAsk by mutableStateOf(false); private set
    /** "না" / outside tap / back — just close. */
    fun aiAskDismiss() { aiAsk = false }
    /** "আর দেখাবে না" — remember on this device: from now on the button only copies. */
    fun aiAskNever() { Prefs.putString(AI_ASK_OFF_KEY, "1"); aiAsk = false }

    // ---------------- auto heading switch (4-চ-২) ----------------

    /** React `autoHead` (config) — on: every pasted / API text gets a `1` before each heading. */
    val autoHead: Boolean get() = env.cfg.cfg.optBoolean("autoHead", false)

    /** React `toggleAutoHead` — the "হেডিং: চালু/বন্ধ" button: flips and remembers the switch, tells the new state. */
    fun toggleAutoHead() {
        val on = !autoHead
        env.cfg.setCfg(JSONObject().put("autoHead", on))
        env.toaster.toast(if (on) "অটো হেডিং চালু — পেস্টে হেডিংয়ের আগে 1 বসবে" else "অটো হেডিং বন্ধ")
    }

    // ---------------- keyboard shortcuts (4-চ-৩) ----------------

    /**
     * React `onKey` — shortcuts of a hardware keyboard (the phone's own keyboard has none). Returns true when the key was used:
     *  - Esc: closes the picture viewer (lightbox) when it is open;
     *  - Ctrl+S (or Meta+S): save the text ([saveText]);
     *  - Alt+Right / Alt+Left: next / previous visible page (hidden pages are skipped; no page = nothing happens).
     * The page UI puts this on the root with `Modifier.onPreviewKeyEvent { st.onKey(it) }`, so it works while the text box has the focus.
     */
    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.Escape && lightbox.open) { lightbox.closeLightbox(); return true }
        if ((e.isCtrlPressed || e.isMetaPressed) && e.key == Key.S) { scope.launch { saveText() }; return true }
        if (e.isAltPressed && e.key == Key.DirectionRight) { val n = stepVis(cur, 1); scope.launch { goPage(n) }; return true }
        if (e.isAltPressed && e.key == Key.DirectionLeft) { val n = stepVis(cur, -1); scope.launch { goPage(n) }; return true }
        return false
    }

    // ---------------- page list for the dropdown (4-চ-৪) ----------------

    /**
     * React `labelOf(im)` — text of a page in the list: stem, + " (প্রুফড[: নাম])" when proofed (the admin sees the owner's name for others'),
     * + " •" when a draft is kept, + " (txt নেই)" when the page has no text file yet.
     */
    fun labelOf(im: ViewImageRef): String =
        im.stem + (if (proofed.contains(im.stem)) " (প্রুফড" + ownerTag(im.stem) + ")" else "") +
            (if (hasDraft(im.stem)) " •" else "") + (if (textSet.contains(im.stem)) "" else " (txt নেই)")

    /**
     * React `pageOptions` — the dropdown items: every page this user may see, value = index in [images], label = [labelOf].
     * Reads [listRev] / [draftRev] so the list is made again when proofs, owners or drafts change.
     */
    fun pageOptions(): List<ViewDropdownOption> {
        listRev; draftRev
        val o = ArrayList<ViewDropdownOption>()
        images.forEachIndexed { i, im -> if (!isHid(i)) o.add(ViewDropdownOption(i.toString(), labelOf(im))) }
        return o
    }

    // ---------------- header buttons: jump / next unproofed (4-ঞ-৩-ক) ----------------

    /** A page change started by a button ([goPage] is suspend): the header's previous / next / list use this. */
    fun go(i: Int) { scope.launch { goPage(i) } }

    /**
     * React `jumpTo` — the "যাও" button / Enter in the page-number box. [raw] = what is typed; a number that is not a whole number or empty
     * does nothing. The page whose name ends with that number (`numOf`), else the n-th page of the list.
     */
    fun jumpTo(raw: String) {
        val n = raw.trim().toIntOrNull()?.takeIf { it != 0 } ?: return
        var i = images.indexOfFirst { ViewUtils.numOf(it.stem) == n.toLong() }
        if (i < 0 && n >= 1 && n <= images.size) i = n - 1
        if (i < 0) { env.toaster.toast("এই নম্বরের পেইজ নেই", true); return }
        if (isHid(i)) { env.toaster.toast("এই পেইজ অন্য ইউজার প্রুফড করেছেন — আপনি দেখতে পারবেন না", true); return }
        scope.launch { goPage(i) }
    }

    /** React `nextUnproofed` — the next visible page (wrapping round) that is not proofed; all proofed = a toast. */
    fun nextUnproofed() {
        val n = images.size
        for (k in 1..n) {
            val i = (cur + k).mod(n)
            if (!isHid(i) && !proofed.contains(images[i].stem)) { scope.launch { goPage(i) }; return }
        }
        env.toaster.toast("সব পেইজ প্রুফড!")
    }
}

/** Prefs key: the user chose "আর দেখাবে না" on the AI prompt browser dialog. */
private const val AI_ASK_OFF_KEY = "view_ai_open_ask_off"
/** The AI chat opened by "হ্যাঁ" — the copied prompt is pasted there. */
const val AI_CHAT_URL = "https://www.meta.ai/"

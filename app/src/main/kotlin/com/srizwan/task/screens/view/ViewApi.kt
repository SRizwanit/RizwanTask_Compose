package com.srizwan.task.screens.view

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewGemini
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.core.copyText
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** React `apiRun` = `{ n, stem, ctrl, chars }` — [job] is the running coroutine (cancel = React `ctrl.abort()`). */
class ApiRun(val n: Int, val stem: String, val job: Job?) {
    /** Characters received so far (updated at most every 0.4 s) — shown on the API button. */
    var chars by mutableIntStateOf(0)
}

/**
 * React `View.jsx` API (Gemini) part: API-1 = image -> text (OCR), API-2 = text proofing. Step 4-chha-2: `runApi` for API-1 +
 * `applyApiResult` + stop; step 4-chha-3: the API-2 branch, the API picker / config dialogs state (`pickOpen`, `cfgN`, `openApiCfg`,
 * `apiBtnClick`; the dialogs themselves are in `ApiDialogs.kt`). Step 4-chha-4: auto OCR (`maybeAutoOcr`, `toggleAutoApi`). The result goes straight into the editor like a paste (undo works, auto heading puts `1`); nothing is pushed to
 * the repo — the reader checks and saves.
 */
class ViewApi(private val env: ViewEnv, private val st: ViewPageState, private val scope: CoroutineScope, private val appContext: Context) {

    /** React `apiRun` (null = nothing running). Read by the UI (API button spinner / character count). */
    var run: ApiRun? by mutableStateOf(null)
        private set

    /** React `autoPause` — set when an auto run fails with a key / model / quota error; cleared when API settings are saved (4-chha-3/4). */
    var autoPause = false

    private var checking = false

    // ---- API dialogs (React `apiPickOpen`, `apiCfgN`) ----
    /** The "API" picker (API-1 / API-2) is open. */
    var pickOpen by mutableStateOf(false)
        private set
    /** Which API's config dialog is open (null = none). */
    var cfgN: Int? by mutableStateOf(null)
        private set

    /** React `openApiCfg(n)` — admin only: closes the picker and opens the config dialog of API [n]. */
    fun openApiCfg(n: Int) {
        if (!env.ctx.isAdmin) return
        pickOpen = false; cfgN = n
    }
    fun closePicker() { pickOpen = false }
    fun closeCfg() { cfgN = null }

    /** React `apiBtnClick` — API button: something running -> ask to stop; else open the picker. */
    fun apiBtnClick() {
        if (run != null) scope.launch { confirmStop() } else pickOpen = true
    }

    /** React `onFormSaved` — API settings were saved again: auto OCR may run again. */
    fun onFormSaved() { autoPause = false }

    // ---- hooks filled by later steps ----
    /** React `document.querySelector('dialog[open]')` for the dialogs of other steps (settings 4-nga-1, TTS 4-jha-3, the page confirm dialog 4-nga-3): they set this; auto OCR never starts while any dialog is open. */
    var anyDialogOpen: () -> Boolean = { false }
    /** React `ta.blur()` on touch screens — the keyboard must not stay open after the result (4-nga-3, the editor). */
    var blurEditor: () -> Unit = {}

    init {
        // React `refreshApiFlag`: admin switched the API off -> the running call is aborted
        env.apiFlag.onTurnedOff = { stop() }
        env.apiFlag.onTurnedOn = { maybeAutoOcr() }
        st.onPageLoaded = { maybeAutoOcr() }          // page + text loaded: an empty page may start API-1 by itself
    }

    // ---------------- auto OCR (4-chha-4) ----------------
    private var autoJob: Job? = null

    /**
     * React `maybeAutoOcr()` — after a page loads (and after every run / when the API is switched on / when auto is switched on):
     * 0.9 s later (quick page flipping must not cause useless calls) API-1 runs by itself if ALL hold: the admin has the API on, auto is on
     * and not paused, nothing runs, API-1 has Key + Model + Prompt, the page and its text loaded fine (no failed load, no init running),
     * no dialog / lightbox is open, the editor is empty (no text, no draft), the page is not proofed and auto did not already run on it
     * in this session.
     */
    fun maybeAutoOcr() {
        autoJob?.cancel()
        autoJob = scope.launch {
            delay(900)
            autoJob = null
            val c = apiCfg(1)
            if (!env.apiFlag.enabled) return@launch                                   // the admin switched the API off
            if (!c.optBoolean("auto", false) || autoPause || run != null) return@launch
            if (c.str("key").orEmpty().isEmpty() || c.str("model").orEmpty().isEmpty() || c.str("prompt").orEmpty().isEmpty()) return@launch   // API-1 is not set up
            val i = st.cur
            if (i < 0 || st.taLoading || st.loadErr || st.initing) return@launch      // page / text did not load properly
            if (pickOpen || cfgN != null || st.lightbox.open || anyDialogOpen()) return@launch
            if (st.text.isNotBlank()) return@launch                                   // text (or a draft) exists
            val stem = st.images.getOrNull(i)?.stem ?: return@launch
            if (st.proofed.contains(stem) || st.autoTried.contains(stem)) return@launch   // proofed, or already ran in this session
            runApi(appContext, 1, auto = true)
        }
    }

    /** React `toggleAutoApi` — the auto-OCR switch: flips `api1.auto`, un-pauses, tells the new state. */
    fun toggleAutoApi() {
        val on = !apiCfg(1).optBoolean("auto", false)
        env.cfg.setCfg { cur ->
            val next = JSONObject(cur.toString())
            val a = JSONObject((next.optJSONObject("api1") ?: JSONObject()).toString())
            a.put("auto", on); next.put("api1", a); next
        }
        autoPause = false
        if (!env.apiFlag.enabled) { env.toaster.toast("এপি আই এখন বন্ধ (অ্যাডমিন) — চালু হলে এই সেটিং কাজ করবে", true); return }
        if (on) {
            env.toaster.toast(if (apiCfg(1).str("key").orEmpty().isEmpty()) "অটো OCR চালু — তবে " + ViewConfig.API_NAME.getValue(1) + "-এর Key এখনো দেওয়া নেই" else "অটো OCR চালু (সবুজ)")
            maybeAutoOcr()
        } else {
            autoJob?.cancel(); autoJob = null
            env.toaster.toast("অটো OCR বন্ধ (লাল) — নিজে থেকে আর চলবে না")
        }
    }

    /** Page closing (React cleanup `clearTimeout(autoT.current)`). */
    fun cancelAuto() { autoJob?.cancel(); autoJob = null }

    /** React `apiRunRef.current.ctrl.abort()` */
    fun stop() { run?.job?.cancel() }

    /** React `apiBtnClick` when something runs: asks, then stops. */
    suspend fun confirmStop() {
        val r = run ?: return
        if (st.confirm(ViewConfig.API_NAME.getValue(r.n) + " চলছে। থামাবেন?")) stop()
    }

    private fun apiCfg(n: Int): JSONObject = env.cfg.cfg.optJSONObject("api$n") ?: JSONObject()

    /** React `runApi(n, { auto })` — runs in the page scope; returns at once. */
    fun runApi(context: Context, n: Int, auto: Boolean = false) {
        scope.launch { runApiNow(context, n, auto) }
    }

    private suspend fun runApiNow(context: Context, n: Int, auto: Boolean) {
        val nm = ViewConfig.API_NAME.getValue(n)
        run?.let { env.toaster.toast(ViewConfig.API_NAME.getValue(it.n) + " চলছে — শেষ হওয়া পর্যন্ত অপেক্ষা করুন", true); return }
        if (checking) return
        checking = true
        try { env.apiFlag.refresh() } finally { checking = false }      // the admin's latest state right before running
        if (!env.apiFlag.enabled) { if (!auto) env.toaster.toast("এপি আই বন্ধ আছে — অ্যাডমিন চালু করলে ব্যবহার করা যাবে", true); return }
        if (run != null) return
        if (st.cur < 0 || st.taLoading) { env.toaster.toast("আগে পেইজ লোড হতে দিন", true); return }

        val c = apiCfg(n)
        val key = (c.str("key").orEmpty().ifEmpty { if (n == 2) apiCfg(1).str("key").orEmpty() else "" }).trim()
        if (key.isEmpty()) { env.toaster.toast("$nm: API Key দেওয়া নেই", true); openApiCfg(n); return }
        val model = c.str("model").orEmpty()
        if (model.isEmpty()) { env.toaster.toast("$nm: Model দেওয়া নেই", true); openApiCfg(n); return }
        val prompt = c.str("prompt").orEmpty()
        if (prompt.isEmpty()) { env.toaster.toast("$nm: Prompt খালি", true); openApiCfg(n); return }

        val im = st.images[st.cur]; val stem = im.stem; val before = st.text
        if (n == 1 && before.isNotBlank() &&
            !st.confirm("এই পেইজে আগে থেকেই টেক্সট আছে। $nm চালালে ছবি থেকে নতুন টেক্সট এসে আগেরটা বদলে দেবে (সেভ না করা পর্যন্ত রিপোতে বদলায় না; আনডু চলে)। চালাবেন?")) return
        if (n == 2 && before.isBlank()) { env.toaster.toast("প্রুফ করার মতো টেক্সট নেই — আগে " + ViewConfig.API_NAME.getValue(1) + " চালান বা টেক্সট বসান", true); return }

        val me = ApiRun(n, stem, currentCoroutineContext()[Job])
        run = me
        if (n == 1) st.autoTried.add(stem)      // even when started by hand, auto will not run on this page again (a stop must not restart it)
        env.toaster.toast((if (auto) "অটো: " else "") + "$nm চলছে — পেইজ $stem…", false, 2500)
        var lastUI = 0L
        try {
            val parts = JSONArray()
            if (n == 1) {
                val img64 = env.imageB64(im.name)
                parts.put(ViewGemini.textPart(prompt)).put(img64.toPart())
            } else {
                // API-2: {{TEXT}} in the prompt is replaced by the editor text; without it the text is added below the prompt
                var p = prompt
                if (p.contains("{{TEXT}}")) p = p.replace("{{TEXT}}", before)
                else p += "\n\n===== OCR টেক্সট শুরু =====\n" + before + "\n===== OCR টেক্সট শেষ =====\n"
                parts.put(ViewGemini.textPart(p))
                if (c.optBoolean("sendImg", false)) parts.put(env.imageB64(im.name).toPart())
            }
            val temp = c.opt("temp")?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val res = ViewGemini.callGemini(key, model, parts, temp) { ch ->
                val t = System.currentTimeMillis()
                if (t - lastUI > 400) { lastUI = t; me.chars = ch }
            }
            run = null
            applyApiResult(context, n, stem, res.text, before, res.finish)
        } catch (e: CancellationException) {
            env.toaster.toast("$nm থামানো হয়েছে")
            throw e
        } catch (e: Exception) {
            val em = e.message ?: e.toString()
            if (auto && Regex("^(400|401|403|404|429):").containsMatchIn(em)) {
                autoPause = true        // key / model / quota problem: do not show the same error on every page, pause auto
                env.toaster.toast("অটো $nm ব্যর্থ: $em — অটো সাময়িক বন্ধ (API ডায়ালগে সংরক্ষণ করলে আবার চালু)", true, 9000)
            } else env.toaster.toast((if (auto) "অটো " else "") + "$nm ব্যর্থ: $em" + (if (auto) " — হাতে চালাতে API আইকন চাপুন" else ""), true, 8000)
        } finally {
            if (run === me) run = null
            maybeAutoOcr()
        }
    }

    /**
     * React `applyApiResult(n, stem, value, before, finish)` — the result goes into the editor like a paste. If the reader moved to another
     * page while the API ran, it is kept as that page's draft (shown when the page opens; saved only when the reader saves).
     */
    private suspend fun applyApiResult(context: Context, n: Int, stem: String, value0: String, before: String, finish: String?) {
        var value = value0
        val nm = ViewConfig.API_NAME.getValue(n)
        val probs = ViewUtils.checkText(value); val warn = mutableListOf<String>()
        if (finish == "MAX_TOKENS") warn.add("আউটপুট অসম্পূর্ণ হতে পারে (MAX_TOKENS)")
        if (probs.isNotEmpty()) warn.add(probs.joinToString(" | "))
        val i = st.cur

        if (i >= 0 && st.images.getOrNull(i)?.stem == stem && !st.taLoading) {
            if (st.text != before && !st.confirm("$nm চলার সময় এই পেইজের টেক্সট বদলেছে। তবুও ফল দিয়ে সব টেক্সট বদলাবেন?")) {
                copyText(context, value)
                env.toaster.toast("ফল বসানো হয়নি (ক্লিপবোর্ডে কপির চেষ্টা করা হয়েছে)", true, 5000)
                return
            }
            val hn = st.insertAtStart(value, true, true)      // like a paste: undo works, auto heading puts `1`
            blurEditor()
            val msg = "$nm সম্পন্ন • ${value.length} অক্ষর" + (if (hn > 0) " • ${hn}টি হেডিং চিহ্নিত" else "") + " • দেখে নিয়ে সেভ করুন"
            if (warn.isNotEmpty()) env.toaster.toast("সতর্কতা: " + warn.joinToString(" • ") + " — " + msg, true, 9000)
            else env.toaster.toast(msg, false, 4000)
        } else {
            if (st.autoHead) value = ViewUtils.markHeadings(value).text
            st.putDraft(stem, value)
            st.updateStatus()
            env.toaster.toast(
                "$nm সম্পন্ন — ফল পেইজ $stem-এর খসড়ায় রাখা হয়েছে (ওই পেইজে গেলে দেখা যাবে, সেভ করলে সংরক্ষিত হবে)" +
                    (if (warn.isNotEmpty()) " • সতর্কতা: " + warn.joinToString(" • ") else ""),
                warn.isNotEmpty(), 7000
            )
        }
    }
}

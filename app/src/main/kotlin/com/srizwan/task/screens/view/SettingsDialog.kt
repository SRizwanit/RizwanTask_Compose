package com.srizwan.task.screens.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.core.str
import com.srizwan.task.ui.ViewDropdown
import com.srizwan.task.ui.ViewDropdownOption
import com.srizwan.task.ui.ViewDropdowns
import com.srizwan.task.ui.ViewIcon
import com.srizwan.task.ui.ViewPalette
import com.srizwan.task.ui.viewColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The form of the settings dialog -> config changes (React `FIELD_PATCH` / `normalizedPatch` of `view/SettingsDialog.jsx`).
 * A field whose value is not valid gives `null` (nothing is saved for it yet).
 */
object ViewSettingsForm {
    /** The form fields, in the order of the React form: repo, branch, token, book, proofDir, pages, texts, proof. */
    val KEYS = listOf("repo", "branch", "token", "book", "proofDir", "pages", "texts", "proof")

    private val REPO_RE = Regex("^[^/\\s]+/[^/\\s]+$")
    private val SLASHES = Regex("^/+|/+$")
    private val LEAD_SLASHES = Regex("^/+")

    /** React `FIELD_PATCH[k](v)`. */
    fun fieldPatch(k: String, v: String): JSONObject? = when (k) {
        "repo" -> ViewUtils.normRepo(v).let { r -> if (REPO_RE.matches(r)) JSONObject().put("repo", r) else null }
        "branch" -> JSONObject().put("branch", v.trim())
        "token" -> JSONObject().put("token", v.trim())
        "book" -> JSONObject().put("book", ViewUtils.trimSl(v))
        "proofDir" -> JSONObject().put("proofDir", ViewUtils.trimSl(v))
        "pages" -> JSONObject().put("pagesDir", v.trim().replace(SLASHES, "").ifEmpty { "pages" })
        "texts" -> JSONObject().put("textsDir", v.trim().replace(SLASHES, "").ifEmpty { "texts" })
        "proof" -> JSONObject().put("proofFile", v.trim().replace(LEAD_SLASHES, "").ifEmpty { "proofed.json" })
        else -> null
    }

    /** React `normalizedPatch(f)` — all valid fields of the form as one config patch. */
    fun normalizedPatch(f: Map<String, String>): JSONObject {
        val out = JSONObject()
        for (k in KEYS) {
            val p = fieldPatch(k, f[k].orEmpty()) ?: continue
            for (key in p.keys()) out.put(key, p.opt(key))
        }
        return out
    }

    /** The values the form starts with (React `useLayoutEffect([open])`): the saved config. */
    fun initial(cfg: JSONObject): SnapshotStateMap<String, String> {
        val m = mutableStateMapOf<String, String>()
        m["repo"] = cfg.str("repo").orEmpty()
        m["branch"] = cfg.str("branch").orEmpty()
        m["token"] = cfg.str("token").orEmpty()
        m["book"] = cfg.str("book").orEmpty()
        m["proofDir"] = ViewConfig.repoPaths(cfg).proofDirName
        m["pages"] = cfg.str("pagesDir").orEmpty()
        m["texts"] = cfg.str("textsDir").orEmpty()
        m["proof"] = cfg.str("proofFile").orEmpty()
        return m
    }
}

/**
 * State and actions of the settings dialog (React `View.jsx`: `settingsOpen`, `openSettings`, `onSettingsClose`, `saveSettings`,
 * and the `onEngine` / `onClearToken` props). Only the admin gets the dialog.
 *
 * [openSettings] — admin only; reads the phone's voices again and opens. [close] = "বন্ধ" / outside tap / Back: if the connection settings
 * changed, or there is no page list yet, the connection starts ([ViewPageState.afterSettingsClosed]).
 * [save] = "সংরক্ষণ ও সংযোগ": the repo is checked, the form is saved, the fallback of the reading starts fresh, the image cache is cleared and
 * a new connection is forced, then the dialog closes (the connection starts from the close step, exactly once).
 * [open] also tells [ViewApi.anyDialogOpen] that auto-OCR must not start while the dialog is up.
 */
class ViewSettings(
    private val env: ViewEnv,
    private val st: ViewPageState,
    private val tts: TtsController,
    private val scope: CoroutineScope,
    private val wantPage: String?
) {
    var open by mutableStateOf(false)
        private set

    /** React `openSettings()` — also the hook of [ViewPageState.onOpenSettings] (init failure / first open without token). */
    fun openSettings() {
        if (!env.ctx.isAdmin) return          // settings are the admin's only
        tts.refreshVoices()
        open = true
    }

    /** React `onSettingsClose()`. */
    fun close() {
        if (!open) return
        open = false
        ViewDropdowns.close()
        scope.launch { st.afterSettingsClosed(wantPage) }
    }

    /** React `saveSettings(f)`. */
    fun save(f: Map<String, String>) {
        if (!Regex("^[^/\\s]+/[^/\\s]+$").matches(ViewUtils.normRepo(f["repo"].orEmpty()))) {
            env.toaster.toast("রিপো owner/repo আকারে দিন", true)
            return
        }
        env.cfg.setCfg(ViewSettingsForm.normalizedPatch(f))
        tts.resetForce()                       // engine / voice changed: the automatic fallback starts again from the first
        st.applyConnChange()
        env.imgCache.clear(); st.forceInit = true
        close()
    }

    /** React `onEngine(v)`. */
    fun setEngine(v: String) {
        env.cfg.setCfg(JSONObject().put("ttsEngine", v))
        tts.resetForce()
    }

    /** React `onClearToken()`. */
    fun clearToken() {
        env.cfg.setCfg(JSONObject().put("token", ""))
        env.toaster.toast("টোকেন মুছে ফেলা হয়েছে", false)
    }

    /** React `setVoice(l)(v)` — the phone voice of language [l] (`""` = automatic). */
    fun setVoice(l: String, v: String) {
        env.cfg.setCfg { cur ->
            val next = JSONObject(cur.toString())
            val t = next.optJSONObject("tts") ?: JSONObject().also { next.put("tts", it) }
            t.put(l, v)
            next
        }
    }
}

private val SetDlgShape = RoundedCornerShape(18.dp)

/** React `ENGINES` of the dialog (the last choice of "শুনুন" is shown here). */
private val ENGINES = listOf(
    "auto" to "স্বয়ংক্রিয় (ফোনের TTS; না চললে Google অনলাইন)", "device" to "শুধু ফোনের TTS",
    "google" to "শুধু Google অনলাইন (ইন্টারনেট লাগবে)", "edge" to "শুধু Edge TTS (Microsoft)",
    "poll" to "শুধু Pollinations", "server" to "শুধু Piper / নিজস্ব সার্ভার",
    "watson" to "শুধু IBM Watson", "fish" to "শুধু Fish Audio"
).map { (v, l) -> ViewDropdownOption(v, l) }

/**
 * React `view/SettingsDialog.jsx` — "সেটিংস" (admin only). What is typed is saved at once (auto-save): every valid field goes into the config
 * as the user types ([ViewSettingsForm.fieldPatch]); an invalid repo keeps the old one until it is fixed. Fields: রিপো (owner/repo), ব্রাঞ্চ,
 * GitHub Access Token (hidden), কিতাবের নাম (prefix), ছবির / টেক্সট / প্রুফড ফোল্ডার, প্রুফড তালিকার ফাইল; a preview line shows the folders that
 * will be used; then the reading engine (last choice) and the phone voices for Bangla / Arabic / English.
 * Buttons: "টোকেন মুছুন", "বন্ধ", "সংরক্ষণ ও সংযোগ" — they stay at the bottom while the form scrolls. Icons are vectors; no emoji.
 * Shown by [ViewSettings.open].
 */
@Composable
fun SettingsDialog(env: ViewEnv, tts: TtsController, settings: ViewSettings) {
    if (settings.open) SettingsDialogBody(env, tts, settings)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialogBody(env: ViewEnv, tts: TtsController, settings: ViewSettings) {
    val c = viewColors()
    env.cfg.rev                                           // recompose when the config changes (preview line, engine, voices)
    val f = remember { ViewSettingsForm.initial(env.cfg.cfg) }   // React `useLayoutEffect([open])`: saved values into the form

    /** React `edit(k)` — the form shows the text as typed, the valid part is saved at once. */
    fun edit(k: String, v: String) {
        f[k] = v
        ViewSettingsForm.fieldPatch(k, v)?.let { env.cfg.setCfg(it) }
    }

    val P = ViewConfig.repoPaths(env.cfg.cfg)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.fg, unfocusedTextColor = c.fg, focusedBorderColor = c.pri, unfocusedBorderColor = c.line,
        cursorColor = c.pri, focusedContainerColor = c.panel, unfocusedContainerColor = c.panel
    )

    @Composable
    fun Input(k: String, placeholder: String = "", password: Boolean = false) {
        OutlinedTextField(
            value = f[k].orEmpty(), onValueChange = { edit(k, it) }, singleLine = true, colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
            )
        )
    }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(onDismissRequest = { settings.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(12.dp).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = maxH)
                .clip(SetDlgShape).background(c.bg).padding(18.dp)
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text("সেটিংস", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                SLabel("রিপো (owner/repo)", c)
                Input("repo", "SunniPedia/pdftojpg")
                SLabel("ব্রাঞ্চ (খালি = ডিফল্ট ব্রাঞ্চ)", c)
                Input("branch", "main")
                SLabel("GitHub Access Token (এখানে বসান)", c)
                Input("token", "github_pat_...", password = true)
                SHint(
                    buildAnnotatedString {
                        append("টোকেন শুধু এই ফোনে সংরক্ষিত থাকে। এখানে যা-ই লিখুন/বদলান, সাথে সাথে স্বয়ংক্রিয়ভাবে সংরক্ষিত হয় — প্রতিবার দিতে হবে না। অ্যাপের কোডে বা রিপোতে কখনো লিখবেন না। সেভ/পুশ করতে টোকেনে এই রিপোর জন্য ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Contents: Read and write") }
                        append(" অনুমতি লাগবে।")
                    }, c
                )
                SLabel("কিতাবের নাম (prefix) — খালি রাখলে আগের মতো pages / texts / proofed.json", c)
                Input("book", "যেমন: bukhari")
                SLabel("ছবির ফোল্ডার (মূল নাম)", c)
                Input("pages")
                SLabel("টেক্সট ফোল্ডার (মূল নাম; না থাকলে প্রথম সেভে নিজে থেকে তৈরি হবে)", c)
                Input("texts")
                SLabel("প্রুফড ফোল্ডার (প্রুফড তালিকা এর ভেতরে থাকবে; খালি = রিপোর রুটে)", c)
                Input("proofDir", "proofed")
                SLabel("প্রুফড তালিকার ফাইল (মূল নাম; না থাকলে তৈরি হবে)", c)
                Input("proof")
                SHint(AnnotatedString("এখন ব্যবহার হবে → ছবি: ${P.pagesDir}/ • টেক্সট: ${P.textsDir}/ • প্রুফড: ${P.proofPath}"), c)

                HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 4.dp), color = c.line)

                SLabel("শোনার ইঞ্জিন", c)
                ViewDropdown(
                    value = env.cfg.cfg.str("ttsEngine").orEmpty(), options = ENGINES,
                    onChange = { settings.setEngine(it) }
                )
                SHint(AnnotatedString("Google অনলাইন অডিও একটি অনানুষ্ঠানিক সার্ভিস (Google Translate-এর অডিও), তাই যেকোনো সময় বন্ধ বা সীমিত হতে পারে। এতে ভয়েস বাছা যায় না; ভাষা (বাংলা/আরবি/ইংরেজি) নিজে থেকে চেনা হয়।"), c)
                SHint(AnnotatedString("এখানে শেষ বাছাই দেখায়। \"শুনুন\" বোতাম চাপলে প্রতিবার সার্ভিস বাছার ডায়ালগ আসে (সেখানে সব সার্ভিসের কনফিগও আছে)।"), c)
                for (l in listOf("bn", "ar", "en")) {
                    val o = TtsEngines.voiceOptions(tts.voices, l)
                    val want = env.cfg.cfg.optJSONObject("tts")?.str(l).orEmpty()
                    SLabel("ফোনের ভয়েস — " + (TtsEngines.TTS_NAME[l] ?: l), c)
                    ViewDropdown(
                        value = TtsEngines.voiceValue(o, want),
                        options = o.map { ViewDropdownOption(it.value, it.label) },
                        onChange = { settings.setVoice(l, it) }
                    )
                }
                SHint(AnnotatedString("ফোনের ভয়েস ডিভাইসের টেক্সট-টু-স্পিচ ইঞ্জিন থেকে আসে (শুধু ফোনের TTS ব্যবহার হলে প্রযোজ্য)। \"স্বয়ংক্রিয়\" রাখলে ডিভাইসের সেরা মিল ব্যবহার হবে।"), c)
            }

            HorizontalDivider(Modifier.padding(top = 10.dp), color = c.line)
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { f["token"] = ""; settings.clearToken() },       // React `cClearTok`
                    border = BorderStroke(1.dp, c.line)
                ) {
                    ViewIcon("trash", size = 14.dp, tint = c.fg)
                    Spacer(Modifier.width(6.dp))
                    Text("টোকেন মুছুন", color = c.fg)
                }
                OutlinedButton(onClick = { settings.close() }, border = BorderStroke(1.dp, c.line)) { Text("বন্ধ", color = c.fg) }   // React `cCancel`
                Button(
                    onClick = { settings.save(f.toMap()) },                    // React `cSave`
                    colors = ButtonDefaults.buttonColors(containerColor = c.pri, contentColor = c.priFg)
                ) {
                    ViewIcon("save", size = 14.dp, tint = c.priFg)
                    Spacer(Modifier.width(6.dp))
                    Text("সংরক্ষণ ও সংযোগ")
                }
            }
        }
    }
}

/** React `dialog label`. */
@Composable
private fun SLabel(text: String, c: ViewPalette) {
    Text(text, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
}

/** React `dialog small`. */
@Composable
private fun SHint(text: AnnotatedString, c: ViewPalette) {
    Text(text, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
}

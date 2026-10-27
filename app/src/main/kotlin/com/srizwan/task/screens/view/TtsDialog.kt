package com.srizwan.task.screens.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.str
import com.srizwan.task.ui.ViewDropdown
import com.srizwan.task.ui.ViewDropdownOption
import com.srizwan.task.ui.ViewDropdowns
import com.srizwan.task.ui.ViewIcon
import com.srizwan.task.ui.ViewPalette
import com.srizwan.task.ui.viewColors
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * State and actions of the service picker (React `View.jsx`: `tsOpen`, `tsCb`, `askTts`, `onTsGo`, `onTsClose`).
 *
 * [ask] is what [TtsController.ask] calls: it remembers the callback, reads the phone's voices again and opens the dialog.
 * [go] = a service was chosen and "শুনুন" pressed: it is saved (`cfg.ttsEngine`), the fallback / Google audio start fresh, the dialog closes and
 * the remembered callback runs (it starts the reading). [close] = "বন্ধ" / outside tap / Back: nothing starts.
 * [open] also tells [ViewApi.anyDialogOpen] that auto-OCR must not start while this dialog is up.
 */
class TtsPicker(private val env: ViewEnv, private val tts: TtsController) {
    var open by mutableStateOf(false)
        private set
    private var cb: (() -> Unit)? = null

    /** React `askTts(cb)`. */
    fun ask(cb: () -> Unit) {
        this.cb = cb
        tts.refreshVoices()
        open = true
    }

    /** React `onTsGo(engine)`. */
    fun go(engine: String) {
        env.cfg.setCfg(JSONObject().put("ttsEngine", engine))
        tts.resetForce(); tts.clearG()
        val c = cb; cb = null
        open = false
        c?.invoke()
    }

    /** React `onTsClose()`. */
    fun close() {
        cb = null
        ViewDropdowns.close()
        open = false
    }
}

private val TtsDlgShape = RoundedCornerShape(18.dp)
private val TtsCardShape = RoundedCornerShape(14.dp)

/**
 * React `view/TtsDialog.jsx` — "কোন সার্ভিসে শুনবেন?": shown every time "শুনুন" starts a new reading, for everybody (not only the admin).
 * One card per service ([TtsEngines.TS_ORDER]): a radio dot, the name, a description and coloured tags; the chosen card (the last choice is
 * pre-selected) opens its settings (phone voices for "ফোনের TTS", key / voice / URL fields for the extra services). What is typed or chosen is saved
 * at once in `cfg.ttsx` / `cfg.tts` on the phone, so it never has to be typed again. "শুনুন" checks the config ([TtsEngines.tsValidate]) first.
 * The button row stays at the bottom while the list scrolls (React `position: sticky`); the chosen card is scrolled into view.
 * Shown by [TtsPicker.open]. All icons are vectors.
 */
@Composable
fun TtsDialog(env: ViewEnv, tts: TtsController, picker: TtsPicker) {
    if (picker.open) TtsDialogBody(env, tts, picker)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtsDialogBody(env: ViewEnv, tts: TtsController, picker: TtsPicker) {
    val c = viewColors()
    env.cfg.rev                                          // recompose when the config changes (voice choice)

    // React `useLayoutEffect([open])`: the last choice is marked, the error is cleared, the saved values go into the form
    var sel by remember { mutableStateOf(env.cfg.cfg.str("ttsEngine")?.takeIf { it in TtsEngines.TS_ORDER } ?: "auto") }
    var err by remember { mutableStateOf("") }
    val vals = remember { initialVals(env.cfg.cfg) }    // 'service.field' -> the value shown in the form (not trimmed)
    val requesters = remember { TtsEngines.TS_ORDER.associateWith { BringIntoViewRequester() } }

    // React `scrollIntoView({ block: 'nearest' })` 30 ms after the choice changes
    LaunchedEffect(sel) {
        delay(30)
        requesters[sel]?.bringIntoView()
    }

    /** React `setX(id, k, v)` — the form shows [v] as typed, the saved config gets it trimmed. */
    fun setX(id: String, k: String, v: String) {
        vals["$id.$k"] = v
        env.cfg.setCfg { cur ->
            val next = JSONObject(cur.toString())
            val ttsx = next.optJSONObject("ttsx") ?: JSONObject().also { next.put("ttsx", it) }
            val x = ttsx.optJSONObject(id) ?: JSONObject().also { ttsx.put(id, it) }
            x.put(k, v.trim())
            next
        }
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

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(onDismissRequest = { picker.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxH)
                .clip(TtsDlgShape).background(c.bg).padding(18.dp)
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text("কোন সার্ভিসে শুনবেন?", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "একটি সার্ভিস বেছে নিন, দরকার হলে তার কনফিগ (কী / ভয়েস / URL) দিন, তারপর \"শুনুন\" চাপুন। কনফিগ এই ফোনে থেকে যায় (আবার লিখতে হবে না); শোনা শুরুর সময় প্রতিবার এই তালিকা আসবে, যাতে সার্ভিস বদলে নিতে পারেন।",
                    color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp)
                )
                for (id in TtsEngines.TS_ORDER) {
                    val m = TtsEngines.tsMeta(id) ?: continue
                    TsItem(
                        c = c, meta = m, selected = id == sel, requester = requesters.getValue(id),
                        hasPanel = id == "device" || TtsServices.xeng(id) != null,
                        onPick = { sel = id; err = "" }
                    ) {
                        if (id == "device") DevicePanel(env, tts, c, ::setVoice)
                        else TtsServices.xeng(id)?.let { ExtraPanel(id, it, vals, c, ::setX) }
                    }
                }
            }

            if (err.isNotEmpty())
                Text(err, color = c.err, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(top = 10.dp), color = c.line)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { picker.close() }, border = BorderStroke(1.dp, c.line)) { Text("বন্ধ", color = c.fg) }
                Button(
                    onClick = {
                        val e = TtsEngines.tsValidate(sel, env.cfg.cfg, tts.hasTTS)
                        err = e
                        if (e.isEmpty()) picker.go(sel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.pri, contentColor = c.priFg)
                ) {
                    ViewIcon("play", size = 14.dp, tint = c.priFg)
                    Spacer(Modifier.width(6.dp))
                    Text("শুনুন")
                }
            }
        }
    }
}

/** The form values at the start: `cfg.ttsx[id][field]` of every extra service, "" when nothing is saved. */
private fun initialVals(cfg: JSONObject): SnapshotStateMap<String, String> {
    val m = mutableStateMapOf<String, String>()
    for ((id, eng) in TtsServices.XENG)
        for (f in eng.fields) m["$id.${f.k}"] = cfg.optJSONObject("ttsx")?.optJSONObject(id)?.str(f.k).orEmpty()
    return m
}

/** One service card (React `.tsItem`): the pick button (dot, name, description, tags) and, when chosen, its settings panel (React `.tsPanel`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TsItem(
    c: ViewPalette, meta: TtsMeta, selected: Boolean, requester: BringIntoViewRequester, hasPanel: Boolean,
    onPick: () -> Unit, panel: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth().bringIntoViewRequester(requester)
            .clip(TtsCardShape).background(c.panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.pri else c.line, TtsCardShape)
    ) {
        Column(
            Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TsDot(selected, c)
                Text(meta.name, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            }
            Text(meta.desc, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
            if (meta.tags.isNotEmpty())
                FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((t, k) in meta.tags) TsTag(t, k, c)
                }
        }
        if (selected && hasPanel) {
            Column(
                Modifier.fillMaxWidth()
                    .drawBehind {                                  // React `border-top: 1px dashed var(--line)`
                        drawLine(
                            c.line, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                        )
                    }
                    .padding(start = 13.dp, end = 13.dp, top = 2.dp, bottom = 12.dp),
                content = panel
            )
        }
    }
}

/** React `.tsDot` — a 14dp ring; the chosen one is primary coloured with a filled centre. Vector (drawn). */
@Composable
private fun TsDot(selected: Boolean, c: ViewPalette) {
    Canvas(Modifier.size(14.dp)) {
        val ring = 2.dp.toPx()
        drawCircle(if (selected) c.pri else c.muted, radius = (size.minDimension - ring) / 2f, style = Stroke(ring))
        if (selected) drawCircle(c.pri, radius = 4.5f.dp.toPx())
    }
}

/** React `.tsTag` — a small pill; kind `ok` green, `warn` orange, `err` red, otherwise grey. */
@Composable
private fun TsTag(text: String, kind: String, c: ViewPalette) {
    val (bg, fg) = when (kind) {
        "ok" -> Color(0x2916A34A) to c.ok
        "warn" -> Color(0x24C2410C) to c.warn
        "err" -> Color(0x24DC2626) to c.err
        else -> c.panel2 to c.muted
    }
    Text(
        text, color = fg, fontSize = 11.sp, lineHeight = 17.6f.sp,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** React `dialog label`. */
@Composable
private fun PanelLabel(text: String, c: ViewPalette) {
    Text(text, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
}

/** React `dialog small`. */
@Composable
private fun PanelHint(text: String, c: ViewPalette) {
    Text(text, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
}

/** React `panel('device')` — the phone's voices for Bangla / Arabic / English ("" = automatic). */
@Composable
private fun ColumnScope.DevicePanel(env: ViewEnv, tts: TtsController, c: ViewPalette, setVoice: (String, String) -> Unit) {
    if (!tts.hasTTS) PanelHint("এই ফোনে টেক্সট-টু-স্পিচ ইঞ্জিন নেই।", c)
    for (l in listOf("bn", "ar", "en")) {
        val o = TtsEngines.voiceOptions(tts.voices, l)
        val want = env.cfg.cfg.optJSONObject("tts")?.str(l).orEmpty()
        PanelLabel("ফোনের ভয়েস — " + (TtsEngines.TTS_NAME[l] ?: l), c)
        ViewDropdown(
            value = TtsEngines.voiceValue(o, want),
            options = o.map { ViewDropdownOption(it.value, it.label) },
            onChange = { setVoice(l, it) }
        )
    }
    PanelHint(
        "ভয়েস ডিভাইসের টেক্সট-টু-স্পিচ ইঞ্জিন থেকে আসে। \"স্বয়ংক্রিয়\" রাখলে ডিভাইসের সেরা মিল ব্যবহার হবে। বাংলা ভয়েস না দেখালে ফোনের সেটিংসে ভয়েস ডেটা ডাউনলোড করুন, বা ডিভাইসের TTS ইঞ্জিন বদলান।",
        c
    )
}

/** React `panel(id)` for the extra services — the form fields of [TtsXEng.fields] (choice list / text / hidden key) and the hint. */
@Composable
private fun ColumnScope.ExtraPanel(
    id: String, eng: TtsXEng, vals: SnapshotStateMap<String, String>, c: ViewPalette, setX: (String, String, String) -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.fg, unfocusedTextColor = c.fg, focusedBorderColor = c.pri, unfocusedBorderColor = c.line,
        cursorColor = c.pri, focusedContainerColor = c.panel, unfocusedContainerColor = c.panel
    )
    for (f in eng.fields) {
        val v = vals["$id.${f.k}"] ?: ""
        PanelLabel(f.label, c)
        if (f.t == "sel") {
            ViewDropdown(v, f.opts.map { (value, label) -> ViewDropdownOption(value, label) }, { setX(id, f.k, it) })
        } else {
            val pw = f.t == "pw"
            OutlinedTextField(
                value = v, onValueChange = { setX(id, f.k, it) }, singleLine = true, colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { if (f.ph.isNotEmpty()) Text(f.ph, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                visualTransformation = if (pw) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    keyboardType = if (pw) KeyboardType.Password else KeyboardType.Text
                )
            )
        }
    }
    if (eng.hint.isNotEmpty()) PanelHint(eng.hint, c)
}

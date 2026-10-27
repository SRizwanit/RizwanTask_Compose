package com.srizwan.task.screens.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.str
import com.srizwan.task.ui.ViewBtn
import com.srizwan.task.ui.ViewBtnKind
import com.srizwan.task.ui.ViewDropdown
import com.srizwan.task.ui.ViewDropdownOption
import com.srizwan.task.ui.viewColors
import org.json.JSONObject

/** React `RATES` — the reading speeds of the header list. */
private val RATES = listOf("0.6", "0.8", "1", "1.2", "1.5", "1.8", "2").map { ViewDropdownOption(it, "$it×") }

/** React `String(cfg.ttsRate || 1)` — a whole number shows without ".0". */
private fun rateValue(cfg: JSONObject): String {
    val raw = cfg.opt("ttsRate")
    val d = (raw as? Number)?.toDouble() ?: raw?.toString()?.toDoubleOrNull() ?: 0.0
    val x = if (d == 0.0 || d.isNaN()) 1.0 else d
    return if (x == Math.floor(x)) x.toLong().toString() else x.toString()
}

/**
 * React `<header id="hdr">` of `View.jsx` — the toolbar at the top, one row that scrolls sideways (React `overflow-x: auto`):
 * ফিরুন, সেটিংস (admin), রিলোড, আগের পেইজ, পেইজ-তালিকা, পরের পেইজ, পেইজ নম্বর + "যাও", পরের অপ্রুফড, ছবি/টেক্সটের পাশ বদল,
 * হেডিং: চালু/বন্ধ; then the reading group (শুনুন / বিরতি / চালান, থামান, গতি) and, for the admin only, the API group (অটো OCR, API).
 * Same buttons, order and titles as React. A phone has no hover, so a long press on a button shows its title as a toast.
 * All icons are vectors; no emoji. The actions are the ones the earlier steps made: [ViewPageState], [TtsController], [ViewApi], [ViewSettings].
 */
@Composable
fun ViewHeader(
    env: ViewEnv, st: ViewPageState, tts: TtsController, api: ViewApi, settings: ViewSettings,
    onBack: () -> Unit, onReload: () -> Unit
) {
    val c = viewColors()
    env.cfg.rev                                   // recompose when the config changes (swap, heading switch, rate, auto OCR)
    val cfg = env.cfg.cfg
    val admin = env.ctx.isAdmin
    val apiEnabled = env.apiFlag.enabled
    val autoOn = cfg.optJSONObject("api1")?.optBoolean("auto", false) == true
    val playing = tts.state == TtsState.Playing
    val apiRun = api.run
    val hint: (String) -> Unit = { env.toaster.toast(it) }
    val apiName = ViewConfig.API_NAME
    var jump by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxWidth().background(c.bg)
            .drawBehind { drawLine(c.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewBtn(onBack, icon = "left", hint = "কাজের তালিকায় ফিরুন", desc = "ফিরুন", onHint = hint)
            if (admin) ViewBtn({ settings.openSettings() }, icon = "settings", hint = "সেটিংস / টোকেন", desc = "সেটিংস", onHint = hint)
            ViewBtn(onReload, icon = "refresh", hint = "রিপো থেকে তালিকা ও বর্তমান পেইজ নতুন করে লোড করুন", desc = "রিলোড", onHint = hint)
            ViewBtn({ st.go(st.stepVis(st.cur, -1)) }, icon = "left", hint = "আগের পেইজ (Alt+←)", desc = "আগের পেইজ", onHint = hint)
            ViewDropdown(
                value = st.cur.toString(), options = st.pageOptions(), onChange = { st.go(it.toIntOrNull() ?: -1) },
                width = 150.dp, ariaLabel = "পেইজ বাছুন"
            )
            ViewBtn({ st.go(st.stepVis(st.cur, 1)) }, icon = "right", hint = "পরের পেইজ (Alt+→)", desc = "পরের পেইজ", onHint = hint)

            // React `<input id="jump" type="number" placeholder="পেইজ">` (72 px) + "যাও"
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.width(72.dp).height(38.dp).clip(shape).background(c.bg).border(1.dp, c.line, shape)
                    .semantics { contentDescription = "পেইজ নম্বর" }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = jump, onValueChange = { v -> jump = v.filter { it.isDigit() } }, singleLine = true,
                    textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.pri),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { st.jumpTo(jump) }),
                    decorationBox = { inner -> if (jump.isEmpty()) Text("পেইজ", color = c.muted, fontSize = 14.sp); inner() }
                )
            }
            ViewBtn({ st.jumpTo(jump) }, label = "যাও")
            ViewBtn({ st.nextUnproofed() }, icon = "skip", hint = "পরের অপ্রুফড পেইজে যান", desc = "পরের অপ্রুফড পেইজ", onHint = hint)
            ViewBtn(
                { env.cfg.setCfg(JSONObject().put("swap", !cfg.optBoolean("swap", false))) }, icon = "swap",
                hint = "ছবি ও টেক্সটের পাশ বদলান", desc = "পাশ বদলান", onHint = hint
            )
            ViewBtn(
                { st.toggleAutoHead() }, icon = "tag", label = "হেডিং: " + (if (st.autoHead) "চালু" else "বন্ধ"),
                kind = if (st.autoHead) ViewBtnKind.On else ViewBtnKind.Normal,
                hint = "চালু থাকলে পেস্ট করা টেক্সটের প্রতিটি হেডিংয়ের আগে (স্পেস ছাড়া) 1 বসবে", onHint = hint
            )

            // React `#ttsBox` — a divider on the left, then listen / pause / resume, stop, speed
            GroupDivider()
            ViewBtn(
                { tts.click() }, icon = if (playing) "pause" else "play",
                label = if (playing) "বিরতি" else if (tts.state == TtsState.Paused) "চালান" else "শুনুন",
                kind = if (playing) ViewBtnKind.On else ViewBtnKind.Normal,
                hint = "টেক্সট শুনুন — নির্বাচিত অংশ, না থাকলে কার্সর থেকে শেষ পর্যন্ত (শুরুর সময় সার্ভিস বাছার ডায়ালগ আসবে)", onHint = hint
            )
            ViewBtn(
                { tts.stop(); env.toaster.toast("শোনা বন্ধ") }, icon = "stop", enabled = tts.state != TtsState.Idle,
                hint = "শোনা বন্ধ করুন", desc = "থামান", onHint = hint
            )
            val rv = rateValue(cfg).let { v -> if (RATES.any { it.value == v }) v else "1" }
            ViewDropdown(
                value = rv, options = RATES, width = 70.dp, compact = true, ariaLabel = "পড়ার গতি",
                onChange = { v ->
                    env.cfg.setCfg(JSONObject().put("ttsRate", v.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 1.0))
                    tts.setRate()
                }
            )

            // React `#apiBox` — admin only: auto OCR switch (green on / red off) and the API button
            if (admin) {
                GroupDivider()
                ViewBtn(
                    { api.toggleAutoApi() }, icon = "bolt", kind = if (autoOn) ViewBtnKind.On else ViewBtnKind.Off, desc = "অটো OCR",
                    hint = if (!apiEnabled) "এপি আই বন্ধ — অ্যাডমিন বন্ধ রেখেছেন, তাই অটো OCR চলবে না"
                    else if (autoOn) "অটো OCR চালু (সবুজ) — " + apiName.getValue(1) + "-এর Key দেওয়া থাকলে টেক্সট ফাঁকা পেইজ খুললে নিজে থেকে চলবে। বন্ধ করতে চাপুন"
                    else "অটো OCR বন্ধ (লাল) — API কনফিগার থাকলেও নিজে থেকে চলবে না (হাতে API আইকন থেকে চালানো যাবে)। চালু করতে চাপুন",
                    onHint = hint, modifier = Modifier.apiOff(!apiEnabled)
                )
                ViewBtn(
                    { api.apiBtnClick() }, icon = "api", kind = if (apiRun != null) ViewBtnKind.Primary else ViewBtnKind.Normal,
                    pulse = apiRun != null, desc = "API",
                    hint = if (!apiEnabled) "এপি আই বন্ধ (অ্যাডমিন বন্ধ রেখেছেন)"
                    else if (apiRun != null) apiName.getValue(apiRun.n) + " চলছে — থামাতে চাপুন"
                    else "API — ছবি থেকে টেক্সট (এপি আই - ১) / টেক্সট প্রুফিং (এপি আই - ২)",
                    onHint = hint, modifier = Modifier.apiOff(!apiEnabled)
                )
            }
        }
    }
}

/** The thin vertical line before the reading and API groups (React `border-left: 1px solid var(--line)`, with a little space around it). */
@Composable
private fun GroupDivider() {
    val c = viewColors()
    Box(Modifier.padding(start = 2.dp).width(1.dp).height(26.dp).background(c.line))
}

/** React `#apiBox.off` (API switched off by the admin): the group looks faded. */
private fun Modifier.apiOff(off: Boolean): Modifier = if (off) this.alpha(0.6f) else this

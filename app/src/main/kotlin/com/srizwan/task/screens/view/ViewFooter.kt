package com.srizwan.task.screens.view

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.ui.ViewBtn
import com.srizwan.task.ui.ViewBtnKind
import com.srizwan.task.ui.viewColors
import kotlinx.coroutines.launch

/**
 * React `<footer id="ftr">` of `View.jsx` — the buttons at the bottom, one row that scrolls sideways (React `overflow-x: auto`):
 * সেভ ([ViewPageState.saveText]), সেভ ও পরের (blue, [ViewPageState.proofAndNext]; a second tap while it runs does nothing),
 * প্রুফড (green when this page is proofed, [ViewPageState.toggleProofClick]) and বাতিল ([ViewPageState.discardClick], asks first).
 * Same order and titles as React; a phone has no hover, so a long press shows the title as a toast. 38 dp high buttons, a line on top,
 * the tint of `color-mix(bg 88%, panel)`. All icons are vectors; no emoji.
 */
@Composable
fun ViewFooter(env: ViewEnv, st: ViewPageState) {
    val c = viewColors()
    val scope = rememberCoroutineScope()
    val narrow = LocalConfiguration.current.screenWidthDp <= 640
    st.listRev                                    // proofed marks change in place
    val pf = st.cur >= 0 && st.cur < st.images.size && st.proofed.contains(st.images[st.cur].stem)
    val hint: (String) -> Unit = { env.toaster.toast(it) }
    Box(
        Modifier.fillMaxWidth().background(lerp(c.bg, c.panel, 0.12f))
            .drawBehind { drawLine(c.line, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx()) }
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 8.dp else 12.dp, vertical = if (narrow) 6.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewBtn(
                { scope.launch { st.saveText() } }, icon = "save", label = "সেভ", hint = "এই পেইজের টেক্সট সেভ", onHint = hint, padH = 14.dp
            )
            ViewBtn(
                { scope.launch { st.proofAndNext() } }, icon = "checkc", label = "সেভ ও পরের", kind = ViewBtnKind.Primary,
                hint = "সেভ করে প্রুফড চিহ্নিত করুন এবং পরের পেইজে যান", onHint = hint, padH = 18.dp
            )
            ViewBtn(
                { scope.launch { st.toggleProofClick() } }, icon = if (pf) "checksq" else "square", label = "প্রুফড",
                kind = if (pf) ViewBtnKind.On else ViewBtnKind.Normal,
                hint = if (pf) "প্রুফড চিহ্ন আছে — বাতিল করতে চাপুন" else "প্রুফড হিসেবে চিহ্নিত করুন", onHint = hint, padH = 14.dp
            )
            ViewBtn(
                { scope.launch { st.discardClick() } }, icon = "trash", label = "বাতিল",
                hint = "এই পেইজের অসংরক্ষিত পরিবর্তন/খসড়া বাতিল করে রিপোর টেক্সট ফিরিয়ে আনুন", onHint = hint, padH = 14.dp
            )
        }
    }
}

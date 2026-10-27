package com.srizwan.task.screens.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.viewColors

/**
 * Custom dialog of "সেভ ও পরের": "৩০ সেকেন্ড অপেক্ষা করুন...।" with a counting-down ring (vector, drawn on a Canvas) and the seconds left in
 * its middle. It opens when [ViewPageState.saveWait] is not null; when the count reaches zero the dialog closes and the normal
 * save -> proofed -> next page work runs ([ViewPageState.proofAndNext]). Tapping outside does nothing (a stray tap must not cancel);
 * the back button cancels ([ViewPageState.cancelSaveWait]) and nothing is saved.
 */
@Composable
fun ViewSaveWaitDialog(st: ViewPageState) {
    val left = st.saveWait ?: return
    val c = viewColors()
    Dialog(
        onDismissRequest = { st.cancelSaveWait() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val track = c.line; val arc = c.pri
                val frac = (left.toFloat() / ViewPageState.SAVE_WAIT_SECONDS).coerceIn(0f, 1f)
                Canvas(Modifier.size(96.dp)) {
                    val w = 7.dp.toPx(); val d = size.minDimension - w
                    val tl = Offset(w / 2, w / 2)
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
                    drawArc(arc, -90f, 360f * frac, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                }
                Text(ViewUtils.bnNum(left), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Box(contentAlignment = Alignment.Center) { AppIcon("clock", size = 20.dp, tint = c.muted) }
            Text(
                ViewUtils.bnNum(left) + " সেকেন্ড অপেক্ষা করুন...।",
                color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, textAlign = TextAlign.Center
            )
        }
    }
}

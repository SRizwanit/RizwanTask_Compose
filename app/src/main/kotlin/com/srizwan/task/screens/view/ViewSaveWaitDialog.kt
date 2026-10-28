package com.srizwan.task.screens.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Custom dialog of "সেভ ও পরের": a card with a header (vector clock mark, title, short help line), a counting-down ring (vector, drawn on a Canvas,
 * gliding smoothly and turning orange for the last 5 seconds) with the seconds left in its middle, a thin remaining-time bar, the three
 * things that will happen when it reaches zero (text save, proofed mark, next page) and a "বাতিল করুন" button.
 * It opens when [ViewPageState.saveWait] is not null; when the count reaches zero the dialog closes and the normal
 * save -> proofed -> next page work runs ([ViewPageState.proofAndNext]). Tapping outside does nothing (a stray tap must not cancel);
 * the back button and the cancel button cancel ([ViewPageState.cancelSaveWait]) and nothing is saved.
 */
@Composable
fun ViewSaveWaitDialog(st: ViewPageState) {
    val left = st.saveWait ?: return
    val c = viewColors()
    val total = ViewPageState.SAVE_WAIT_SECONDS
    val urgent = left <= 5
    val accent = if (urgent) c.warn else c.pri

    // the ring and the bar drain smoothly over each second (the number itself changes once a second)
    val frac = remember { Animatable(1f) }
    LaunchedEffect(left) { frac.animateTo(((left - 1).coerceAtLeast(0)).toFloat() / total, tween(1000, easing = LinearEasing)) }

    Dialog(
        onDismissRequest = { st.cancelSaveWait() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.padding(16.dp).widthIn(max = 360.dp).fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(shape).background(c.bg).border(1.dp, c.line, shape)
                .semantics { contentDescription = "${ViewUtils.bnNum(left)} সেকেন্ড অপেক্ষা করুন" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /* header */
            Row(
                Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon("clock", size = 20.dp, tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("সেভ ও পরের", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                    Text("একটু পরেই আপনার কাজ সংরক্ষিত হবে", color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                /* countdown ring */
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    val track = c.line
                    Canvas(Modifier.size(112.dp)) {
                        val w = 8.dp.toPx(); val d = size.minDimension - w
                        val tl = Offset(w / 2, w / 2)
                        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
                        drawArc(accent, -90f, 360f * frac.value, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ViewUtils.bnNum(left), color = if (urgent) c.warn else c.fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                        Text("সেকেন্ড", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
                    }
                }
                Text(
                    ViewUtils.bnNum(left) + " সেকেন্ড অপেক্ষা করুন...।",
                    color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp, textAlign = TextAlign.Center
                )

                /* remaining-time bar */
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(c.line)) {
                    Box(
                        Modifier.fillMaxWidth(frac.value.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(99.dp))
                            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.75f))))
                    )
                }

                /* what happens at zero */
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("সময় শেষ হলে যা হবে", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                    StepRow("file-text", "পেইজের টেক্সট সেভ হবে", c.pri)
                    StepRow("check", "পেইজ প্রুফড চিহ্নিত হবে", c.ok)
                    StepRow("left", "পরের পেইজে যাওয়া হবে", c.muted, flip = true)
                }

                /* cancel */
                val src = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .clickable(interactionSource = src, indication = null) { st.cancelSaveWait() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    AppIcon("x", size = 16.dp, tint = c.err)
                    Box(Modifier.size(8.dp))
                    Text("বাতিল করুন", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("বাতিল করলে কিছুই সেভ হবে না", color = c.muted, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** One line of the "what happens at zero" list: a small tinted vector mark + the text. [flip] turns the arrow to point right. */
@Composable
private fun StepRow(icon: String, text: String, tint: Color, flip: Boolean = false) {
    val c = viewColors()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(icon, size = 14.dp, tint = tint, modifier = if (flip) Modifier.graphicsLayer { rotationZ = 180f } else Modifier)
        }
        Text(text, color = c.fg, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

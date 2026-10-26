package com.srizwan.task.screens.task

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.Fmt
import com.srizwan.task.screens.admin.SmBtn
import com.srizwan.task.ui.AppCard

/**
 * React task/GuideDialog.jsx (3-ঙ-২): the first-time guide as a step-by-step dialog — tag, animated stage ([GuideStage]),
 * title, text, numbered sub-steps (the one the scene is on is lit; all of them with reduced motion), dots and the buttons
 * "পরে দেখব" (not on the last step) / "আগের" (not on the first) / "পরের" -> "শুরু করি".
 * It is shown while the parent keeps it in the composition (React `ref.open()`), always starting at step 1.
 * [onClosed] is called for every way of closing (buttons, back, outside tap): the parent remembers that the guide was
 * seen and loads the notices.
 */
@Composable
fun GuideDialog(onClosed: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var gI by remember { mutableIntStateOf(0) }
    var phase by remember(gI) { mutableIntStateOf(-1) }   // the lit sub-step (PHASE_ALL = all), reset on every step
    val g = GUIDE[gI]
    val last = gI == GUIDE.size - 1
    val scene = remember(gI) { g.sc() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.94f).dp

    Dialog(onDismissRequest = onClosed, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppCard(Modifier.fillMaxWidth(0.94f).widthIn(max = 460.dp).heightIn(max = maxH), padding = 16.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                // .gtag
                Box(
                    Modifier.padding(bottom = 10.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFA8761F), Color(0xFFF2D27A))))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) { Text(g.tag, color = Color(0xFF4A3200), fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
                // .gstage
                val stageShape = RoundedCornerShape(14.dp)
                GuideStage(
                    scene, onPhase = { phase = it },
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(stageShape)
                        .background(Color(0xFF0B3D33)).border(1.dp, Color(0xFFD9AB43).copy(alpha = 0.55f), stageShape)
                )
                Text(g.t, color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 6.dp))
                Text(g.d, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                if (g.subs.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.subs.forEachIndexed { i, s -> GuideSub(i, s, on = phase == PHASE_ALL || phase == i) }
                    }
                }
                // .dots
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GUIDE.indices.forEach { i ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == gI) cs.primary else cs.outline.copy(alpha = 0.6f)))
                    }
                }
                // .acts
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (!last) SmBtn("পরে দেখব", onClick = onClosed)
                    if (gI != 0) SmBtn("আগের", onClick = { gI = maxOf(0, gI - 1) })
                    SmBtn(if (last) "শুরু করি" else "পরের", tone = "primary", onClick = { if (last) onClosed() else gI += 1 })
                }
            }
        }
    }
}

/** `.gsubs li`: number circle + text; lit (full opacity, gold edge and tint) while the scene is on this sub-step. */
@Composable
private fun GuideSub(i: Int, text: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val a by animateFloatAsState(if (on) 1f else 0.62f, tween(250), label = "gsA")
    val edge by animateColorAsState(if (on) Color(0xFFD9AB43) else Color(0xFF788C84).copy(alpha = 0.35f), tween(250), label = "gsE")
    val bg by animateColorAsState(if (on) Color(0xFFD9AB43).copy(alpha = 0.15f) else Color.Transparent, tween(250), label = "gsB")
    Row(
        Modifier.fillMaxWidth().alpha(a).clip(shape).background(bg).border(1.dp, edge, shape).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.padding(top = 1.dp).size(21.dp).clip(CircleShape).background(Color(0xFF0B5F4C)), contentAlignment = Alignment.Center) {
            Text(Fmt.bn(i + 1), color = Color(0xFFF7E2A0), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(text, color = cs.onSurface, fontSize = 13.5.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
    }
}

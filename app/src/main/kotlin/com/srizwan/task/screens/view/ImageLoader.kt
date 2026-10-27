package com.srizwan.task.screens.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.ui.viewColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * React `view/ImageLoader.jsx` — image loading: round ring + percentage.
 * [target] = 0..100, or null when the total size is unknown (the ring spins and "..." is shown).
 * The shown number moves smoothly toward the target (each frame: 20% of the gap, at least 0.5, snaps when closer than 0.4).
 * Wrap the call in `key(page)` so every page starts again from 0.
 */
@Composable
fun ImageLoader(target: Float?, modifier: Modifier = Modifier) {
    val vc = viewColors()
    val tg by rememberUpdatedState(target)
    var shown by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        while (true) {
            val t = tg ?: return@LaunchedEffect
            val d = t - shown
            shown = if (abs(d) < 0.4f) t else shown + max(abs(d) * 0.2f, 0.5f) * sign(d)
            if (shown == t) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    val v = shown.coerceIn(0f, 100f)
    val r = v.roundToInt()
    val full = target != null && r >= 100
    val barColor = if (full) vc.ok else vc.pri
    val spin by rememberInfiniteTransition(label = "ldr").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")

    Column(
        modifier.semantics { if (target != null) progressBarRangeInfo = ProgressBarRangeInfo(r.toFloat(), 0f..100f) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            val line = vc.line
            Canvas(Modifier.size(92.dp)) {
                val k = size.width / 100f                         // SVG viewBox 100: r = 40, stroke 6
                val sw = 6f * k; val rad = 40f * k
                val tl = Offset(size.width / 2 - rad, size.height / 2 - rad); val sz = Size(rad * 2, rad * 2)
                drawArc(line, 0f, 360f, false, tl, sz, style = Stroke(sw))
                if (target == null) {
                    // dasharray 60 of the 251.3 circumference = about 86 degrees, rotating
                    drawArc(barColor, -90f + spin, 60f / 251.327f * 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                } else if (v > 0f) {
                    drawArc(barColor, -90f, v / 100f * 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (target == null) "…" else ViewUtils.bnNum(r), color = vc.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Text("%", color = vc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 1.dp))
            }
        }
        Box(Modifier.size(width = 1.dp, height = 14.dp))   // .ldr-sub (min-height 14px, empty)
    }
}

package com.srizwan.task.screens.task

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srizwan.task.ui.goldColor

/**
 * "No internet" illustration, drawn entirely with Canvas (no XML drawable, no emoji): a soft round badge, a wifi symbol
 * (three arcs + a dot) and a diagonal slash across it. Colours come from the current theme, so it follows every design
 * theme and day / night. It floats up and down very gently, like [com.srizwan.task.ui.EmptyArt].
 * Not placed anywhere yet - the dialog that uses it comes in step 'ঘ-১-খ' of CONVERSION_NOTES.md.
 */
@Composable
fun NoInternetArt(modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val cs = MaterialTheme.colorScheme
    val ink = cs.primary
    val soft = cs.surfaceVariant
    val line = cs.outline
    val warn = cs.error
    val gold = goldColor()
    val bob by rememberInfiniteTransition(label = "noNetArt").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "noNetArtBob"
    )
    Canvas(modifier.size(size).graphicsLayer { translationY = (bob - 0.5f) * 5.dp.toPx() }) {
        val u = this.size.minDimension / 96f   // drawn on a 96 x 96 grid
        // soft ground shadow
        drawOval(ink.copy(alpha = 0.12f), Offset(22f * u, 86f * u), Size(52f * u, 7f * u))
        // round badge with a thin rim
        drawCircle(soft, 42f * u, Offset(48f * u, 46f * u))
        drawCircle(line, 42f * u, Offset(48f * u, 46f * u), style = Stroke(1.5f * u))

        // wifi symbol: three arcs fanning up from the dot (the arcs fade outward)
        val cx = 48f * u
        val cy = 62f * u
        val sw = 5.5f * u
        listOf(12f to 1f, 24f to 0.8f, 36f to 0.6f).forEach { (r, a) ->
            drawArc(
                color = ink.copy(alpha = a),
                startAngle = -135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - r * u, cy - r * u), size = Size(2f * r * u, 2f * r * u),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        drawCircle(ink, 4.4f * u, Offset(cx, cy))

        // slash: a badge-coloured stroke first so the wifi symbol looks cut, then the coloured line on top
        val from = Offset(26f * u, 22f * u)
        val to = Offset(70f * u, 70f * u)
        drawLine(soft, from, to, strokeWidth = 11f * u, cap = StrokeCap.Round)
        drawLine(warn, from, to, strokeWidth = 5f * u, cap = StrokeCap.Round)

        // small gold sparkle (same accent as the other illustrations)
        val sx = 80f * u
        val sy = 18f * u
        val sr = 3.5f * u
        drawLine(gold, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
        drawLine(gold, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    }
}

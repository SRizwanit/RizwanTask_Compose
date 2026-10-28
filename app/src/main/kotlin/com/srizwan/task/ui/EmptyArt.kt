package com.srizwan.task.ui

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small illustration for an empty list / failed load, drawn with the current theme colours (so it follows every design
 * theme and day / night, including the colour fade). It floats up and down very gently.
 * [kind]: "books" | "search" | "list" | "trophy" | "wallet" | "cloud" | anything else = an empty tray.
 */
@Composable
fun EmptyArt(kind: String, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val cs = MaterialTheme.colorScheme
    val pal = ArtPal(cs.primary, cs.secondary, goldColor(), cs.outline, cs.surfaceVariant, cs.surface)
    val bob by rememberInfiniteTransition(label = "emptyArt").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyArtBob"
    )
    Canvas(modifier.size(size).graphicsLayer { translationY = (bob - 0.5f) * 5.dp.toPx() }) {
        val u = this.size.minDimension / 96f   // the art is drawn on a 96 x 96 grid
        drawOval(pal.ink.copy(alpha = 0.12f), Offset(20f * u, 84f * u), Size(56f * u, 8f * u))   // soft ground shadow
        when (kind) {
            "books" -> artBooks(u, pal)
            "search" -> artSearch(u, pal)
            "list" -> artList(u, pal)
            "trophy" -> artTrophy(u, pal)
            "wallet" -> artCoins(u, pal)
            "cloud" -> artCloud(u, pal)
            else -> artInbox(u, pal)
        }
    }
}

private class ArtPal(val ink: Color, val ink2: Color, val gold: Color, val line: Color, val soft: Color, val face: Color)

private fun DrawScope.spark(cx: Float, cy: Float, r: Float, c: Color, w: Float) {
    drawLine(c, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = w, cap = StrokeCap.Round)
}

/** A stack of three books. */
private fun DrawScope.artBooks(u: Float, p: ArtPal) {
    fun book(x: Float, y: Float, w: Float, c: Color) {
        drawRoundRect(c, Offset(x * u, y * u), Size(w * u, 18f * u), CornerRadius(4f * u))
        drawLine(Color.White.copy(alpha = 0.35f), Offset((x + 7f) * u, (y + 3f) * u), Offset((x + 7f) * u, (y + 15f) * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.22f), Offset((x + 12f) * u, (y + 9f) * u), Offset((x + w - 8f) * u, (y + 9f) * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
    }
    book(14f, 64f, 66f, p.ink)
    book(20f, 46f, 58f, p.gold)
    rotate(-8f, Offset(48f * u, 36f * u)) { book(24f, 28f, 50f, p.ink2) }
    spark(80f * u, 22f * u, 4f * u, p.gold, 1.8f * u)
    spark(12f * u, 40f * u, 3f * u, p.ink2, 1.5f * u)
}

/** A page with a magnifying glass. */
private fun DrawScope.artSearch(u: Float, p: ArtPal) {
    drawRoundRect(p.face, Offset(20f * u, 14f * u), Size(44f * u, 60f * u), CornerRadius(7f * u))
    drawRoundRect(p.line, Offset(20f * u, 14f * u), Size(44f * u, 60f * u), CornerRadius(7f * u), style = Stroke(1.6f * u))
    for (i in 0..2) {
        val y = (26f + i * 12f) * u
        drawLine(p.soft, Offset(28f * u, y), Offset((56f - i * 6f) * u, y), strokeWidth = 4f * u, cap = StrokeCap.Round)
    }
    drawCircle(p.face.copy(alpha = 0.55f), 17f * u, Offset(58f * u, 56f * u))
    drawCircle(p.ink, 17f * u, Offset(58f * u, 56f * u), style = Stroke(5f * u))
    drawLine(p.ink, Offset(70f * u, 68f * u), Offset(82f * u, 80f * u), strokeWidth = 7f * u, cap = StrokeCap.Round)
    drawArc(Color.White.copy(alpha = 0.6f), 200f, 60f, false, Offset(47f * u, 45f * u), Size(22f * u, 22f * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
    spark(78f * u, 20f * u, 4f * u, p.gold, 1.8f * u)
}

/** A clipboard with three empty rows. */
private fun DrawScope.artList(u: Float, p: ArtPal) {
    drawRoundRect(p.face, Offset(22f * u, 14f * u), Size(52f * u, 68f * u), CornerRadius(9f * u))
    drawRoundRect(p.line, Offset(22f * u, 14f * u), Size(52f * u, 68f * u), CornerRadius(9f * u), style = Stroke(1.6f * u))
    drawRoundRect(p.gold, Offset(36f * u, 8f * u), Size(24f * u, 12f * u), CornerRadius(5f * u))
    for (i in 0..2) {
        val y = (36f + i * 15f) * u
        drawCircle(p.ink.copy(alpha = 0.55f), 3.5f * u, Offset(33f * u, y))
        drawLine(p.soft, Offset(42f * u, y), Offset((64f - i * 7f) * u, y), strokeWidth = 4.5f * u, cap = StrokeCap.Round)
    }
    spark(82f * u, 26f * u, 4f * u, p.gold, 1.8f * u)
}

/** A golden cup. */
private fun DrawScope.artTrophy(u: Float, p: ArtPal) {
    val g = p.gold
    drawArc(g.copy(alpha = 0.8f), 90f, 180f, false, Offset(17f * u, 22f * u), Size(22f * u, 24f * u), style = Stroke(4.5f * u))
    drawArc(g.copy(alpha = 0.8f), 270f, 180f, false, Offset(57f * u, 22f * u), Size(22f * u, 24f * u), style = Stroke(4.5f * u))
    drawRoundRect(g, Offset(30f * u, 14f * u), Size(36f * u, 22f * u), CornerRadius(3f * u))
    drawArc(g, 0f, 180f, true, Offset(30f * u, 18f * u), Size(36f * u, 36f * u))
    drawRect(g.copy(alpha = 0.85f), Offset(44f * u, 52f * u), Size(8f * u, 14f * u))
    drawRoundRect(p.ink, Offset(32f * u, 66f * u), Size(32f * u, 9f * u), CornerRadius(3f * u))
    drawLine(Color.White.copy(alpha = 0.45f), Offset(37f * u, 20f * u), Offset(37f * u, 36f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
    spark(80f * u, 18f * u, 4f * u, p.ink2, 1.8f * u)
    spark(14f * u, 14f * u, 3f * u, g, 1.5f * u)
}

/** Two coins. */
private fun DrawScope.artCoins(u: Float, p: ArtPal) {
    drawCircle(p.gold.copy(alpha = 0.6f), 17f * u, Offset(34f * u, 58f * u))
    drawCircle(Color.White.copy(alpha = 0.35f), 11f * u, Offset(34f * u, 58f * u), style = Stroke(2f * u))
    drawCircle(p.gold, 22f * u, Offset(58f * u, 46f * u))
    drawCircle(Color.White.copy(alpha = 0.5f), 15f * u, Offset(58f * u, 46f * u), style = Stroke(2.4f * u))
    drawArc(Color.White.copy(alpha = 0.6f), 200f, 70f, false, Offset(44f * u, 32f * u), Size(28f * u, 28f * u), style = Stroke(2.4f * u, cap = StrokeCap.Round))
    spark(80f * u, 18f * u, 4f * u, p.ink2, 1.8f * u)
    spark(12f * u, 36f * u, 3f * u, p.gold, 1.5f * u)
}

/** A cloud with an exclamation mark (load failed). */
private fun DrawScope.artCloud(u: Float, p: ArtPal) {
    fun blob(grow: Float, c: Color) {
        drawCircle(c, (15f + grow) * u, Offset(34f * u, 54f * u))
        drawCircle(c, (19f + grow) * u, Offset(52f * u, 46f * u))
        drawCircle(c, (13f + grow) * u, Offset(68f * u, 56f * u))
        drawRoundRect(c, Offset((22f - grow) * u, (54f - grow) * u), Size((56f + 2f * grow) * u, (17f + 2f * grow) * u), CornerRadius((8f + grow) * u))
    }
    blob(1.6f, p.line)
    blob(0f, p.soft)
    drawLine(p.ink, Offset(48f * u, 40f * u), Offset(48f * u, 54f * u), strokeWidth = 5f * u, cap = StrokeCap.Round)
    drawCircle(p.ink, 3f * u, Offset(48f * u, 63f * u))
    spark(80f * u, 24f * u, 3.5f * u, p.gold, 1.6f * u)
}

/** An empty tray with a blank sheet (the default). */
private fun DrawScope.artInbox(u: Float, p: ArtPal) {
    drawRoundRect(p.face, Offset(30f * u, 24f * u), Size(36f * u, 34f * u), CornerRadius(5f * u))
    drawRoundRect(p.line, Offset(30f * u, 24f * u), Size(36f * u, 34f * u), CornerRadius(5f * u), style = Stroke(1.5f * u))
    drawLine(p.soft, Offset(37f * u, 33f * u), Offset(59f * u, 33f * u), strokeWidth = 4f * u, cap = StrokeCap.Round)
    drawLine(p.soft, Offset(37f * u, 42f * u), Offset(52f * u, 42f * u), strokeWidth = 4f * u, cap = StrokeCap.Round)
    val tray = Path().apply {
        moveTo(14f * u, 50f * u); lineTo(30f * u, 50f * u); lineTo(34f * u, 58f * u); lineTo(62f * u, 58f * u)
        lineTo(66f * u, 50f * u); lineTo(82f * u, 50f * u); lineTo(78f * u, 76f * u); lineTo(18f * u, 76f * u); close()
    }
    drawPath(tray, p.soft)
    drawPath(tray, p.line, style = Stroke(1.6f * u))
    spark(80f * u, 20f * u, 4f * u, p.gold, 1.8f * u)
    spark(14f * u, 30f * u, 3f * u, p.ink2, 1.5f * u)
}

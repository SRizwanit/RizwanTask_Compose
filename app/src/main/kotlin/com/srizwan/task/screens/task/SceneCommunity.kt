package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight

/**
 * React `sceneCommunity` (3-ঙ-৫): the last step — four icons pop in one after another: a podium (র‍্যাংক), a ringing bell
 * with a red dot (নোটিশ), a ticket (টিকেট) and the profile avatar with a pulsing ring (প্রোফাইল). The icon of the
 * active sub-step grows a little. 400 x 200, 190 frames. [GuideScene.phaseAt] = 0..3 (f<44, <88, <132, then 3).
 */
private val COMM_X = listOf(50f, 150f, 250f, 350f)
private val COMM_LABEL = listOf("\u09B0\u200D\u09CD\u09AF\u09BE\u0982\u0995", "নোটিশ", "টিকেট", "প্রোফাইল")
private val COMM_ST = listOf(6, 46, 90, 134)
private val COMM_GOLD = Color(0xFFF2D27A)
private val COMM_BROWN = Color(0xFFA8761F)
private val COMM_INK = Color(0xFF4A3200)
private val COMM_CREAM = Color(0xFFF7E2A0)

private val BELL = Path().apply {
    moveTo(0f, -28f); cubicTo(-15f, -28f, -22f, -17f, -22f, -3f); lineTo(-22f, 12f); lineTo(-30f, 22f)
    lineTo(30f, 22f); lineTo(22f, 12f); lineTo(22f, -3f); cubicTo(22f, -17f, 15f, -28f, 0f, -28f); close()
}
private val BODY = Path().apply {
    moveTo(-16f, 20f); cubicTo(-16f, 8f, 16f, 8f, 16f, 20f); close()
}

private fun DrawScope.podium(f: Int) {
    val xs = listOf(-25f, -7.5f, 10f)
    val cols = listOf(Color(0xFFC9D6D1), COMM_GOLD, Color(0xFFD9A066))
    val hs = listOf(34f, 50f, 24f)
    for (k in 0 until 3) {
        val h = interpolate(f, listOf(COMM_ST[0] + 6 + k * 6, COMM_ST[0] + 26 + k * 6), listOf(0f, hs[k]), E.out)
        if (h > 0f) drawRoundRect(cols[k], Offset(xs[k], 30f - h), Size(15f, h), CornerRadius(3f))
    }
    drawCircle(COMM_CREAM, radius = 6f, center = Offset(0f, -34f))
    drawCircle(COMM_BROWN, radius = 6f, center = Offset(0f, -34f), style = Stroke(1.6f))
}

private fun DrawScope.bell(f: Int, ph: Int) {
    val t = Math.max(0, f - COMM_ST[1] - 6) / FPS.toFloat()
    val ang = if (ph == 1 || f > COMM_ST[1]) (22.0 * Math.exp(-t * .9) * Math.sin(t * 16.0)).toFloat() else 0f
    rotate(ang, Offset(0f, -31f)) {
        drawPath(BELL, COMM_GOLD)
        drawPath(BELL, COMM_BROWN, style = Stroke(2f))
        drawCircle(COMM_GOLD, radius = 3.4f, center = Offset(0f, -31f))
        drawCircle(COMM_BROWN, radius = 3.4f, center = Offset(0f, -31f), style = Stroke(1.6f))
        drawCircle(Color(0xFFC9972B), radius = 5f, center = Offset(0f, 30f))
    }
    val a = Math.min(1f, Math.max(0f, spring(f - COMM_ST[1] - 14, damping = 7, stiffness = 200)))
    groupAlpha(a) {
        drawCircle(Color(0xFFE5484D), radius = 7f, center = Offset(19f, -22f))
        drawCircle(Color.White, radius = 7f, center = Offset(19f, -22f), style = Stroke(2f))
    }
}

private fun DrawScope.ticket() {
    drawRoundRect(COMM_GOLD, Offset(-36f, -22f), Size(72f, 44f), CornerRadius(9f))
    drawRoundRect(COMM_BROWN, Offset(-36f, -22f), Size(72f, 44f), CornerRadius(9f), style = Stroke(2f))
    drawLine(COMM_BROWN, Offset(14f, -20f), Offset(14f, 20f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
    drawRoundRect(COMM_INK.copy(alpha = .7f), Offset(-26f, -10f), Size(32f, 5f), CornerRadius(2.5f))
    drawRoundRect(COMM_INK.copy(alpha = .45f), Offset(-26f, 2f), Size(22f, 5f), CornerRadius(2.5f))
    drawCircle(COMM_INK.copy(alpha = .6f), radius = 5f, center = Offset(25f, 0f))
}

private fun DrawScope.avatar(f: Int) {
    if (f > COMM_ST[3]) {
        val rf = ((f - COMM_ST[3]) % 40) / 40f
        drawCircle(COMM_GOLD.copy(alpha = ((1 - rf) * .9f).coerceIn(0f, 1f)), radius = 26f + 16f * rf, style = Stroke(2.4f))
    }
    drawCircle(Color(0xFF0A4F40), radius = 27f)
    drawCircle(COMM_GOLD, radius = 27f, style = Stroke(3f))
    drawCircle(COMM_CREAM, radius = 9f, center = Offset(0f, -7f))
    drawPath(BODY, COMM_CREAM)
}

fun sceneCommunity(): GuideScene {
    val dur = 190
    fun phase(f: Int) = if (f < 44) 0 else if (f < 88) 1 else if (f < 132) 2 else 3
    return GuideScene(400f, 200f, dur, phaseAt = { f -> phase(f) }) { f, env ->
        // SVG objectBoundingBox gradient (0,0)->(1,1) on 400x200 == this vector in user units
        drawRect(
            Brush.linearGradient(listOf(Color(0xFF0A4F40), Color(0xFF127A63)), start = Offset.Zero, end = Offset(160f, 320f)),
            size = Size(400f, 200f)
        )
        val ph = phase(f)
        groupAlpha(fadeIO(f, dur)) {
            for (i in 0 until 4) {
                val s = Math.max(0f, spring(f - COMM_ST[i], damping = 9, stiffness = 150))
                val hi = if (ph == i) 1.1f else 1f
                groupAlpha(Math.min(1f, s * 1.2f)) {
                    tf(COMM_X[i], 88f, s * hi) {
                        when (i) {
                            0 -> podium(f)
                            1 -> bell(f, ph)
                            2 -> ticket()
                            else -> avatar(f)
                        }
                        svgText(env, COMM_LABEL[i], 0f, 62f, 13f, COMM_CREAM, FontWeight.Bold, middle = true)
                    }
                }
            }
        }
    }
}

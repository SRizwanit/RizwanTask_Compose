package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * React `sceneWelcome` (3-ঙ-৩): the first guide step — an open book floating under a crescent moon and twinkling stars,
 * lines of text writing themselves on the pages, and a gold tick popping in. 400 x 200, 150 frames, all vector.
 */
private val STARS = listOf(
    40 to 30, 90 to 72, 150 to 24, 250 to 42, 372 to 112, 60 to 150, 340 to 172, 300 to 18, 120 to 160
)
private val STAR = Color(0xFFF7E2A0)
private val GOLD = Color(0xFFF2D27A)
private val GREEN = Color(0xFF0B5F4C)

private val PAGE_L = Path().apply {
    moveTo(-4f, -36f); cubicTo(-28f, -44f, -60f, -42f, -84f, -34f); lineTo(-84f, 38f)
    cubicTo(-60f, 30f, -28f, 28f, -4f, 36f); close()
}
private val PAGE_R = Path().apply {
    moveTo(4f, -36f); cubicTo(28f, -44f, 60f, -42f, 84f, -34f); lineTo(84f, 38f)
    cubicTo(60f, 30f, 28f, 28f, 4f, 36f); close()
}
private val TICK = Path().apply { moveTo(-7f, 0f); lineTo(-2f, 6f); lineTo(8f, -5f) }

fun sceneWelcome(): GuideScene {
    val dur = 150
    return GuideScene(400f, 200f, dur) { f, _ ->
        // background (SVG objectBoundingBox gradient (0,0)->(1,1) on 400x200 == this vector in user units)
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF0A4F40), Color(0xFF16A583)),
                start = Offset.Zero, end = Offset(160f, 320f)
            ),
            size = Size(400f, 200f)
        )
        groupAlpha(fadeIO(f, dur)) {
            // stars
            STARS.forEachIndexed { i, s ->
                val a = (.3f + .7f * (.5f + .5f * Math.sin(f / 7.0 + i * 1.7).toFloat())).coerceIn(0f, 1f)
                drawCircle(STAR.copy(alpha = a), radius = 1.5f + (i % 3) * .55f, center = Offset(s.first.toFloat(), s.second.toFloat()))
            }
            // crescent moon: disc minus a shifted disc
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(347f, 38f), 17f)) }
            clipPath(bite, ClipOp.Difference) { drawCircle(GOLD, radius = 21f, center = Offset(338f, 46f)) }

            // book (floats, springs in)
            val s = .55f + .45f * spring(f, damping = 10, stiffness = 90)
            tf(200f, 112f + Math.sin(f / 14.0).toFloat() * 3f, s) {
                drawRoundRect(Color(0xFF073B30), Offset(-92f, -44f), Size(184f, 86f), CornerRadius(10f))
                drawPath(PAGE_L, Color.White)
                drawPath(PAGE_R, Color(0xFFF8F3E3))
                drawRect(Color(0xFFC9972B), Offset(-1.5f, -36f), Size(3f, 72f))
                for (i in 0 until 4) {
                    val w = interpolate(f, listOf(24 + i * 9, 42 + i * 9), listOf(0, 52 - (i % 2) * 16))
                    val y = -20f + i * 13f
                    drawRoundRect(GREEN.copy(alpha = .38f), Offset(-68f, y), Size(w, 4f), CornerRadius(2f))
                    drawRoundRect(GREEN.copy(alpha = .38f), Offset(16f, y), Size(w * .92f, 4f), CornerRadius(2f))
                }
            }

            // tick badge pops in
            tf(262f, 66f, Math.max(0f, spring(f - 58, damping = 8, stiffness = 140))) {
                drawCircle(GOLD, radius = 17f)
                drawCircle(Color.White, radius = 17f, style = Stroke(3f))
                drawPath(TICK, Color(0xFF4A3200), style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight

/**
 * React `sceneBook` (3-ঙ-৪): step 2 "open your favourite book" — three book cards whose progress bars fill, a finger taps
 * the middle one, it lifts and gets a gold ring, and a "কিতাব খুলছে…" pill appears. 400 x 200, 140 frames, all vector.
 */
private val COLS = listOf(Color(0xFF0B5F4C), Color(0xFFA8761F), Color(0xFF2C6E8F))
private val CX = listOf(80f, 200f, 320f)
private val PR = listOf(.62f, .35f, .8f)
private val BOOK_GOLD = Color(0xFFF2D27A)

private fun DrawScope.bookCard(color: Color, progress: Float) {
    drawRoundRect(Color.White, Offset(-52f, -72f), Size(104f, 144f), CornerRadius(11f))
    drawRoundRect(Color(0xFFCFE3DC), Offset(-52f, -72f), Size(104f, 144f), CornerRadius(11f), style = Stroke(1f))
    drawRoundRect(color, Offset(-44f, -64f), Size(88f, 62f), CornerRadius(7f))
    val ink = Color(0xFFF7E2A0)
    drawRect(ink, Offset(-14f, -46f), Size(28f, 30f), style = Stroke(2.4f, join = StrokeJoin.Round))
    drawLine(ink, Offset(0f, -46f), Offset(0f, -16f), strokeWidth = 2.4f)
    drawRoundRect(Color(0xFFCFD8D4), Offset(-44f, 6f), Size(78f, 6f), CornerRadius(3f))
    drawRoundRect(Color(0xFFE3EAE7), Offset(-44f, 18f), Size(52f, 5f), CornerRadius(2.5f))
    drawRoundRect(Color(0xFFDFEAE6), Offset(-44f, 36f), Size(88f, 7f), CornerRadius(3.5f))
    if (progress > 0f) drawRoundRect(Color(0xFF16A583), Offset(-44f, 36f), Size(progress, 7f), CornerRadius(3.5f))
    drawRoundRect(Color(0xFFE3B94F).copy(alpha = .75f), Offset(-44f, 50f), Size(60f, 5f), CornerRadius(2.5f))
}

fun sceneBook(): GuideScene {
    val dur = 140
    val finger = touchP(350, 196, 200, 84, t0 = 28, t1 = 52, tp = 56, r = 20)
    return GuideScene(400f, 200f, dur) { f, env ->
        drawRect(Color(0xFFE8F4EF), size = Size(400f, 200f))
        val op = fadeIO(f, dur)
        val lift = spring(f - 58, damping = 9, stiffness = 160)
        groupAlpha(op) {
            for (i in 0 until 3) {
                val w = interpolate(f, listOf(8 + i * 4, 44 + i * 4), listOf(0f, 88f * PR[i]), E.out)
                val sc = if (i == 1) 1f + .07f * Math.max(0f, lift) else 1f
                tf(CX[i], 98f - (if (i == 1) 6f * Math.max(0f, lift) else 0f), sc) { bookCard(COLS[i], w) }
            }
            // gold selection ring around the middle card
            val selA = if (f >= 58) Math.min(1f, (f - 58) / 6f) else 0f
            groupAlpha(selA) {
                tf(CX[1], 92f, 1f + .07f * Math.max(0f, lift)) {
                    drawRoundRect(BOOK_GOLD, Offset(-56f, -76f), Size(112f, 152f), CornerRadius(14f), style = Stroke(3.5f))
                }
            }
            // "opening the book" pill
            groupAlpha(interpolate(f, listOf(74, 84), listOf(0, 1))) {
                drawRoundRect(BOOK_GOLD, Offset(100f, 176f), Size(200f, 20f), CornerRadius(10f))
                svgText(env, "কিতাব খুলছে…", 200f, 186.5f, 11f, Color(0xFF4A3200), FontWeight.Bold, middle = true, central = true)
            }
            touch(f, finger, op)
        }
    }
}

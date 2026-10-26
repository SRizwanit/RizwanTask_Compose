package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight

/**
 * React `sceneGoal` (3-ঙ-৫): the "লক্ষ্য, টানা দিন আর চালিয়ে যান" step — today's goal counts up with a filling bar,
 * the streak counts up and its dots light one by one, then the "চালিয়ে যান" button pops when tapped.
 * 400 x 200, 180 frames. [GuideScene.phaseAt] = 0 (goal), 1 (from f 56, streak), 2 (from f 112, continue) — the sub-steps.
 */
private val CARD_LINE = Color(0xFFCFE3DC)
private val TRACK = Color(0xFFDFEAE6)
private val PLAY = Path().apply { moveTo(118f, 142f); lineTo(132f, 150f); lineTo(118f, 158f); close() }

private fun DrawScope.card(x: Float, w: Float) {
    drawRoundRect(Color.White, Offset(x, 16f), Size(w, 92f), CornerRadius(14f))
    drawRoundRect(CARD_LINE, Offset(x, 16f), Size(w, 92f), CornerRadius(14f), style = Stroke(1f))
}

fun sceneGoal(): GuideScene {
    val dur = 180
    val finger = touchP(350, 196, 250, 150, t0 = 112, t1 = 136, tp = 140, r = 18)
    return GuideScene(400f, 200f, dur, phaseAt = { f -> if (f < 56) 0 else if (f < 112) 1 else 2 }) { f, env ->
        drawRect(Color(0xFFE8F4EF), size = Size(400f, 200f))
        val op = fadeIO(f, dur)
        groupAlpha(op) {
            // today's goal
            card(22f, 176f)
            val green = Color(0xFF0B5F4C)
            svgText(env, "আজকের লক্ষ্য", 38f, 40f, 12f, green, FontWeight.Bold)
            val n = Math.round(interpolate(f, listOf(6, 50), listOf(0, 14), E.out))
            svgText(env, bd(n), 38f, 74f, 28f, green, FontWeight.Bold)
            svgText(env, "/ ২০ পেইজ", 72f, 74f, 14f, Color(0xFF6B7280))
            drawRoundRect(TRACK, Offset(38f, 86f), Size(144f, 8f), CornerRadius(4f))
            val bar = interpolate(f, listOf(6, 50), listOf(0f, 144f * 14f / 20f), E.out)
            if (bar > 0f) drawRoundRect(Color(0xFF16A583), Offset(38f, 86f), Size(bar, 8f), CornerRadius(4f))

            // streak
            card(212f, 166f)
            val brown = Color(0xFFA8761F)
            svgText(env, "টানা কাজ", 228f, 40f, 12f, brown, FontWeight.Bold)
            svgText(env, bd(Math.round(interpolate(f, listOf(58, 90), listOf(0, 7), E.out))), 228f, 74f, 28f, brown, FontWeight.Bold)
            svgText(env, "দিন", 258f, 74f, 14f, Color(0xFF6B7280))
            for (i in 0 until 3) {
                drawCircle(if (f > 74 + i * 10) Color(0xFFF2D27A) else TRACK, radius = 7f, center = Offset(236f + i * 28f, 92f))
            }

            // "চালিয়ে যান" button pops (scales around its centre)
            val pop = 1f + .04f * Math.max(0f, spring(f - 114, damping = 8, stiffness = 170))
            tf(200f * (1f - pop), 150f * (1f - pop), pop) {
                drawRoundRect(green, Offset(22f, 124f), Size(356f, 52f), CornerRadius(26f))
                drawPath(PLAY, Color(0xFFF7E2A0))
                svgText(env, "চালিয়ে যান", 214f, 150.5f, 16f, Color(0xFFF7E2A0), FontWeight.Bold, middle = true, central = true)
            }
            touch(f, finger, op)
        }
    }
}

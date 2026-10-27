package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/**
 * React `sceneEarn` (3-ঙ-৪): step "ফলাফল" — page rows get stamped (অনুমোদিত / সংশোধন), a coin flies from each approved
 * row into the wallet and the total counts up, then a "পেমেন্ট রিকোয়েস্ট" button rises and is tapped.
 * 400 x 200, 250 frames. [GuideScene.phaseAt] = 0 (stamps), 1 (from f 84, earnings), 2 (from f 150, request) — the sub-steps.
 * The tick that React writes as a "✓" character in the sent button is a vector tick here.
 */
private class EarnRow(val y: Float, val t: String, val ok: Boolean)

private val ROWS = listOf(EarnRow(14f, "পেইজ ১২", true), EarnRow(64f, "পেইজ ১৩", true), EarnRow(114f, "পেইজ ১৪", false))
private val ST = listOf(36, 62, 88)
private val ARR = listOf(70, 96)
private val EGOLD = Color(0xFFF2D27A)
private val COIN_EDGE = Color(0xFFA8761F)

private fun DrawScope.row(env: SceneEnv, r: EarnRow) {
    drawRoundRect(Color.White, Offset(14f, r.y), Size(184f, 42f), CornerRadius(10f))
    drawRoundRect(Color(0xFFCFE3DC), Offset(14f, r.y), Size(184f, 42f), CornerRadius(10f), style = Stroke(1f))
    val edge = Color(0xFF9DB8AF)
    drawRoundRect(Color(0xFFE8F1ED), Offset(27f, r.y + 9f), Size(17f, 24f), CornerRadius(3f))
    drawRoundRect(edge, Offset(27f, r.y + 9f), Size(17f, 24f), CornerRadius(3f), style = Stroke(1f))
    drawRoundRect(edge, Offset(31f, r.y + 16f), Size(9f, 2.5f), CornerRadius(1.2f))
    drawRoundRect(edge, Offset(31f, r.y + 22f), Size(9f, 2.5f), CornerRadius(1.2f))
    svgText(env, r.t, 54f, r.y + 21f, 13f, Color(0xFF12372F), FontWeight.SemiBold, central = true)
}

private fun DrawScope.stamp(env: SceneEnv, r: EarnRow) {
    val c = if (r.ok) Color(0xFF16A583) else Color(0xFFD64545)
    drawRoundRect(Color.White, Offset(-33f, -12f), Size(66f, 24f), CornerRadius(6f))
    drawRoundRect(c, Offset(-33f, -12f), Size(66f, 24f), CornerRadius(6f), style = Stroke(2.4f))
    svgText(
        env, if (r.ok) "অনুমোদিত" else "সংশোধন", 0f, 0.5f, 11.5f,
        if (r.ok) Color(0xFF0D7A5F) else Color(0xFFC0392B), FontWeight.Bold, middle = true, central = true
    )
}

private fun DrawScope.wallet() {
    drawRoundRect(Color(0xFF0B5F4C), Offset(-36f, -22f), Size(72f, 46f), CornerRadius(9f))
    drawLine(EGOLD.copy(alpha = .7f), Offset(-36f, -8f), Offset(36f, -8f), strokeWidth = 2.2f)
    drawRoundRect(Color(0xFF0A4F40), Offset(12f, -2f), Size(24f, 16f), CornerRadius(6f))
    drawRoundRect(EGOLD, Offset(12f, -2f), Size(24f, 16f), CornerRadius(6f), style = Stroke(2f))
    drawCircle(EGOLD, radius = 3.2f, center = Offset(23f, 6f))
}

private fun DrawScope.coin() {
    drawCircle(EGOLD, radius = 8f)
    drawCircle(COIN_EDGE, radius = 8f, style = Stroke(1.6f))
    drawCircle(COIN_EDGE, radius = 4.4f, style = Stroke(1.2f))
}

private val SENT_TICK = Path().apply { moveTo(0f, 0f); lineTo(3.4f, 3.8f); lineTo(9.6f, -4f) }

fun sceneEarn(): GuideScene {
    val dur = 250
    val finger = touchP(392, 196, 338, 170, t0 = 158, t1 = 180, tp = 184, r = 18)
    return GuideScene(400f, 200f, dur, phaseAt = { f -> if (f < 84) 0 else if (f < 150) 1 else 2 }) { f, env ->
        drawRect(Color(0xFFEEF7F3), size = Size(400f, 200f))
        val op = fadeIO(f, dur)
        groupAlpha(op) {
            // page rows slide in, then get their stamps
            for (i in 0 until 3) {
                val ap = interpolate(f, listOf(6 + i * 10, 20 + i * 10), listOf(0, 1), E.out)
                groupAlpha(ap) {
                    translate((1 - ap) * -30f, 0f) {
                        row(env, ROWS[i])
                        val sp = spring(f - ST[i], damping = 7, stiffness = 220)
                        val a = if (f >= ST[i]) Math.min(1f, (f - ST[i]) / 3f) else 0f
                        groupAlpha(a) {
                            tf(150f, ROWS[i].y + 21f, if (f >= ST[i]) 1f + .9f * (1f - Math.min(1f, sp)) else 2f, -9f) { stamp(env, ROWS[i]) }
                        }
                    }
                }
            }
            var earned = 0f
            for (i in 0 until 2) if (f >= ARR[i]) earned += .2f

            // wallet bumps each time a coin lands
            val bump = Math.max(spring(f - ARR[0], damping = 6, stiffness = 260), 0f)
            val bump2 = Math.max(spring(f - ARR[1], damping = 6, stiffness = 260), 0f)
            val ws = 1f + .1f * Math.sin(Math.PI * Math.min(1f, bump)).toFloat() * (if (f < ARR[1]) 1f else 0f) +
                .1f * Math.sin(Math.PI * Math.min(1f, bump2)).toFloat() * (if (f >= ARR[1]) 1f else 0f)
            tf(297f, 58f, ws) { wallet() }

            svgText(env, bd(String.format(Locale.US, "%.2f", earned)) + " পয়েন্টস", 297f, 112f, 25f, Color(0xFF0B4F40), FontWeight.Bold, middle = true)
            svgText(env, "মোট পয়েন্টস", 297f, 130f, 11f, Color(0xFF59706A), middle = true)

            // "payment request" button rises, is tapped, turns into the sent state
            val bp = Math.max(spring(f - 140, damping = 11, stiffness = 120), 0f)
            groupAlpha(Math.min(1f, bp)) {
                tf(297f, 162f + (1f - Math.min(1f, bp)) * 34f, 1f) {
                    val pressed = f >= 186
                    drawRoundRect(if (pressed) Color(0xFF16A583) else Color(0xFF0B5F4C), Offset(-76f, -18f), Size(152f, 36f), CornerRadius(11f))
                    if (!pressed) {
                        svgText(env, "পেমেন্ট রিকোয়েস্ট", 0f, 1f, 12.5f, Color.White, FontWeight.Bold, middle = true, central = true)
                    } else {
                        val label = "রিকোয়েস্ট পাঠানো হয়েছে"
                        val w = env.text.measure(label, androidx.compose.ui.text.TextStyle(
                            fontFamily = com.srizwan.task.ui.AnekBangla, fontWeight = FontWeight.Bold, fontSize = 11f.toSp())).size.width
                        val total = 9.6f + 5f + w
                        val x0 = -total / 2f
                        translate(x0, 1f) {
                            drawPath(SENT_TICK, Color.White, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        svgText(env, label, x0 + 9.6f + 5f, 1f, 11f, Color.White, FontWeight.Bold, central = true)
                    }
                }
            }

            // coins fly from the approved rows to the wallet
            for (i in 0 until 2) {
                val t = interpolate(f, listOf(ST[i] + 10, ARR[i]), listOf(0, 1), E.inOut)
                if (f > ST[i] + 10 && f < ARR[i]) {
                    val x = 205f + (297f - 205f) * t
                    val y = ROWS[i].y + 21f + (52f - ROWS[i].y - 21f) * t - 34f * Math.sin(Math.PI * t).toFloat()
                    tf(x, y, 1f) { coin() }
                }
            }
            touch(f, finger, op)
        }
    }
}

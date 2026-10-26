package com.srizwan.task.screens.task

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import com.srizwan.task.ui.AnekBangla

/**
 * React task/guideScenes.js (3-ঙ-৩): the drawing helpers every scene shares — `tf`, `touch` (finger dot + ripple),
 * group opacity — and `shotScene` (screenshot + spotlight + ring + number badge + finger).
 * Everything draws in the scene's own viewBox units (the player scales them to the screen), vector only.
 */

private val GOLD = Color(0xFFF2D27A)
private val GOLD_INK = Color(0xFF4A3200)
private val GREEN_INK = Color(0xFF0B5F4C)
private val DIM = Color(0xFF03231B)

/** React `tf(el, x, y, s, r)`: `translate(x y) rotate(r) scale(s)` — scale first, then rotate, then move. */
fun DrawScope.tf(x: Float, y: Float, s: Float = 1f, r: Float = 0f, block: DrawScope.() -> Unit) {
    if (s <= 0.0005f) return
    translate(x, y) { rotate(r, Offset.Zero) { scale(s, Offset.Zero) { block() } } }
}

/**
 * SVG group `opacity`: the children are drawn together and then faded as one picture (so overlapping shapes do not show
 * through each other, unlike giving every shape its own alpha).
 */
fun DrawScope.groupAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 0.999f) { block(); return }
    val paint = Paint().apply { this.alpha = alpha }
    drawIntoCanvas { c ->
        c.saveLayer(Rect(-100000f, -100000f, 100000f, 100000f), paint)
        block()
        c.restore()
    }
}

/** The finger: a white disc with a green ring and a green centre (React `gDot`), drawn at the current origin. */
private fun DrawScope.fingerDot(r: Float, ring: Float) {
    drawCircle(Color.White.copy(alpha = .92f), radius = r * .55f)
    drawCircle(GREEN_INK, radius = r * .55f, style = Stroke(ring))
    drawCircle(GREEN_INK, radius = r * .18f)
}

/** React `touch` argument `P`: [from] -> [to] between frames [t0] and [t1], tap at [tp], ripple radius [r]. */
class TouchP(val from: Offset, val to: Offset, val t0: Int, val t1: Int, val tp: Int, val r: Float)

fun touchP(fromX: Number, fromY: Number, toX: Number, toY: Number, t0: Int, t1: Int, tp: Int, r: Number) =
    TouchP(Offset(fromX.toFloat(), fromY.toFloat()), Offset(toX.toFloat(), toY.toFloat()), t0, t1, tp, r.toFloat())

/**
 * React `touchSvg(r)` + `touch(q, f, P, op)`: a finger dot moving from `P.from` to `P.to`, pressing at frame `P.tp`
 * and sending out a ripple; [op] fades the whole thing (0..1).
 */
fun DrawScope.touch(f: Int, P: TouchP, op: Float) {
    val m = interpolate(f, listOf(P.t0, P.t1), listOf(0, 1), E.inOut)
    val x = P.from.x + (P.to.x - P.from.x) * m
    val y = P.from.y + (P.to.y - P.from.y) * m
    val s = interpolate(f, listOf(P.tp, P.tp + 4, P.tp + 9), listOf(1f, .78f, 1f))
    val rf = interpolate(f, listOf(P.tp + 2, P.tp + 28), listOf(0, 1), E.out)
    if (f >= P.tp + 2) {
        drawCircle(
            Color.White.copy(alpha = ((1 - rf) * .9f * op).coerceIn(0f, 1f)),
            radius = P.r * (.5f + 2.2f * rf), center = P.to, style = Stroke(2.4f)
        )
    }
    val a = op * interpolate(f, listOf(P.t0 - 6, P.t0), listOf(0, 1))
    groupAlpha(a) { tf(x, y, s) { fingerDot(P.r, 2f) } }
}

/**
 * React `shotScene(key, W, H, T, opt)`: a screenshot ([key] in [GuideImages], stretched to W x H) where each target of [T]
 * in turn is lit by a spotlight (the rest dims), circled by a pulsing gold ring, numbered by a badge and tapped by a finger.
 * [D] = frames per target; [swapKey] / [swapAt] = React `opt.swap`: a second screenshot fades in while target [swapAt] is shown.
 * The active target number is the sub-step the dialog lights up ([GuideScene.phaseAt]).
 */
fun shotScene(key: String, W: Int, H: Int, T: List<ShotTarget>, D: Int = 74, swapKey: String? = null, swapAt: Int = 0): GuideScene {
    val n = T.size
    val total = n * D + 34
    val sw = W / 120f
    val pad = W / 90f
    val R = W / 32f
    val wf = W.toFloat()
    val hf = H.toFloat()
    return GuideScene(wf, hf, total, phaseAt = { f -> Math.min(n - 1, f / D) }) { f, env ->
        val idx = Math.min(n - 1, f / D)
        val lf = f - idx * D
        val c = T[idx]
        val pv = if (idx > 0) T[idx - 1] else ShotTarget(c.x - W * .04f, c.y - W * .04f, c.w + W * .08f, c.h + W * .08f)
        val m = interpolate(lf, listOf(0, 16), listOf(0, 1), E.inOut)
        val x = pv.x + (c.x - pv.x) * m - pad
        val y = pv.y + (c.y - pv.y) * m - pad
        val w = pv.w + (c.w - pv.w) * m + 2 * pad
        val h = pv.h + (c.h - pv.h) * m + 2 * pad
        val rx = Math.min(w, h) * .28f
        val op = interpolate(f, listOf(0, 10), listOf(0, 1)) * interpolate(f, listOf(total - 12, total), listOf(1, 0))

        // screenshot (+ the one that fades in over it)
        env.image(key)?.let { drawImage(it, dstSize = IntSize(W, H)) }
        if (swapKey != null) {
            val a = if (idx > swapAt) 1f else if (idx == swapAt) interpolate(lf, listOf(24, 38), listOf(0, 1)) else 0f
            if (a > 0f) env.image(swapKey)?.let { drawImage(it, dstSize = IntSize(W, H), alpha = a) }
        }

        // spotlight: dark film over everything except the rounded hole around the target
        val film = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, wf, hf))
            addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(rx)))
        }
        drawPath(film, DIM.copy(alpha = (.62f * op).coerceIn(0f, 1f)))

        // pulsing ring
        drawRoundRect(
            GOLD, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(rx),
            style = Stroke(sw), alpha = (op * (.78f + .22f * Math.sin(f * .28))).toFloat().coerceIn(0f, 1f)
        )

        // number badge (pops in with a spring at the ring's top-left)
        groupAlpha(op) {
            tf(x + rx * .2f, y + rx * .2f, Math.max(0f, spring(lf - 6, damping = 9, stiffness = 170))) {
                drawCircle(GOLD, radius = R * .62f)
                drawCircle(GOLD_INK, radius = R * .62f, style = Stroke(sw * .4f))
                val layout = env.text.measure(
                    bd(idx + 1),
                    TextStyle(fontFamily = AnekBangla, fontWeight = FontWeight.Bold, fontSize = (R * .8f).toSp(), color = GOLD_INK)
                )
                drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
            }
        }

        // finger + ripple, at the target's lower-right so the icon stays visible
        fun ctr(k: ShotTarget) = Offset(k.x + k.w * .62f, k.y + k.h * .7f)
        val cc = ctr(c)
        val pc = if (idx > 0) ctr(T[idx - 1]) else Offset(cc.x + W * .22f, cc.y + H * .9f)
        val dm = interpolate(lf, listOf(0, 18), listOf(0, 1), E.inOut)
        val rf = interpolate(lf, listOf(20, 46), listOf(0, 1), E.out)
        if (lf >= 20) {
            drawCircle(
                Color.White.copy(alpha = ((1 - rf) * .9f * op).coerceIn(0f, 1f)),
                radius = R * (.5f + 2.2f * rf), center = cc, style = Stroke(sw * 1.2f)
            )
        }
        groupAlpha(op) {
            tf(
                pc.x + (cc.x - pc.x) * dm, pc.y + (cc.y - pc.y) * dm,
                interpolate(lf, listOf(18, 22, 27), listOf(1f, .78f, 1f))
            ) { fingerDot(R, sw * .8f) }
        }
    }
}

/**
 * SVG `<text>` in scene units: [x] is the start (or the middle with [middle]); [y] is the alphabetic baseline, or the
 * middle of the line with [central] (`dominant-baseline="central"`). Returns the text width (for laying out a tick beside it).
 */
fun DrawScope.svgText(
    env: SceneEnv, text: String, x: Float, y: Float, size: Float, color: Color,
    weight: FontWeight = FontWeight.Normal, middle: Boolean = false, central: Boolean = false
): Float {
    val layout = env.text.measure(
        text, TextStyle(fontFamily = AnekBangla, fontWeight = weight, fontSize = size.toSp(), color = color)
    )
    val left = if (middle) x - layout.size.width / 2f else x
    val top = if (central) y - layout.size.height / 2f else y - layout.firstBaseline
    drawText(layout, topLeft = Offset(left, top))
    return layout.size.width.toFloat()
}

package com.srizwan.task.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.srizwan.task.core.MotionLevel
import com.srizwan.task.core.MotionStore
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * PIN accepted -> a short, fully vector (Canvas + Compose, no images, no XML drawables, no emoji) animation:
 *
 *   1. DIGITS : the four PIN digits sit in a 2x2 grid of tiles, spread onto a circle and whirl around it
 *               (1.5 turns) while the circle shrinks into the centre.
 *   2. VERIFY : a ring appears in the centre and fills up with a 0 -> 100 counter, little dots around it light up.
 *   3. DONE   : the ring pops into a filled circle (spring overshoot), a shock-wave + confetti burst flies out and
 *               a tick mark draws itself. Then [onFinished] is called (the caller opens the next screen).
 *
 * All colours come from the active app theme (primary / secondary / gold / ok + surface), so it follows the chosen
 * design theme (Garden, Ocean, Twilight) and day / night. The "অ্যানিমেশন কমান" choice is respected:
 * LOW = no confetti, OFF = straight to the tick.
 */

private const val GATHER_MS = 420
private const val ORBIT_MS = 950
private const val VERIFY_MS = 1150
private const val TICK_DELAY_MS = 200L
private const val TICK_MS = 430
private const val BURST_MS = 950
private const val HOLD_MS = 620L

private enum class Stage { DIGITS, VERIFY, DONE }

/** Grid corners (2x2) the tiles start from: top-left, top-right, bottom-right, bottom-left. */
private val Grid = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

private class Spark(val angle: Float, val dist: Float, val size: Float, val kind: Int, val color: Int, val spin: Float)

private val Sparks: List<Spark> = run {
    val rnd = Random(42)
    List(22) { i ->
        Spark(
            angle = i * (360f / 22f) + rnd.nextFloat() * 10f - 5f,
            dist = 64f + rnd.nextFloat() * 46f,
            size = 3.2f + rnd.nextFloat() * 3.4f,
            kind = i % 3,
            color = i % 4,
            spin = rnd.nextFloat() * 360f
        )
    }
}

private fun easeIn(t: Float) = t * t

@Composable
fun PinSuccessOverlay(pin: String, onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    val ok = okColor()
    val haptic = LocalHapticFeedback.current
    val finished by rememberUpdatedState(onFinished)
    val level = remember { MotionStore.level }
    val digits = remember(pin) { pin.take(4).padEnd(4, '•').map { it.toString() } }

    val enter = remember { Animatable(0f) }
    val gather = remember { Animatable(0f) }
    val orbit = remember { Animatable(0f) }
    val ringIn = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var stage by remember { mutableStateOf(Stage.DIGITS) }

    // the PIN screen must not be usable (or left with Back) while this plays
    BackHandler(true) { }

    LaunchedEffect(Unit) { enter.animateTo(1f, tween(240)) }

    LaunchedEffect(Unit) {
        if (level != MotionLevel.OFF) {
            gather.animateTo(1f, tween(GATHER_MS, easing = FastOutSlowInEasing))
            orbit.animateTo(1f, tween(ORBIT_MS, easing = CubicBezierEasing(0.55f, 0f, 0.35f, 1f)))
            stage = Stage.VERIFY
            coroutineScope {
                launch { ringIn.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
                progress.animateTo(1f, tween(VERIFY_MS, easing = FastOutSlowInEasing))
            }
        }
        stage = Stage.DONE
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 300f)) }
            if (level == MotionLevel.FULL) launch { burst.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing)) }
            delay(TICK_DELAY_MS)
            tick.animateTo(1f, tween(TICK_MS, easing = FastOutSlowInEasing))
            delay(HOLD_MS)
        }
        finished()
    }

    val percent by remember { derivedStateOf { (progress.value * 100f).toInt().coerceIn(0, 100) } }
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(cs.background.copy(alpha = 0.88f))
            .clickable(interactionSource = interaction, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp)
                .graphicsLayer { val s = 0.92f + 0.08f * enter.value; scaleX = s; scaleY = s }
        ) {
            AppCard(padding = 24.dp) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(200.dp), contentAlignment = Alignment.Center) {
                    // ring / success circle / confetti / tick
                    Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        val rin = ringIn.value
                        val prog = progress.value
                        val p = pop.value
                        val pc = p.coerceIn(0f, 1f)

                        // ---- VERIFY ring (fades while the success circle pops in) ----
                        val ringAlpha = rin * (1f - (pc * 2f).coerceIn(0f, 1f))
                        if (ringAlpha > 0.01f) {
                            val rr = 46.dp.toPx()
                            val sw = 6.dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.14f * ringAlpha), Color.Transparent), center = c, radius = rr),
                                radius = rr, center = c
                            )
                            drawCircle(cs.outline.copy(alpha = 0.35f * ringAlpha), radius = rr, center = c, style = Stroke(sw))
                            rotate(-90f, c) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(cs.secondary.copy(alpha = ringAlpha), cs.primary.copy(alpha = ringAlpha), cs.secondary.copy(alpha = ringAlpha)),
                                        center = c
                                    ),
                                    startAngle = 0f, sweepAngle = 360f * prog, useCenter = false,
                                    topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2f, rr * 2f),
                                    style = Stroke(sw, cap = StrokeCap.Round)
                                )
                            }
                            // glowing head of the arc
                            val ha = Math.toRadians(-90.0 + 360.0 * prog)
                            val head = Offset(c.x + (cos(ha) * rr).toFloat(), c.y + (sin(ha) * rr).toFloat())
                            if (prog > 0.01f && prog < 0.999f) {
                                drawCircle(cs.primary.copy(alpha = 0.28f * ringAlpha), radius = sw * 1.4f, center = head)
                                drawCircle(cs.onPrimary.copy(alpha = ringAlpha), radius = sw * 0.38f, center = head)
                            }
                            // 12 countdown dots around the ring, lit one by one
                            for (k in 0 until 12) {
                                val a = Math.toRadians(-90.0 + k * 30.0)
                                val dp0 = Offset(c.x + (cos(a) * 61.dp.toPx()).toFloat(), c.y + (sin(a) * 61.dp.toPx()).toFloat())
                                val lit = (k + 1) / 12f <= prog + 0.001f
                                drawCircle(
                                    (if (lit) cs.primary else cs.outline).copy(alpha = (if (lit) 0.95f else 0.35f) * ringAlpha),
                                    radius = (if (lit) 2.6f else 2f).dp.toPx(), center = dp0
                                )
                            }
                        }

                        // ---- SUCCESS circle ----
                        if (p > 0.001f) {
                            val base = 40.dp.toPx()
                            // shock-wave
                            drawCircle(
                                cs.primary.copy(alpha = (1f - pc) * 0.5f),
                                radius = base * (1f + 0.9f * pc), center = c,
                                style = Stroke(width = 3.dp.toPx() * (1f - pc) + 1f)
                            )
                            // soft glow
                            drawCircle(
                                Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.34f * pc), Color.Transparent), center = c, radius = base * 1.9f),
                                radius = base * 1.9f, center = c
                            )
                            val r = base * max(p, 0f)
                            drawCircle(
                                Brush.linearGradient(
                                    listOf(cs.primary, cs.secondary),
                                    start = Offset(c.x - base, c.y - base), end = Offset(c.x + base, c.y + base)
                                ),
                                radius = r, center = c
                            )
                            drawCircle(Color.White.copy(alpha = 0.25f * pc), radius = r * 0.88f, center = c, style = Stroke(1.5.dp.toPx()))
                        }

                        // ---- confetti ----
                        val bt = burst.value
                        if (bt > 0f && bt < 1f) drawSparks(c, bt, listOf(cs.primary, cs.secondary, gold, ok))

                        // ---- tick mark, drawn stroke by stroke ----
                        val tk = tick.value
                        if (tk > 0f) {
                            val path = Path().apply {
                                moveTo(c.x - 15.dp.toPx(), c.y + 1.dp.toPx())
                                lineTo(c.x - 5.dp.toPx(), c.y + 12.dp.toPx())
                                lineTo(c.x + 16.dp.toPx(), c.y - 11.dp.toPx())
                            }
                            val pm = PathMeasure()
                            pm.setPath(path, false)
                            val seg = Path()
                            pm.getSegment(0f, pm.length * tk, seg, true)
                            scale(0.8f + 0.2f * pc, c) {
                                drawPath(seg, cs.onPrimary, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }

                    // percent counter in the middle of the ring
                    Text(
                        text = percent.toString(),
                        color = cs.primary, fontFamily = AnekBangla, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                        modifier = Modifier.graphicsLayer { alpha = ringIn.value * (1f - (pop.value * 3f).coerceIn(0f, 1f)) }
                    )

                    // the four PIN digits
                    if (stage == Stage.DIGITS) {
                        digits.forEachIndexed { i, d -> DigitTile(d, i, { gather.value }, { orbit.value }) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.TopCenter) {
                    Crossfade(targetState = stage, animationSpec = tween(220), label = "pinStageText") { s ->
                        val (title, sub) = when (s) {
                            Stage.DIGITS -> "PIN গ্রহণ করা হয়েছে" to "আপনার কোডটি নিরাপদে প্রসেস হচ্ছে…"
                            Stage.VERIFY -> "যাচাই হচ্ছে…" to "আপনার তথ্য নিরাপদে মিলিয়ে দেখা হচ্ছে"
                            Stage.DONE -> "প্রবেশ সফল" to "স্বাগতম! আপনার অ্যাকাউন্টে নিয়ে যাওয়া হচ্ছে…"
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(title, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(sub, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** One PIN digit tile: starts in the 2x2 grid (turned 45 degrees), spreads onto a circle, whirls and shrinks into the centre. */
@Composable
private fun DigitTile(digit: String, index: Int, g: () -> Float, o: () -> Float) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(46.dp)
            .graphicsLayer {
                val gv = g()
                val ov = o()
                val shrink = easeIn(((ov - 0.45f) / 0.55f).coerceIn(0f, 1f))
                val radius = 64.dp.toPx() * (1f - shrink)
                val ang = Math.toRadians(-135.0 + index * 90.0 + 540.0 * ov)
                val cx = (cos(ang) * radius).toFloat()
                val cy = (sin(ang) * radius).toFloat()
                translationX = lerp(Grid[index].first * 30.dp.toPx(), cx, gv)
                translationY = lerp(Grid[index].second * 30.dp.toPx(), cy, gv)
                rotationZ = lerp(45f, 0f, gv) + 540f * ov
                val appear = (gv / 0.35f).coerceIn(0f, 1f)
                val s = (0.55f + 0.45f * appear) * (1f - 0.65f * shrink)
                scaleX = s
                scaleY = s
                alpha = appear * (1f - ((ov - 0.82f) / 0.18f).coerceIn(0f, 1f))
            }
            .clip(shape)
            .background(cs.surface)
            .background(Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.22f), cs.secondary.copy(alpha = 0.10f))))
            .border(1.5.dp, Brush.linearGradient(listOf(cs.primary, cs.secondary)), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(digit, color = cs.primary, fontFamily = AnekBangla, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

/** Confetti: circles, turning rounded squares and diamonds fly out of the success circle and fade. */
private fun DrawScope.drawSparks(c: Offset, e: Float, colors: List<Color>) {
    val out = 1f - (1f - e) * (1f - e) * (1f - e)
    val a = (1f - e) * (1f - e)
    for (s in Sparks) {
        val d = (34f + (s.dist - 34f) * out).dp.toPx()
        val rad = Math.toRadians(s.angle.toDouble())
        val p = Offset(c.x + (cos(rad) * d).toFloat(), c.y + (sin(rad) * d).toFloat())
        val col = colors[s.color].copy(alpha = a)
        val sz = s.size.dp.toPx() * (1f - 0.4f * e)
        when (s.kind) {
            0 -> drawCircle(col, radius = sz, center = p)
            1 -> rotate(s.spin + 220f * e, p) {
                drawRoundRect(col, topLeft = Offset(p.x - sz, p.y - sz), size = Size(sz * 2f, sz * 2f), cornerRadius = CornerRadius(sz * 0.4f))
            }
            else -> rotate(45f + s.spin * e, p) {
                drawRect(col, topLeft = Offset(p.x - sz * 0.8f, p.y - sz * 0.8f), size = Size(sz * 1.6f, sz * 1.6f))
            }
        }
    }
}

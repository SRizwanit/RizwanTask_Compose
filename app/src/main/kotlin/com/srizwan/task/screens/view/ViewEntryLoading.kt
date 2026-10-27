package com.srizwan.task.screens.view

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as dsScale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.LocalAppDark
import com.srizwan.task.RizwanBackground

private val GreenLight = Color(0xFF0B5F4C)
private val GreenDark = Color(0xFF43A58B)
private val GreenSoft = Color(0xFF43A58B)

private const val BOOK_P1 = "M16 9.2C12.7 6.9 8.7 6.5 4.8 8v16c4-1.5 7.9-1.1 11.2 1.2 3.3-2.3 7.2-2.7 11.2-1.2V8c-3.9-1.5-7.9-1.1-11.2 1.2Z"
private const val BOOK_P2 = "M16 9.4v15.7M8.2 11.3c2-.4 4.1 0 5.8 1.1M8.2 15.1c2-.4 4.1 0 5.8 1.1M23.8 11.3c-2-.4-4.1 0-5.8 1.1M23.8 15.1c-2-.4-4.1 0-5.8 1.1"

/**
 * React `view-entry-loading` block of `View.jsx`: shown while the book and settings load.
 * Card with an orbit ring, a vector book mark, two pulsing dots, the "RIZWANTASK . কিতাব প্রুফিং" line, title, description,
 * an indeterminate progress bar and a pulsing hint. All drawn as vectors (no emoji / bitmap).
 */
@Composable
fun ViewEntryLoading() {
    val cs = MaterialTheme.colorScheme
    val dark = LocalAppDark.current
    val green = if (dark) GreenDark else GreenLight
    val line = cs.outline.copy(alpha = 0.5f)
    val muted = cs.onSurfaceVariant

    val inf = rememberInfiniteTransition(label = "viewEntry")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "spin")
    val pulse1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "p1")
    val pulse2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, delayMillis = 500), RepeatMode.Reverse), label = "p2")
    val hint by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "hint")
    val bar by inf.animateFloat(
        -1.2f, 3.0f,
        infiniteRepeatable(keyframes { durationMillis = 1550; -1.2f at 0; 1.6f at 852; 3.0f at 1550 }),
        label = "bar"
    )
    val rise = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.Animatable(0f).animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f))) { rise.floatValue = value }
    }

    RizwanBackground(Modifier.fillMaxSize(), fireflies = false, flowers = false) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp, vertical = 24.dp)
                .semantics { contentDescription = "কিতাবের তথ্য প্রস্তুত হচ্ছে" },
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.widthIn(max = 390.dp).fillMaxWidth()
                    .graphicsLayer { alpha = rise.floatValue; translationY = (1f - rise.floatValue) * 9.dp.toPx() }
                    .shadow(14.dp, shape, ambientColor = Color(0x120F172A), spotColor = Color(0x120F172A))
                    .clip(shape).background(cs.surface.copy(alpha = 0.94f)).border(1.dp, line, shape)
                    .padding(start = 25.dp, end = 25.dp, top = 34.dp, bottom = 27.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* mark: orbit ring + book + dots */
                Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(94.dp)) {
                        val sw = 2.dp.toPx(); val inset = 1.dp.toPx() + sw / 2
                        val tl = Offset(inset, inset); val sz = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(line, 0f, 360f, false, tl, sz, style = Stroke(sw))
                        rotate(spin) { drawArc(green, -135f, 180f, false, tl, sz, style = Stroke(sw)) }
                    }
                    Box(
                        Modifier.size(68.dp).clip(RoundedCornerShape(22.dp))
                            .background(
                                if (dark) Brush.linearGradient(listOf(Color(0x2443A58B), Color(0x2443A58B)))
                                else Brush.linearGradient(listOf(Color(0x210B5F4C), Color(0x0B0B5F4C)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(37.dp)) {
                            val k = size.width / 32f
                            dsScale(k, k, pivot = Offset.Zero) {
                                val st = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                drawPath(PathParser().parsePathString(BOOK_P1).toPath(), green, style = st)
                                drawPath(PathParser().parsePathString(BOOK_P2).toPath(), green, style = st)
                            }
                        }
                    }
                    Dot(green, pulse1, Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 17.dp), 1f)
                    Dot(green, pulse2, Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 15.dp), 0.55f)
                }
                Box(Modifier.height(23.dp))

                /* eyebrow */
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RIZWANTASK", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
                    Box(Modifier.size(4.dp).alpha(0.8f).background(green, CircleShape))
                    Text("কিতাব প্রুফিং", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
                }
                Box(Modifier.height(10.dp))
                Text("কিতাব প্রস্তুত করা হচ্ছে", color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 33.sp)
                Box(Modifier.height(8.dp))
                Text("আপনার কিতাবের তথ্য ও সেটিংস লোড হচ্ছে।", color = muted, fontSize = 13.sp, lineHeight = 23.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp))
                Box(Modifier.height(22.dp))

                /* indeterminate progress */
                Box(Modifier.widthIn(max = 210.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(line)) {
                    Box(
                        Modifier.fillMaxWidth(0.38f).height(5.dp)
                            .graphicsLayer { translationX = bar * size.width }
                            .clip(RoundedCornerShape(99.dp))
                            .background(Brush.horizontalGradient(listOf(green, GreenSoft)))
                    )
                }
                Box(Modifier.height(13.dp))
                Text("অনুগ্রহ করে অপেক্ষা করুন…", color = muted, fontSize = 11.sp, lineHeight = 17.6.sp, modifier = Modifier.alpha(hint))
            }
        }
    }
}

/** 9dp dot with a 2dp ring and a soft halo, pulsing between scale .85 / opacity .45 and scale 1.15 / opacity 1. */
@Composable
private fun Dot(color: Color, t: Float, modifier: Modifier, base: Float) {
    val s = 0.85f + 0.30f * t
    val a = (0.45f + 0.55f * t) * base
    Box(
        modifier.size(9.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }
            .drawBehind { drawCircle(color.copy(alpha = 0.10f), radius = size.minDimension / 2 + 3.dp.toPx()) }
            .background(color, CircleShape)
    )
}

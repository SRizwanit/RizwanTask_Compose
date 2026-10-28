package com.srizwan.task.screens.view

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.srizwan.task.LocalAppDark
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.ViewBoot
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.ui.AppIcon
import kotlin.math.roundToInt

private val GreenLight = Color(0xFF0B5F4C)
private val GreenDark = Color(0xFF43A58B)
private val GreenSoft = Color(0xFF43A58B)

private const val BOOK_P1 = "M16 9.2C12.7 6.9 8.7 6.5 4.8 8v16c4-1.5 7.9-1.1 11.2 1.2 3.3-2.3 7.2-2.7 11.2-1.2V8c-3.9-1.5-7.9-1.1-11.2 1.2Z"
private const val BOOK_P2 = "M16 9.4v15.7M8.2 11.3c2-.4 4.1 0 5.8 1.1M8.2 15.1c2-.4 4.1 0 5.8 1.1M23.8 11.3c-2-.4-4.1 0-5.8 1.1M23.8 15.1c-2-.4-4.1 0-5.8 1.1"

/**
 * React `view-entry-loading` block of `View.jsx`, made real: covers the View page from the tap until the first page is ready.
 * Card with an orbit ring, a vector book mark, two pulsing dots, the "RIZWANTASK . কিতাব প্রুফিং" line, title, the stage text,
 * a real progress bar with the percentage in Bangla digits ([ViewBoot.pct]) and a pulsing hint. When something goes wrong
 * (no internet / slow internet / a failed request / internet is back) a custom toast slides up from the bottom ([ViewBoot.issue]).
 * All drawn as vectors (no emoji / bitmap). The screen takes every touch (the page below is not reachable); back button / arrow go [onBack].
 */
@Composable
fun ViewEntryLoading(boot: ViewBoot, onBack: () -> Unit) {
    AnimatedVisibility(visible = !boot.done, enter = fadeIn(tween(0)), exit = fadeOut(tween(320))) {
        BackHandler(onBack = onBack)
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
        val rise = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            androidx.compose.animation.core.Animatable(0f).animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f))) { rise.floatValue = value }
        }
        // the bar and the number glide to the real value
        val shown by animateFloatAsState(boot.pct, tween(380, easing = LinearOutSlowInEasing), label = "pct")
        val pctInt = shown.coerceIn(0f, 100f).roundToInt()

        Box(Modifier.fillMaxSize().background(cs.background).pointerInput(Unit) { detectTapGestures { } }) {
            RizwanBackground(Modifier.fillMaxSize(), fireflies = false, flowers = false) {
                Box(
                    Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp, vertical = 24.dp)
                        .semantics { contentDescription = "কিতাবের তথ্য প্রস্তুত হচ্ছে"; progressBarRangeInfo = ProgressBarRangeInfo(pctInt.toFloat(), 0f..100f) },
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
                                // while the internet is gone the ring stands still (nothing is moving)
                                val a = if (boot.issue == ViewBoot.Issue.OFFLINE) -135f else spin
                                rotate(a) { drawArc(green, -135f, 180f, false, tl, sz, style = Stroke(sw)) }
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
                        // the stage text follows the real work (account / book / settings / page list / image + text)
                        Text(
                            boot.detail, color = muted, fontSize = 13.sp, lineHeight = 23.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 290.dp), maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Box(Modifier.height(20.dp))

                        /* real progress: label + percentage (Bangla digits) above the bar */
                        Row(Modifier.widthIn(max = 250.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text("লোড হচ্ছে", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(ViewUtils.bnNum(pctInt) + "%", color = green, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                        }
                        Box(Modifier.height(7.dp))
                        Box(Modifier.widthIn(max = 250.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(line)) {
                            Box(
                                Modifier.fillMaxWidth((shown / 100f).coerceIn(0f, 1f)).height(6.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Brush.horizontalGradient(listOf(green, GreenSoft)))
                            )
                        }
                        Box(Modifier.height(14.dp))
                        Text("অনুগ্রহ করে অপেক্ষা করুন…", color = muted, fontSize = 11.sp, lineHeight = 17.6.sp, modifier = Modifier.alpha(hint))
                    }
                }

                /* back arrow: the screen covers the page, so the way out must be here */
                Box(
                    Modifier.align(Alignment.TopStart).systemBarsPadding().padding(start = 14.dp, top = 10.dp)
                        .size(40.dp).clip(CircleShape).background(cs.surface.copy(alpha = 0.9f)).border(1.dp, line, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { AppIcon("left", size = 20.dp, tint = cs.onSurface) }

                BootToast(boot, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 28.dp))
            }
        }
    }
}

private val ToastBg = Color(0xF2111827)
private val ToastRed = Color(0xFFEF4444)
private val ToastAmber = Color(0xFFF59E0B)
private val ToastGreen = Color(0xFF22C55E)

private const val WIFI_OFF = "M12 20h.01M8.5 16.429a5 5 0 0 1 7 0M5 12.859a10 10 0 0 1 5.17-2.69M19 12.859a10 10 0 0 0-2.007-1.523M2 8.82a15 15 0 0 1 4.177-2.643M22 8.82a15 15 0 0 0-11.288-3.764M2 2l20 20"
private const val WIFI_ON = "M12 20h.01M2 8.82a15 15 0 0 1 20 0M5 12.859a10 10 0 0 1 14 0M8.5 16.429a5 5 0 0 1 7 0"
private const val ALERT = "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"
private const val SNAIL_CLOCK = "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2"

/**
 * The custom problem toast of the loading screen: a dark card with a coloured edge, a vector icon, a title, one line of help and, for a
 * failed request, a "আবার চেষ্টা" button. Offline / slow / failed stay until it is solved; "internet is back" shows green for a short time.
 */
@Composable
private fun BootToast(boot: ViewBoot, modifier: Modifier) {
    var last by remember { mutableStateOf(boot.issue) }
    if (boot.issue != ViewBoot.Issue.NONE) last = boot.issue
    val issue = if (boot.issue != ViewBoot.Issue.NONE) boot.issue else last

    val accent: Color; val icon: String; val title: String; val sub: String
    when (issue) {
        ViewBoot.Issue.OFFLINE -> { accent = ToastRed; icon = WIFI_OFF; title = "ইন্টারনেট সংযোগ নেই"; sub = "সংযোগ ফিরলে নিজে থেকেই আবার লোড হবে।" }
        ViewBoot.Issue.SLOW -> { accent = ToastAmber; icon = SNAIL_CLOCK; title = "ইন্টারনেট ধীর গতির"; sub = "লোড হতে একটু বেশি সময় লাগছে, অনুগ্রহ করে অপেক্ষা করুন।" }
        ViewBoot.Issue.FAILED -> {
            accent = ToastRed; icon = ALERT; title = "সংযোগ ব্যর্থ হয়েছে"
            sub = boot.reason.ifBlank { "সার্ভারে পৌঁছানো যায়নি। আবার চেষ্টা করুন।" }
        }
        ViewBoot.Issue.BACK -> { accent = ToastGreen; icon = WIFI_ON; title = "ইন্টারনেট ফিরেছে"; sub = "আবার লোড করা হচ্ছে…" }
        ViewBoot.Issue.NONE -> { accent = ToastAmber; icon = SNAIL_CLOCK; title = ""; sub = "" }
    }
    val showRetry = issue == ViewBoot.Issue.FAILED

    AnimatedVisibility(
        visible = boot.issue != ViewBoot.Issue.NONE, modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 3 }
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
                .clip(shape).background(ToastBg).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .semantics { contentDescription = "$title। $sub" }
        ) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(44.dp).background(accent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(20.dp)) {
                        val k = size.width / 24f
                        dsScale(k, k, pivot = Offset.Zero) {
                            drawPath(PathParser().parsePathString(icon).toPath(), accent, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (showRetry) {
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                            .clickable { boot.requestRetry() }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppIcon("refresh", size = 14.dp, tint = Color.White)
                        Text("আবার চেষ্টা", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
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

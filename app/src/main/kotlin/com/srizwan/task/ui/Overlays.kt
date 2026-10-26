package com.srizwan.task.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Auth
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Net
import com.srizwan.task.core.openWhatsApp

/** `#netBar` — no internet / slow net / back online message under the header. */
@Composable
fun NetBar() {
    val bar by Net.bar.collectAsState()
    var last by remember { mutableStateOf<Net.Bar?>(null) }
    if (bar != null) last = bar

    Box(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 66.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(visible = bar != null, enter = fadeIn(), exit = fadeOut()) {
            last?.let { b ->
                val bg = when (b.mode) {
                    Net.Mode.OFF -> Color(0xFFB91C1C)
                    Net.Mode.SLOW -> Color(0xFFB45309)
                    Net.Mode.OK -> Color(0xFF15803D)
                }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.widthIn(max = 520.dp).shadow(8.dp, shape).clip(shape).background(bg)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(b.text, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f, fill = false))
                    if (b.retry) {
                        Box(
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .clickable { Net.retry() }
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Text("আবার চেষ্টা", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** `#toast` — dark pill with a golden left edge, 3.2 s. */
@Composable
fun ToastHost() {
    val msg by AppToast.msg.collectAsState()
    var last by remember { mutableStateOf<AppToast.Msg?>(null) }
    if (msg != null) last = msg
    val gold = goldColor()
    val err = errColor()

    Box(
        Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 84.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(visible = msg != null, enter = fadeIn(), exit = fadeOut()) {
            last?.let { m ->
                Text(
                    m.text,
                    color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (m.bad) err else Color(0xFF111827))
                        .drawBehind { drawRect(gold, size = Size(3.dp.toPx(), size.height)) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}

/** blockedScreen(): full-screen message for a blocked account (WhatsApp the admin / log out). */
@Composable
fun BlockedScreen(u: AppUser) {
    val cs = MaterialTheme.colorScheme
    val ctx: Context = LocalContext.current
    val err = errColor()
    Box(
        Modifier.fillMaxSize().background(cs.background)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(top = 40.dp)) {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(76.dp).clip(CircleShape).background(err), contentAlignment = Alignment.Center) {
                            Text("!", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "আপনার অ্যাকাউন্ট ব্লক করা হয়েছে",
                            color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                        )
                        HintText(
                            u.blockedReason?.takeIf { it.isNotEmpty() } ?: "অ্যাডমিন আপনার অ্যাকাউন্টটি সাময়িকভাবে বন্ধ করেছেন।",
                            center = true
                        )
                        Column(Modifier.padding(top = 14.dp)) {
                            OutlineButton(
                                "হোয়াটসঅ্যাপে অ্যাডমিনের সাথে যোগাযোগ",
                                onClick = {
                                    openWhatsApp(
                                        ctx,
                                        "আসসালামু আলাইকুম, আমার অ্যাকাউন্ট ব্লক দেখাচ্ছে। নাম: " + (u.fullName?.takeIf { it.isNotEmpty() } ?: "-") +
                                            ", ফোন: " + (u.phone?.takeIf { it.isNotEmpty() } ?: "-")
                                    )
                                },
                                color = cs.primary
                            )
                            Box(Modifier.padding(top = 10.dp)) {
                                OutlineButton("লগআউট", onClick = { Auth.logout() }, color = err)
                            }
                        }
                    }
                }
            }
        }
    }
}

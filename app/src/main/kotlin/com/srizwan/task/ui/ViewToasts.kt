package com.srizwan.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ViewToaster
import kotlin.math.roundToInt

private val LtGreen = Color(0xFF4ADE80)

/**
 * React `Toasts.jsx` `#ltoast` — loading toast: spinner + step name + percentage + progress bar along the bottom edge.
 * The plain `#toast` is the app-wide [ToastHost] (see core/ViewToaster.kt), so it is not repeated here.
 * Put it in the View screen's root Box (above the content), like `ToastHost` in MainActivity.
 */
@Composable
fun ViewLoadingToast(toaster: ViewToaster) {
    val s by toaster.loading.collectAsState()
    var last by remember { mutableStateOf(s) }
    if (s.show) last = s
    val gold = goldColor()

    Box(
        Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 64.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = s.show,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            val shape = RoundedCornerShape(12.dp)
            val v = if (s.show) s else last
            Box(
                Modifier.defaultMinSize(minWidth = 260.dp).widthIn(max = 520.dp)
                    .shadow(10.dp, shape).clip(shape)
                    .background(Color(0xF0111827))
                    .drawBehind { drawRect(gold, size = Size(3.dp.toPx(), size.height)) }
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp), strokeWidth = 3.dp,
                        color = LtGreen, trackColor = Color.White.copy(alpha = 0.28f)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(v.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (v.step.isNotEmpty()) Text(v.step, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                    Text(
                        "${v.pct.roundToInt()}%", color = LtGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End, modifier = Modifier.defaultMinSize(minWidth = 46.dp).padding(start = 10.dp)
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth((v.pct / 100f).coerceIn(0f, 1f))
                        .height(3.dp).background(LtGreen)
                )
            }
        }
    }
}

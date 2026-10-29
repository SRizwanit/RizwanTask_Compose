package com.srizwan.task.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.ViewDownload
import com.srizwan.task.core.ViewUtils
import kotlin.math.roundToInt

private val DlGreen = Color(0xFF4ADE80)

/**
 * Custom dialog of the View page's image download button (`downloadCurrent`): download vector icon, "ডাউনলোড হচ্ছে…",
 * the book name, the page number (Bengali digits), and a progress bar with the percentage in Bengali digits + "%".
 * It cannot be dismissed by touch / back while running, and goes away by itself when the download is complete
 * ([ViewDownload.progress] `.show` = false). Vector icons only — no emoji, no XML drawables. Put it in the View screen's root Box.
 */
@Composable
fun ViewDownloadDialog() {
    val s by ViewDownload.progress.collectAsState()
    if (!s.show) return
    val v = s
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    val pct = v.pct.coerceIn(0f, 100f)
    val shape = RoundedCornerShape(18.dp)

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp).widthIn(max = 360.dp).fillMaxWidth()
                .clip(shape).background(cs.surface).border(1.dp, gold.copy(alpha = 0.55f), shape)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // gold-ringed round badge with the download vector icon
            Box(
                Modifier.size(54.dp).border(2.dp, gold, CircleShape).padding(4.dp).clip(CircleShape).background(gold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { AppIcon("download", size = 24.dp, tint = gold) }
            Text(
                "ডাউনলোড হচ্ছে…", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (v.book.isNotEmpty()) Text(
                v.book, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
            )
            if (v.page.isNotEmpty()) Text(
                "পেইজ নম্বর: " + v.page, color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp)
            )
            Spacer(Modifier.height(16.dp))
            // progress bar + percentage (Bengali digits, with %)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.14f))) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(CircleShape).background(DlGreen))
                }
                Text(
                    ViewUtils.bnNum(pct.roundToInt()) + "%", color = DlGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.widthIn(min = 44.dp)
                )
            }
        }
    }
}

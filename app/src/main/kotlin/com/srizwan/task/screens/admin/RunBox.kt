package com.srizwan.task.screens.admin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.openUrl
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.okColor
import com.srizwan.task.ui.warnColor

/**
 * React BooksTab.jsx `RunBox({ s })` — image-creation status box on a book card.
 * Statuses: null (fetching) / "error" / "completed" (success, cancelled, failed) / queued / in_progress.
 * The React check / cross text marks are drawn as vector icons ("check" / "x") — no emoji.
 * Placed on the book card in BooksTab (2-ঝ-২-খ).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunBox(s: RunState?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxWidth().clip(shape).background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)   // React: 1px dashed (Compose border has no dash)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val link: @Composable () -> Unit = {
            val u = s?.url
            if (u != null) Text(
                "বিস্তারিত", color = cs.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { openUrl(ctx, u) }
            )
        }
        val rs: @Composable (String) -> Unit = { t -> Text(t, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp) }
        when {
            s == null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                RunBadge("ছবি তৈরি", ""); rs("স্ট্যাটাস আনা হচ্ছে…")
            }
            s.status == "error" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                RunBadge("ছবি তৈরি", "warn"); rs("স্ট্যাটাস আনা যায়নি: ${s.err}")
            }
            s.status == "completed" -> {
                val ok = s.conclusion == "success"
                val cn = s.conclusion == "cancelled"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RunBadge(
                        if (ok) "ছবি তৈরি সম্পন্ন" else if (cn) "ছবি তৈরি বাতিল" else "ছবি তৈরি ব্যর্থ",
                        if (ok) "ok" else if (cn) "warn" else "bad",
                        icon = if (ok) "check" else if (cn) null else "x"
                    )
                    Box(Modifier.weight(1f))
                    link()
                }
            }
            else -> {
                val q = s.status != "in_progress"
                val pct = if (s.total > 0) maxOf(4, Math.round(s.done.toFloat() / s.total * 100)) else (if (q) 0 else 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RunBadge(if (q) "ছবি তৈরি: সারিতে আছে" else "ছবি তৈরি চলছে…", "warn")
                    if (s.total > 0) Box(Modifier.weight(1f, fill = false)) {
                        rs("ধাপ ${minOf(s.done + 1, s.total)}/${s.total}" + (if (s.step.isNotEmpty()) " • " + s.step else ""))
                    }
                    Box(Modifier.weight(1f))
                    link()
                }
                RunProgress(pct)
            }
        }
    }
}

/** `.badge` with an optional leading vector icon. tone: "" / "ok" / "warn" / "bad". */
@Composable
private fun RunBadge(text: String, tone: String, icon: String? = null) {
    val cs = MaterialTheme.colorScheme
    val c: Color = when (tone) { "ok" -> okColor(); "warn" -> warnColor(); "bad" -> errColor(); else -> cs.onSurfaceVariant }
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) AppIcon(icon, size = 12.dp, tint = c)
        Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** `.pbar.run` — 6dp bar, width animates (.5s), moving light stripe while running. */
@Composable
private fun RunProgress(pct: Int) {
    val cs = MaterialTheme.colorScheme
    val frac by animateFloatAsState(pct.coerceIn(0, 100) / 100f, tween(500), label = "runPct")
    val shift by rememberInfiniteTransition(label = "runShift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "shift"
    )
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceVariant.copy(alpha = 0.7f))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape).background(cs.primary)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.35f), 1f to Color.Transparent,
                        startX = -60f + 120f * shift, endX = 60f + 120f * shift, tileMode = androidx.compose.ui.graphics.TileMode.Repeated
                    )
                )
        )
    }
}

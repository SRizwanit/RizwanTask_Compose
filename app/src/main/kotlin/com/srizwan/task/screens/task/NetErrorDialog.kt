package com.srizwan.task.screens.task

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.AppToast
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.goldColor
import com.srizwan.task.ui.hapticClickable

/**
 * "Check your internet connection" dialog for the Task page: the [NoInternetArt] vector, a title, and two buttons -
 * "Go to settings" (wireless settings, falling back to the main settings) and "Refresh" ([onRefresh]).
 * Back / outside tap do NOT dismiss it: it closes only when the caller stops showing it (after a successful refresh).
 * [onBack] (optional, used by the View page): when given, the back press calls it (leave the page); null = back does nothing (Task page).
 * [busy] = a refresh is running: the refresh button is disabled and shows a waiting label.
 * All icons are vectors (no emoji, no XML). Not placed anywhere yet - step 'ঘ-২-ক' puts it into TaskScreen.
 */
@Composable
fun NetErrorDialog(onRefresh: () -> Unit, busy: Boolean = false, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()

    Dialog(
        onDismissRequest = { onBack?.invoke() },
        properties = DialogProperties(dismissOnBackPress = onBack != null, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(gold, cs.outline, cs.outline, gold))),
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NoInternetArt()
                Spacer(Modifier.height(10.dp))
                Text(
                    "আপনার ইন্টারনেট কানেকশন চেক করুন",
                    color = cs.onSurface, fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                NetAction("সেটিং এ যান", "settings", gold, filled = false, enabled = true) {
                    val wireless = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(wireless)
                    } catch (e: Exception) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e2: Exception) {
                            AppToast.show("সেটিং খোলা যায়নি", true)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                NetAction(
                    if (busy) "রিফ্রেশ হচ্ছে…" else "রিফ্রেশ করুন", "refresh", gold,
                    filled = true, enabled = !busy, onClick = onRefresh
                )
            }
        }
    }
}

/** One full-width button of [NetErrorDialog]: vector icon + label; [filled] = solid gold, otherwise a tinted outline. */
@Composable
private fun NetAction(label: String, icon: String, tone: Color, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fg = if (filled) Color.White else cs.onSurface
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).clip(shape)
            .then(
                if (filled) Modifier.background(tone)
                else Modifier.background(tone.copy(alpha = 0.10f)).border(1.dp, tone.copy(alpha = 0.65f), shape)
            )
            .hapticClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon(icon, size = 18.dp, tint = if (filled) Color.White else tone)
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

package com.srizwan.task.screens.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.openBrowserApp
import com.srizwan.task.core.openUrl
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.ViewIcon
import com.srizwan.task.ui.hapticClickable
import com.srizwan.task.ui.viewColors

/**
 * Shown right after the AI prompt button copied the prompt: "আপনি কি ব্রাউজারে ওপেন করতে চান?".
 *  - হ্যাঁ: opens the AI chat ([AI_CHAT_URL]) in the browser;
 *  - ব্রাউজার ওপেন করুন: opens only the phone's browser app (no link, no text);
 *  - না: just closes the dialog;
 *  - আর দেখাবে না: closes it and, from now on, the button only copies.
 * Same card look as the other View dialogs; every icon is a vector.
 */
@Composable
fun ViewAiOpenDialog(st: ViewPageState) {
    if (!st.aiAsk) return
    val ctx = LocalContext.current
    val c = viewColors()
    Dialog(onDismissRequest = { st.aiAskDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.pri.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                ViewIcon("sparkle", size = 26.dp, tint = c.pri)
            }
            Spacer(Modifier.height(12.dp))
            Text("প্রম্পট কপি করা হয়েছে।", color = c.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "আপনি কি ব্রাউজারে ওপেন করতে চান?",
                color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            AiAction("হ্যাঁ (ব্রাউজারে ওপেন হবে)", { AppIcon("send", size = 18.dp, tint = c.priFg) }, filled = true) {
                st.aiAskDismiss()
                openUrl(ctx, AI_CHAT_URL)
            }
            Spacer(Modifier.height(8.dp))
            // only the browser app, no link / no text
            AiAction("ব্রাউজার ওপেন করুন", { ViewIcon("globe", size = 18.dp, tint = c.fg) }, filled = false) {
                st.aiAskDismiss()
                openBrowserApp(ctx)
            }
            Spacer(Modifier.height(8.dp))
            AiAction("না (ডায়লগ dismiss)", { AppIcon("x", size = 18.dp, tint = c.fg) }, filled = false) { st.aiAskDismiss() }
            Spacer(Modifier.height(8.dp))
            AiAction("আর দেখাবে না (ক্লিক করলে শুধুই কপি হবে)", { AppIcon("ban", size = 18.dp, tint = c.muted) }, filled = false, muted = true) {
                st.aiAskNever()
            }
        }
    }
}

@Composable
private fun AiAction(label: String, icon: @Composable () -> Unit, filled: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    val c = viewColors()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (filled) c.pri else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(BorderStroke(1.dp, c.line), shape))
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon()
        Text(
            label, color = if (filled) c.priFg else if (muted) c.muted else c.fg, fontSize = 14.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

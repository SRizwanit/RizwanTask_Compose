package com.srizwan.task.screens.task

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.openUrl
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.AppLogo
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.goldColor

/** Play Store page of the app — used by the rate and share buttons of [ExitDialog]. */
private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.srizwan.task"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Custom "exit the app?" dialog shown when back is pressed on the Task page.
 * Greeting, question, a rate button (Play Store), a share button (system share sheet with the Play Store link) and the
 * exit button. Back / outside tap / the close button only dismiss it. All icons are vectors.
 */
@Composable
fun ExitDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    val err = errColor()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(gold, cs.outline, cs.outline, gold))),
            shadowElevation = 12.dp
        ) {
            Box {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // logo mark on a soft gold disc
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(gold.copy(alpha = 0.22f), gold.copy(alpha = 0.06f))))
                            .border(1.dp, gold.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { AppLogo(size = 40.dp) }

                    Spacer(Modifier.height(14.dp))
                    Text("আসসালামু আলাইকুম", color = gold, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "আপনি কি অ্যাপ থেকে বের হতে চান?",
                        color = cs.onSurface, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))

                    ExitAction("রেটিং দিন", "star", gold, filled = false) { openUrl(ctx, PLAY_URL) }
                    Spacer(Modifier.height(10.dp))
                    ExitAction("শেয়ার করুন", "share", gold, filled = false) {
                        try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "RizwanTask অ্যাপটি ব্যবহার করে দেখুন:\n$PLAY_URL")
                            }
                            ctx.startActivity(Intent.createChooser(send, "শেয়ার করুন").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            AppToast.show("শেয়ার করা যায়নি", true)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ExitAction("হ্যাঁ, বের হতে চাই।", "logout", err, filled = true) {
                        onDismiss()
                        ctx.findActivity()?.finish()
                    }
                }
                // close (stay in the app)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { AppIcon("x", size = 18.dp, tint = cs.onSurfaceVariant) }
            }
        }
    }
}

/** One full-width button of [ExitDialog]: vector icon + label; [filled] = solid [tone], otherwise a tinted outline. */
@Composable
private fun ExitAction(label: String, icon: String, tone: Color, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fg = if (filled) Color.White else cs.onSurface
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .then(
                if (filled) Modifier.background(tone)
                else Modifier.background(tone.copy(alpha = 0.10f)).border(1.dp, tone.copy(alpha = 0.65f), shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon(icon, size = 18.dp, tint = if (filled) Color.White else tone)
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

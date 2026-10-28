package com.srizwan.task

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.CrashHandler
import com.srizwan.task.ui.AppTypography
import com.srizwan.task.ui.appIconVector
import com.srizwan.task.ui.viewIconVector

private val CrBg = Color(0xFF0E1712)
private val CrCard = Color(0xFF16231B)
private val CrLine = Color(0xFF24362B)
private val CrText = Color(0xFFF3FFF9)
private val CrMuted = Color(0xFF9DB5A6)
private val CrGold = Color(0xFFF2D27A)
private val CrDanger = Color(0xFFFF6B6B)
private val CrWa = Color(0xFF25D366)

/** Triangle with an exclamation mark (vector, 24 viewBox, 2px round stroke). */
private val CrAlertIcon: ImageVector by lazy {
    ImageVector.Builder("crash-alert", 24.dp, 24.dp, 24f, 24f).apply {
        listOf(
            "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
            "M12 9v4",
            "M12 17h.01"
        ).forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

/**
 * Debug screen shown when the app crashes. Runs in its own ":crash" process (see manifest), so it does not use any
 * app service (Prefs, Theme, Nav ...) - only the saved report from [CrashHandler.read].
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        val data = CrashHandler.read(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CrGold, background = CrBg, surface = CrCard, onSurface = CrText, onBackground = CrText
                ),
                typography = AppTypography
            ) {
                CrashScreen(
                    data = data,
                    onSend = { if (data == null) false else CrashHandler.sendToWhatsApp(this, data.short) },
                    onCopy = { data != null && copy(data.full) },
                    onRestart = { restart() }
                )
            }
        }
    }

    private fun copy(text: String): Boolean = try {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", text))
        true
    } catch (_: Throwable) {
        false
    }

    private fun restart() {
        try {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        } catch (_: Throwable) {
            // ignore
        }
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        // leave no empty ":crash" process behind
        if (isFinishing) Process.killProcess(Process.myPid())
    }
}

@Composable
private fun CrashScreen(
    data: CrashHandler.Data?,
    onSend: () -> Boolean,
    onCopy: () -> Boolean,
    onRestart: () -> Unit
) {
    var note by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(CrBg).systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(CrDanger.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(CrAlertIcon, null, Modifier.size(28.dp), tint = CrDanger) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("অ্যাপ ক্র্যাশ করেছে", color = CrText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "রিপোর্টটি ডেভেলপারকে পাঠালে দ্রুত ঠিক করা যাবে।",
                        color = CrMuted, fontSize = 13.sp
                    )
                }
            }

            if (data == null) {
                CrashCard { Text("কোনো রিপোর্ট পাওয়া যায়নি।", color = CrMuted, fontSize = 14.sp) }
            } else {
                CrashCard {
                    data.meta.forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(k, color = CrMuted, fontSize = 13.sp, modifier = Modifier.width(76.dp))
                            Text(v, color = CrText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
                CrashCard(border = CrDanger.copy(alpha = 0.45f)) {
                    Text("Error", color = CrDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer { Text(data.headline, color = CrText, fontSize = 14.sp) }
                }
                CrashCard {
                    Text("Stack trace", color = CrGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                data.trace.take(12000),
                                color = CrText, fontSize = 11.sp, lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)) {
            note?.let {
                Text(it, color = CrGold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = {
                    note = if (onSend()) null else {
                        onCopy()
                        "WhatsApp খোলা যায়নি, রিপোর্ট কপি করা হয়েছে।"
                    }
                },
                enabled = data != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrWa, contentColor = Color(0xFF062E17),
                    disabledContainerColor = CrLine, disabledContentColor = CrMuted
                )
            ) {
                Icon(appIconVector("whatsapp"), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("WhatsApp-এ রিপোর্ট পাঠান", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { note = if (onCopy()) "রিপোর্ট কপি হয়েছে।" else "কপি করা যায়নি।" },
                    enabled = data != null,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrLine)
                ) {
                    Icon(viewIconVector("copy"), null, Modifier.size(18.dp), tint = CrText)
                    Spacer(Modifier.width(6.dp))
                    Text("কপি", color = CrText, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrLine)
                ) {
                    Icon(viewIconVector("refresh"), null, Modifier.size(18.dp), tint = CrText)
                    Spacer(Modifier.width(6.dp))
                    Text("আবার চালু", color = CrText, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun CrashCard(border: Color = CrLine, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CrCard)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) { content() }
}

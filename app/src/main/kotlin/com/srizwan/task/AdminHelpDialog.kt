package com.srizwan.task

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.ceil

private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Custom dialog (with a vector lock + chat illustration) that tells the user
 * to contact the admin on WhatsApp to reset the PIN / free the device.
 */
@Composable
fun AdminHelpDialog(
    state: HelpDialogState,
    userPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val (title, body) = when (state.reason) {
        HelpReason.WrongPin -> {
            val left = state.extra
            "পিন ভুল হয়েছে" to buildString {
                append("আপনার দেওয়া পিনটি সঠিক নয়।")
                if (left > 0) append(" আর $left বার চেষ্টা করতে পারবেন।")
                append("\n\nপিন ভুলে গেলে রিসেট করতে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।")
            }
        }
        HelpReason.ForgotPin ->
            "পিন ভুলে গেছেন?" to
                "পিন রিসেট করতে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।"
        HelpReason.Locked -> {
            val minutes = ceil(state.extra / 60f).toInt().coerceAtLeast(1)
            "একাউন্ট সাময়িক লক" to
                "অনেকবার ভুল পিন দেওয়া হয়েছে। $minutes মিনিট পরে আবার চেষ্টা করুন।\n\n" +
                "এখনই পিন রিসেট করতে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।"
        }
        HelpReason.Blocked ->
            "একাউন্ট বন্ধ আছে" to
                "আপনার একাউন্টটি বন্ধ করা আছে। বিস্তারিত জানতে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।"
        HelpReason.DeviceMismatch ->
            "অন্য মোবাইলের সাথে যুক্ত" to
                "এই একাউন্টটি অন্য একটি মোবাইলের সাথে যুক্ত আছে। মোবাইল পরিবর্তন করতে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।"
        HelpReason.DeviceInUse ->
            "এই মোবাইলে একাউন্ট আছে" to
                "একটি মোবাইলে একটির বেশি একাউন্ট খোলা যায় না। সমস্যা হলে অ্যাডমিনের সাথে WhatsApp-এ যোগাযোগ করুন।"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LockChatIllustration(modifier = Modifier.size(116.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    color = colors.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = body,
                    color = colors.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "অ্যাডমিন: ${AppConfig.adminPhone}",
                    color = colors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        openAdminWhatsApp(context, userPhone, state.reason)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "WhatsApp-এ যোগাযোগ করুন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("বন্ধ করুন")
                }
            }
        }
    }
}

private fun openAdminWhatsApp(context: Context, userPhone: String, reason: HelpReason) {
    val phoneText = if (userPhone.isNotBlank()) " আমার মোবাইল নাম্বার: $userPhone" else ""
    val request = when (reason) {
        HelpReason.DeviceMismatch, HelpReason.DeviceInUse ->
            "আসসালামু আলাইকুম, আমার ডিভাইস সংক্রান্ত সমস্যা হচ্ছে।"
        HelpReason.Blocked -> "আসসালামু আলাইকুম, আমার একাউন্ট বন্ধ দেখাচ্ছে।"
        else -> "আসসালামু আলাইকুম, আমার পিন রিসেট করতে চাই।"
    }
    val url = AppConfig.whatsappUrl(request + phoneText)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "WhatsApp বা ব্রাউজার পাওয়া যায়নি।", Toast.LENGTH_LONG).show()
    }
}

/** Vector illustration: lock + WhatsApp-style chat bubble, drawn with Canvas. */
@Composable
private fun LockChatIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val soft = colors.primary.copy(alpha = 0.14f)
    val hole = colors.surface

    val infinite = rememberInfiniteTransition(label = "help_illustration")
    val pulse = infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        }
    ) {
        val s = size.minDimension

        // soft round background
        drawCircle(color = soft, radius = s * 0.48f, center = Offset(s / 2f, s / 2f))

        // lock shackle (upper half circle + two legs)
        val shackleStroke = Stroke(width = s * 0.058f, cap = StrokeCap.Round)
        drawArc(
            color = primary,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(s * 0.5f - s * 0.13f, s * 0.20f),
            size = Size(s * 0.26f, s * 0.26f),
            style = shackleStroke
        )
        drawLine(
            color = primary,
            start = Offset(s * 0.5f - s * 0.13f, s * 0.33f),
            end = Offset(s * 0.5f - s * 0.13f, s * 0.46f),
            strokeWidth = s * 0.058f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = primary,
            start = Offset(s * 0.5f + s * 0.13f, s * 0.33f),
            end = Offset(s * 0.5f + s * 0.13f, s * 0.46f),
            strokeWidth = s * 0.058f,
            cap = StrokeCap.Round
        )

        // lock body
        drawRoundRect(
            color = primary,
            topLeft = Offset(s * 0.5f - s * 0.21f, s * 0.45f),
            size = Size(s * 0.42f, s * 0.31f),
            cornerRadius = CornerRadius(s * 0.07f)
        )

        // keyhole
        drawCircle(color = hole, radius = s * 0.04f, center = Offset(s * 0.5f, s * 0.58f))
        drawRoundRect(
            color = hole,
            topLeft = Offset(s * 0.5f - s * 0.012f, s * 0.58f),
            size = Size(s * 0.024f, s * 0.09f),
            cornerRadius = CornerRadius(s * 0.01f)
        )

        // chat bubble (bottom right)
        drawRoundRect(
            color = WhatsAppGreen,
            topLeft = Offset(s * 0.58f, s * 0.60f),
            size = Size(s * 0.34f, s * 0.24f),
            cornerRadius = CornerRadius(s * 0.07f)
        )
        val tail = Path().apply {
            moveTo(s * 0.65f, s * 0.83f)
            lineTo(s * 0.62f, s * 0.94f)
            lineTo(s * 0.75f, s * 0.83f)
            close()
        }
        drawPath(path = tail, color = WhatsAppGreen)

        // three dots inside the bubble
        val dotY = s * 0.72f
        listOf(0.67f, 0.75f, 0.83f).forEach { x ->
            drawCircle(color = Color.White, radius = s * 0.022f, center = Offset(s * x, dotY))
        }
    }
}

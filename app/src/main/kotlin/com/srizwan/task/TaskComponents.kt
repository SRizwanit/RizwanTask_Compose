package com.srizwan.task

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/*
 * ---------------------------------------------------------------------------
 *  Small shared pieces for the Task / Profile / Admin pages.
 *  NOTE: no material3.Text import here - the package Text (AppFont.kt) puts the
 *  right font (Bangla / English / Arabic) on every text.
 * ---------------------------------------------------------------------------
 */

val GoldColor = Color(0xFFC8962E)
val WarnColor = Color(0xFFB45309)
val OkColor = Color(0xFF2E7D32)

/** Calm static day / night gradient (the animated garden is only used on Splash / Login / Signup to save battery). */
@Composable
fun TaskBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = LocalAppDark.current
    val gradient = remember(dark) {
        Brush.verticalGradient(
            colors = if (dark) {
                listOf(Color(0xFF020B07), Color(0xFF052418), Color(0xFF0A3A27), Color(0xFF031109))
            } else {
                listOf(Color(0xFFF4FCEF), Color(0xFFDDF3D4), Color(0xFFBFE5BC))
            }
        )
    }
    Box(modifier = Modifier.fillMaxSize().background(gradient), content = content)
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

/** approved (green) / pending (amber) / correction (red) in one bar */
@Composable
fun SegBar(approved: Int, pending: Int, correction: Int, total: Int, modifier: Modifier = Modifier) {
    val t = maxOf(total, approved + pending + correction, 1).toFloat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (approved > 0) Box(Modifier.weight(approved / t).fillMaxHeight().background(OkColor))
        if (pending > 0) Box(Modifier.weight(pending / t).fillMaxHeight().background(WarnColor))
        if (correction > 0) Box(Modifier.weight(correction / t).fillMaxHeight().background(MaterialTheme.colorScheme.error))
        val rest = t - approved - pending - correction
        if (rest > 0.001f) Box(Modifier.weight(rest / t).fillMaxHeight())
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** "label ........ value" line */
@Composable
fun KvRow(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        modifier = modifier
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 8.dp)
    )
}

/** Grey pulsing block for loading states. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val a by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton_alpha"
    )
    Box(
        modifier = modifier
            .alpha(a)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/** Rounded dialog frame used by all Task dialogs (same look as AdminHelpDialog). */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

/** Short message at the bottom of the screen (like the web toast). Disappears after ~3 s. */
@Composable
fun ToastHost(toast: ToastMsg?, onDone: (Long) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(3_200)
            onDone(toast.id)
        }
    }
    if (toast != null) {
        Surface(
            modifier = modifier.padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (toast.bad) MaterialTheme.colorScheme.error else Color(0xFF173024),
            shadowElevation = 8.dp
        ) {
            Text(
                text = toast.text,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}

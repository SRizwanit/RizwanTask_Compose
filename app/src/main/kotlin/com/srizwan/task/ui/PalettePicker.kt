package com.srizwan.task.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.LocalAppDark
import com.srizwan.task.appColorScheme
import com.srizwan.task.appGradientColors
import com.srizwan.task.core.AppPalette

/**
 * Profile page: pick the design theme (garden / ocean / twilight). Every card previews the real colours of that theme
 * in the current day / night mode. Day / night itself stays a separate choice (ThemeStore.mode).
 */
@Composable
fun PalettePicker(selected: AppPalette, onSelect: (AppPalette) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalAppDark.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppPalette.entries.forEach { p ->
            val on = p == selected
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(cs.surface.copy(alpha = 0.8f))
                    .background(cs.surfaceVariant.copy(alpha = 0.45f))
                    .border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outline.copy(alpha = 0.6f), shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(p) })
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PalettePreview(p, dark, Modifier.fillMaxWidth().height(66.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (on) AppIcon("check", size = 13.dp, tint = cs.primary)
                    Text(p.label, color = if (on) cs.primary else cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    p.desc, color = cs.onSurfaceVariant, fontSize = 10.5.sp, lineHeight = 14.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/** A tiny still of a theme: its background gradient, a few motifs and a button in its colours. */
@Composable
private fun PalettePreview(p: AppPalette, dark: Boolean, modifier: Modifier) {
    val scheme = appColorScheme(p, dark)
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.clip(shape).background(Brush.verticalGradient(appGradientColors(p, dark)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (p) {
                AppPalette.GARDEN -> {
                    val petal = if (dark) Color(0xFF4CC074) else Color(0xFF43A047)
                    listOf(0.18f to 0.28f, 0.78f to 0.22f, 0.50f to 0.52f, 0.88f to 0.62f).forEachIndexed { i, (x, y) ->
                        val r = (5 + (i % 2) * 2).dp.toPx()
                        val c = Offset(w * x, h * y)
                        for (k in 0 until 5) {
                            val a = Math.toRadians(k * 72.0)
                            drawCircle(petal.copy(alpha = 0.85f), r * 0.55f, Offset(c.x + (Math.cos(a) * r * 0.7f).toFloat(), c.y + (Math.sin(a) * r * 0.7f).toFloat()))
                        }
                        drawCircle(Color(0xFFFFE57F), r * 0.4f, c)
                    }
                }
                AppPalette.OCEAN -> {
                    val ring = if (dark) Color(0xFF4FC3F7) else Color(0xFF1E88C8)
                    listOf(Triple(0.2f, 0.3f, 6f), Triple(0.62f, 0.2f, 4f), Triple(0.82f, 0.5f, 7f), Triple(0.42f, 0.55f, 3.5f)).forEach { (x, y, r) ->
                        drawCircle(ring.copy(alpha = 0.75f), r.dp.toPx(), Offset(w * x, h * y), style = Stroke(1.dp.toPx()))
                        drawCircle(Color.White.copy(alpha = 0.85f), r.dp.toPx() * 0.22f, Offset(w * x - r.dp.toPx() * 0.35f, h * y - r.dp.toPx() * 0.35f))
                    }
                    drawRect(ring.copy(alpha = 0.18f), topLeft = Offset(0f, h * 0.86f), size = Size(w, h * 0.14f))
                }
                AppPalette.TWILIGHT -> {
                    val star = if (dark) Color(0xFFF3EAFF) else Color(0xFF8E5CD6)
                    listOf(0.15f to 0.2f, 0.4f to 0.45f, 0.62f to 0.15f, 0.85f to 0.38f, 0.28f to 0.65f, 0.72f to 0.62f).forEachIndexed { i, (x, y) ->
                        val r = (1.2f + (i % 3) * 0.7f).dp.toPx()
                        drawCircle(star.copy(alpha = 0.9f), r, Offset(w * x, h * y))
                        if (i % 2 == 0) {
                            drawLine(star.copy(alpha = 0.8f), Offset(w * x - r * 3, h * y), Offset(w * x + r * 3, h * y), strokeWidth = 0.8.dp.toPx())
                            drawLine(star.copy(alpha = 0.8f), Offset(w * x, h * y - r * 3), Offset(w * x, h * y + r * 3), strokeWidth = 0.8.dp.toPx())
                        }
                    }
                }
            }
            // the theme's button
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary)),
                topLeft = Offset(w * 0.22f, h * 0.76f), size = Size(w * 0.56f, h * 0.16f),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

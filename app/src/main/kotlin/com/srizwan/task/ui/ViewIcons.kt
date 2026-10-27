package com.srizwan.task.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * React `view/Sprite.jsx` (the `<symbol id="i-...">` icon sprite of the View page) as vector paths: viewBox 24, stroke 2, round caps/joins,
 * no fill (CSS `.i`), tinted by `tint` (= `currentColor`). circle / rect / line / polyline / polygon are converted to path data
 * (generated from Sprite.jsx; names are the part after "i-"). Vector only - no emoji, no bitmap.
 */
private val VIEW_ICON_PATHS: Map<String, List<String>> = mapOf(
    "settings" to listOf("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"),
    "refresh" to listOf("M23 4L23 10L17 10", "M1 20L1 14L7 14", "M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"),
    "left" to listOf("M15 18L9 12L15 6"),
    "right" to listOf("M9 18L15 12L9 6"),
    "down" to listOf("M6 9L12 15L18 9"),
    "check" to listOf("M20 6L9 17L4 12"),
    "skip" to listOf("M5 4L15 12L5 20L5 4z", "M19 5L19 19"),
    "swap" to listOf("M17 1L21 5L17 9", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23L3 19L7 15", "M21 13v2a4 4 0 0 1-4 4H3"),
    "tag" to listOf("M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z", "M7 7L7.01 7"),
    "download" to listOf("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10L12 15L17 10", "M12 15L12 3"),
    "copy" to listOf("M11 9H20a2 2 0 0 1 2 2V20a2 2 0 0 1 -2 2H11a2 2 0 0 1 -2 -2V11a2 2 0 0 1 2 -2z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"),
    "paste" to listOf("M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M9 2H15a1 1 0 0 1 1 1V5a1 1 0 0 1 -1 1H9a1 1 0 0 1 -1 -1V3a1 1 0 0 1 1 -1z"),
    "save" to listOf("M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z", "M17 21L17 13L7 13L7 21", "M7 3L7 8L15 8"),
    "checkc" to listOf("M22 11.08V12a10 10 0 1 1-5.93-9.14", "M22 4L12 14.01L9 11.01"),
    "square" to listOf("M5 3H19a2 2 0 0 1 2 2V19a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z"),
    "checksq" to listOf("M9 11L12 14L22 4", "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"),
    "globe" to listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M2 12H22", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"),
    "trash" to listOf("M3 6L5 6L21 6", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
    "play" to listOf("M6 3L20 12L6 21L6 3z"),
    "pause" to listOf("M7 4H9a1 1 0 0 1 1 1V19a1 1 0 0 1 -1 1H7a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z", "M15 4H17a1 1 0 0 1 1 1V19a1 1 0 0 1 -1 1H15a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z"),
    "stop" to listOf("M7 5H17a2 2 0 0 1 2 2V17a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z"),
    "api" to listOf("M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1", "M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1"),
    "bolt" to listOf("M13 2L3 14L12 14L11 22L21 10L12 10L13 2z"),
    "lens" to listOf("M3 8V5a2 2 0 0 1 2-2h3M16 3h3a2 2 0 0 1 2 2v3M21 16v3a2 2 0 0 1-2 2h-3M8 21H5a2 2 0 0 1-2-2v-3", "M8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0"),
    "minus" to listOf("M5 12h14"),
    "plus" to listOf("M12 5v14M5 12h14"),
    "fit" to listOf("M4 9V5h4M20 9V5h-4M4 15v4h4M20 15v4h-4"),
    "fontDown" to listOf("M3 18L8 6l5 12M5 14h6", "M16 12h5"),
    "fontUp" to listOf("M3 18L8 6l5 12M5 14h6", "M18.5 9.5v5M16 12h5"),
    "sparkle" to listOf("M10 3l1.9 5.6L17.5 10.5l-5.6 1.9L10 18l-1.9-5.6L2.5 10.5l5.6-1.9z", "M19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9z")
)

private val viewIconCache = HashMap<String, ImageVector>()

/** Vector for a sprite icon name (e.g. "left", "download"); empty vector when the name is unknown. */
fun viewIconVector(name: String): ImageVector = viewIconCache.getOrPut(name) {
    ImageVector.Builder(name = "i-$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        (VIEW_ICON_PATHS[name] ?: emptyList()).forEach { d ->
            addPath(
                pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

/** React `<Ic n="left" />` (`.i` = 16px; toolbar buttons use 14-15px). */
@Composable
fun ViewIcon(n: String, modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = LocalContentColor.current) {
    Icon(imageVector = viewIconVector(n), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

val VIEW_ICON_NAMES: Set<String> get() = VIEW_ICON_PATHS.keys

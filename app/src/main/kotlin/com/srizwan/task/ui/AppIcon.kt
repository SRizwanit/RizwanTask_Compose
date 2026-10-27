package com.srizwan.task.ui

import androidx.compose.foundation.Image
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
import com.srizwan.task.LocalAppDark

private val cache = HashMap<String, ImageVector>()

/** Same icon set as the React app (common.js ICONS): 24x24, 2px round stroke, tinted by `tint`. */
fun appIconVector(name: String): ImageVector = cache.getOrPut(name) {
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        (ICON_PATHS[name] ?: emptyList()).forEach { p ->
            addPath(
                pathData = addPathNodes(p.d),
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

// Night colours (light gold / pearl on a dark or green background)
private val Gold = Color(0xFFF2D27A)
private val Pearl = Color(0xFFF3FFF9)
private val Sparkle = Color(0xFFFFF7D6)

// Day colours: same drawing, but deep gold + deep green so it stays clear on the light day background
private val GoldDay = Color(0xFF9A6A0A)
private val PearlDay = Color(0xFF1B5E20)
private val SparkleDay = Color(0xFFD9981A)

private fun buildMark(name: String, gold: Color, pearl: Color, sparkle: Color): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 64.dp, defaultHeight = 64.dp, viewportWidth = 64f, viewportHeight = 64f
    ).apply {
        fun stroke(d: String, c: Color, w: Float) = addPath(
            pathData = addPathNodes(d), stroke = SolidColor(c), strokeLineWidth = w,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        stroke("M9 61V35a23 23 0 0 1 46 0V61M4 61H60", gold, 2f)
        // rosette (centre 32,25): a dot + 4 petals rotated 0/45/90/135 degrees
        stroke("M30.6 25a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0", gold, 1.2f)
        for (deg in listOf(0f, 45f, 90f, 135f)) {
            addGroup(rotate = deg, pivotX = 32f, pivotY = 25f)
            stroke("M30.3 25a1.7 5 0 1 0 3.4 0a1.7 5 0 1 0 -3.4 0", gold, 1.2f)
            clearGroup()
        }
        stroke(
            "M32 52V38M32 38C28 35.5 22 35.5 17 37V51C22 49.5 28 49.5 32 52M32 38C36 35.5 42 35.5 47 37V51C42 49.5 36 49.5 32 52",
            pearl, 2.2f
        )
        stroke("M19 57C25 56.5 29 55 32 52C35 55 39 56.5 45 57", gold, 1.8f)
        stroke("M36.5 44l2.8 2.8 4.6-5.8", gold, 2.2f)
        addPath(pathData = addPathNodes("M35 1.4A5.5 5.5 0 1 0 35 10.6A4.6 4.6 0 1 1 35 1.4z"), fill = SolidColor(gold))
        addPath(
            pathData = addPathNodes("M40.50 3.30 L41.18 5.07 L43.07 5.17 L41.59 6.36 L42.09 8.18 L40.50 7.15 L38.91 8.18 L39.41 6.36 L37.93 5.17 L39.82 5.07z"),
            fill = SolidColor(sparkle)
        )
    }.build()

/** The RizwanTask logo mark (dome arch + rosette + open book + tick + crescent), LOGO in common.js — night colours (for dark / green tiles). */
val AppMark: ImageVector by lazy { buildMark("mark", Gold, Pearl, Sparkle) }

/** The same logo in day colours (deep gold + deep green) — clearly visible on the light day background. */
val AppMarkDay: ImageVector by lazy { buildMark("markDay", GoldDay, PearlDay, SparkleDay) }

/** The logo for a plain screen background: night look at night, day colours in day mode (matches the background either way). */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Image(
        imageVector = if (LocalAppDark.current) AppMark else AppMarkDay,
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

@Composable
fun AppIcon(name: String, modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = LocalContentColor.current) {
    if (name == "mark") {
        Image(imageVector = AppMark, contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(imageVector = appIconVector(name), contentDescription = null, modifier = modifier.size(size), tint = tint)
    }
}

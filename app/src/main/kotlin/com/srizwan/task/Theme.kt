package com.srizwan.task

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.srizwan.task.core.AppPalette
import com.srizwan.task.core.MotionLevel
import com.srizwan.task.core.MotionStore
import com.srizwan.task.core.ThemeMode
import com.srizwan.task.core.ThemeStore
import com.srizwan.task.ui.AppTypography

/*
 * ---------------------------------------------------------------------------
 *  Theme.kt  -  one place for the whole app look
 * ---------------------------------------------------------------------------
 *
 *  1) Wrap the app once:
 *
 *       setContent { RizwanTaskTheme { AppRoot() } }
 *
 *  2) Put the animated background behind ANY screen:
 *
 *       @Composable
 *       fun MyScreen() {
 *           RizwanBackground(modifier = Modifier.fillMaxSize()) {
 *               // your screen content here (Column, LazyColumn, ...)
 *           }
 *       }
 *
 *     Options:
 *       scrim      = true   -> soft calm area in the middle (good for logo / text)
 *       fireflies  = false  -> turn the fireflies off
 *       flowers    = false  -> turn the small flowers off
 *       darkTheme  = ...    -> force night or day (default: follows the app theme = system / light / dark)
 * ---------------------------------------------------------------------------
 */

// ---------------------------------------------------------------------------
// Material colors (day / night)
// ---------------------------------------------------------------------------
private val GardenLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    background = Color(0xFFEFFAEA),
    onBackground = Color(0xFF14301A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14301A),
    surfaceVariant = Color(0xFFDCEDC8),
    onSurfaceVariant = Color(0xFF3B5B40),
    outline = Color(0xFF7BA67F)
)

private val GardenDark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF06220C),
    secondary = Color(0xFF4CAF50),
    background = Color(0xFF031009),
    onBackground = Color(0xFFE2F3E3),
    surface = Color(0xFF0C1D14),
    onSurface = Color(0xFFE2F3E3),
    surfaceVariant = Color(0xFF173024),
    onSurfaceVariant = Color(0xFFB5D3B8),
    outline = Color(0xFF4F7A56)
)

// ---- Ocean (sagor): blue water, bubbles ----
private val OceanLight = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    secondary = Color(0xFF039BE5),
    background = Color(0xFFEAF6FC),
    onBackground = Color(0xFF0B2A3C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B2A3C),
    surfaceVariant = Color(0xFFD2EAF7),
    onSurfaceVariant = Color(0xFF38596D),
    outline = Color(0xFF7DB0CB)
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF032434),
    secondary = Color(0xFF0288D1),
    background = Color(0xFF030E18),
    onBackground = Color(0xFFE0F2FB),
    surface = Color(0xFF0A1B2A),
    onSurface = Color(0xFFE0F2FB),
    surfaceVariant = Color(0xFF123045),
    onSurfaceVariant = Color(0xFFB0D2E4),
    outline = Color(0xFF3F7794)
)

// ---- Twilight (godhuli): purple sky, stars ----
private val TwilightLight = lightColorScheme(
    primary = Color(0xFF6A3FB5),
    onPrimary = Color.White,
    secondary = Color(0xFF9566D6),
    background = Color(0xFFF6EFFC),
    onBackground = Color(0xFF2A1745),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A1745),
    surfaceVariant = Color(0xFFE8DCF6),
    onSurfaceVariant = Color(0xFF58427A),
    outline = Color(0xFFA68BC9)
)

private val TwilightDark = darkColorScheme(
    primary = Color(0xFFB794F6),
    onPrimary = Color(0xFF1E0F3A),
    secondary = Color(0xFF7E57C2),
    background = Color(0xFF0B0716),
    onBackground = Color(0xFFEDE4FA),
    surface = Color(0xFF161026),
    onSurface = Color(0xFFEDE4FA),
    surfaceVariant = Color(0xFF2A1F45),
    onSurfaceVariant = Color(0xFFC9B8E6),
    outline = Color(0xFF6B54A0)
)

/** Material colours of a design theme in day / night (also used by the theme picker previews). */
fun appColorScheme(p: AppPalette, dark: Boolean): ColorScheme = when (p) {
    AppPalette.GARDEN -> if (dark) GardenDark else GardenLight
    AppPalette.OCEAN -> if (dark) OceanDark else OceanLight
    AppPalette.TWILIGHT -> if (dark) TwilightDark else TwilightLight
}

/** Top-to-bottom background gradient colours of a design theme in day / night. */
fun appGradientColors(p: AppPalette, dark: Boolean): List<Color> = when (p) {
    AppPalette.GARDEN -> if (dark) {
        listOf(Color(0xFF020B07), Color(0xFF052418), Color(0xFF0A3A27), Color(0xFF031109))
    } else {
        listOf(Color(0xFFF4FCEF), Color(0xFFDDF3D4), Color(0xFFBFE5BC))
    }
    AppPalette.OCEAN -> if (dark) {
        listOf(Color(0xFF020912), Color(0xFF05203A), Color(0xFF083A5E), Color(0xFF031322))
    } else {
        listOf(Color(0xFFF2FAFF), Color(0xFFD6EEFA), Color(0xFFB2DCF2))
    }
    AppPalette.TWILIGHT -> if (dark) {
        listOf(Color(0xFF07040F), Color(0xFF150C2E), Color(0xFF2A1850), Color(0xFF0B0617))
    } else {
        listOf(Color(0xFFFAF3FF), Color(0xFFEADDFA), Color(0xFFDCC6F2), Color(0xFFF0CDE6))
    }
}

/** The design theme that is active right now (see ThemeStore.palette). */
val LocalAppPalette = compositionLocalOf { AppPalette.GARDEN }

/** true when the app is currently in night mode (system / forced light / forced dark, see ThemeStore). */
val LocalAppDark = compositionLocalOf { false }

@Composable
fun rememberAppDark(): Boolean {
    val system = isSystemInDarkTheme()
    return when (ThemeStore.mode) {
        ThemeMode.AUTO -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** Length of the colour fade when the theme changes (day / night / system, or another design theme). */
const val THEME_FADE_MS = 450

@Composable
fun RizwanTaskTheme(
    darkTheme: Boolean = rememberAppDark(),
    palette: AppPalette = ThemeStore.palette,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppDark provides darkTheme, LocalAppPalette provides palette) {
        MaterialTheme(
            // the colours glide to the new theme instead of jumping
            colorScheme = rememberFadedColorScheme(appColorScheme(palette, darkTheme)),
            typography = AppTypography,
            content = content
        )
    }
}

/**
 * The colour scheme of [target], faded in over [THEME_FADE_MS] whenever [target] changes (the first composition shows
 * it at once). A change in the middle of a fade starts from the colours that are on screen at that moment.
 */
@Composable
private fun rememberFadedColorScheme(target: ColorScheme): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = if (progress.value >= 1f) to else lerpScheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(THEME_FADE_MS, easing = FastOutSlowInEasing))
    }
    val f = progress.value
    return if (f >= 1f) to else lerpScheme(from, to, f)
}

private fun lerpScheme(a: ColorScheme, b: ColorScheme, f: Float): ColorScheme = a.copy(
    primary = lerpColor(a.primary, b.primary, f),
    onPrimary = lerpColor(a.onPrimary, b.onPrimary, f),
    primaryContainer = lerpColor(a.primaryContainer, b.primaryContainer, f),
    onPrimaryContainer = lerpColor(a.onPrimaryContainer, b.onPrimaryContainer, f),
    inversePrimary = lerpColor(a.inversePrimary, b.inversePrimary, f),
    secondary = lerpColor(a.secondary, b.secondary, f),
    onSecondary = lerpColor(a.onSecondary, b.onSecondary, f),
    secondaryContainer = lerpColor(a.secondaryContainer, b.secondaryContainer, f),
    onSecondaryContainer = lerpColor(a.onSecondaryContainer, b.onSecondaryContainer, f),
    tertiary = lerpColor(a.tertiary, b.tertiary, f),
    onTertiary = lerpColor(a.onTertiary, b.onTertiary, f),
    tertiaryContainer = lerpColor(a.tertiaryContainer, b.tertiaryContainer, f),
    onTertiaryContainer = lerpColor(a.onTertiaryContainer, b.onTertiaryContainer, f),
    background = lerpColor(a.background, b.background, f),
    onBackground = lerpColor(a.onBackground, b.onBackground, f),
    surface = lerpColor(a.surface, b.surface, f),
    onSurface = lerpColor(a.onSurface, b.onSurface, f),
    surfaceVariant = lerpColor(a.surfaceVariant, b.surfaceVariant, f),
    onSurfaceVariant = lerpColor(a.onSurfaceVariant, b.onSurfaceVariant, f),
    surfaceTint = lerpColor(a.surfaceTint, b.surfaceTint, f),
    inverseSurface = lerpColor(a.inverseSurface, b.inverseSurface, f),
    inverseOnSurface = lerpColor(a.inverseOnSurface, b.inverseOnSurface, f),
    error = lerpColor(a.error, b.error, f),
    onError = lerpColor(a.onError, b.onError, f),
    errorContainer = lerpColor(a.errorContainer, b.errorContainer, f),
    onErrorContainer = lerpColor(a.onErrorContainer, b.onErrorContainer, f),
    outline = lerpColor(a.outline, b.outline, f),
    outlineVariant = lerpColor(a.outlineVariant, b.outlineVariant, f),
    scrim = lerpColor(a.scrim, b.scrim, f),
    surfaceBright = lerpColor(a.surfaceBright, b.surfaceBright, f),
    surfaceDim = lerpColor(a.surfaceDim, b.surfaceDim, f),
    surfaceContainer = lerpColor(a.surfaceContainer, b.surfaceContainer, f),
    surfaceContainerHigh = lerpColor(a.surfaceContainerHigh, b.surfaceContainerHigh, f),
    surfaceContainerHighest = lerpColor(a.surfaceContainerHighest, b.surfaceContainerHighest, f),
    surfaceContainerLow = lerpColor(a.surfaceContainerLow, b.surfaceContainerLow, f),
    surfaceContainerLowest = lerpColor(a.surfaceContainerLowest, b.surfaceContainerLowest, f)
)

/** A background gradient as it is drawn when idle ([raw]) plus the same gradient sampled at fixed stops, so two of them can be blended. */
private class GradStops(val raw: List<Color>) {
    val sampled: List<Color> = List(GRAD_SAMPLES + 1) { i ->
        val x = i.toFloat() / GRAD_SAMPLES * (raw.size - 1)
        val lo = x.toInt().coerceIn(0, raw.size - 1)
        val hi = (lo + 1).coerceAtMost(raw.size - 1)
        lerpColor(raw[lo], raw[hi], x - lo)
    }
}

/** 12 steps: both the 3-colour and the 4-colour gradients have their colour stops exactly on a sample. */
private const val GRAD_SAMPLES = 12

private fun lerpStops(a: List<Color>, b: List<Color>, f: Float): List<Color> = List(a.size) { lerpColor(a[it], b[it], f) }

/** The scenery that is on screen right now (it is swapped in the middle of a theme fade). */
private class ShownDeco(val palette: AppPalette, val deco: Deco)

// ---------------------------------------------------------------------------
// Reusable animated background
// ---------------------------------------------------------------------------
@Composable
fun RizwanBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = LocalAppDark.current,
    scrim: Boolean = false,
    fireflies: Boolean = true,
    flowers: Boolean = true,
    palette: AppPalette = LocalAppPalette.current,
    content: @Composable BoxScope.() -> Unit = {}
) {
    // One looping clock (0..1 every LOOP_SECONDS), driven by the screen's frame clock. Every motion below uses whole
    // multiples of this clock, so the loop is seamless.
    // How much it moves comes from MotionStore (profile "অ্যানিমেশন কমান" + battery saver + the phone's animation setting):
    //   FULL = every frame, LOW = about 8 redraws a second (delay between frames), OFF = no loop at all, one still picture.
    // When the level changes the clock goes on from where it stopped, so the scenery does not jump.
    val level = MotionStore.level
    val time = remember { mutableFloatStateOf(STILL_TIME) }
    LaunchedEffect(level) {
        if (level == MotionLevel.OFF) return@LaunchedEffect
        val base = (time.floatValue * LOOP_NANOS).toLong()
        val start = withFrameNanos { it }
        val gap = if (level == MotionLevel.LOW) MotionStore.LOW_FRAME_MS else 0L
        while (true) {
            if (gap > 0L) delay(gap)
            withFrameNanos { now ->
                time.floatValue = (((now - start) + base) % LOOP_NANOS) / LOOP_NANOS.toFloat()
            }
        }
    }

    // Theme change: the gradient blends from the old colours to the new ones, the scenery (flowers / bubbles / stars)
    // fades out, is swapped and fades back in.
    val target = remember(darkTheme, palette) { GradStops(appGradientColors(palette, darkTheme)) }
    var gFrom by remember { mutableStateOf(target.sampled) }
    var gTo by remember { mutableStateOf(target) }
    val gP = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target === gTo) return@LaunchedEffect
        gFrom = if (gP.value >= 1f) gTo.sampled else lerpStops(gFrom, gTo.sampled, gP.value)
        gTo = target
        gP.snapTo(0f)
        gP.animateTo(1f, tween(THEME_FADE_MS, easing = FastOutSlowInEasing))
    }

    val deco = remember(darkTheme, palette) { Deco.of(palette, darkTheme) }
    var shown by remember { mutableStateOf(ShownDeco(palette, deco)) }
    val decoAlpha = remember { Animatable(1f) }
    LaunchedEffect(deco) {
        if (deco === shown.deco) return@LaunchedEffect
        decoAlpha.animateTo(0f, tween(THEME_FADE_MS / 3))
        shown = ShownDeco(palette, deco)
        decoAlpha.animateTo(1f, tween(THEME_FADE_MS * 2 / 3, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier.drawBehind {
            val f = gP.value
            drawRect(Brush.verticalGradient(colors = if (f >= 1f) gTo.raw else lerpStops(gFrom, gTo.sampled, f)))
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = decoAlpha.value }) {
            val t = time.floatValue
            val sp = shown.palette
            val sd = shown.deco
            // "flowers" = the theme's main scenery: flowers (garden), bubbles + waves (ocean), stars + nebula (twilight)
            when (sp) {
                AppPalette.GARDEN -> {
                    drawDust(t, sd)
                    if (flowers) drawFlowers(t, sd)
                }
                AppPalette.OCEAN -> {
                    drawDust(t, sd)
                    if (flowers) { drawWaves(t, sd); drawBubbles(t, sd) }
                }
                AppPalette.TWILIGHT -> {
                    if (flowers) drawNebula(t, sd)
                    drawDust(t, sd)
                    if (flowers) { drawStars(t, sd); drawShootingStars(t, sd) }
                }
            }
            if (scrim) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(sd.scrim.copy(alpha = 0.62f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.75f
                    )
                )
            }
            if (fireflies) drawFireflies(t, sd)
        }
        content()
    }
}

// ---------------------------------------------------------------------------
// Decoration colours of every design theme
// ---------------------------------------------------------------------------
private class Deco(
    val petals: List<Color>,
    val flowerCenter: Color,
    val twinkle: Color,
    val dust: Color,
    val fireflyGlow: Color,
    val fireflyCore: Color,
    val scrim: Color,
    /** ocean: wave / bubble tint, twilight: shooting-star colour */
    val accent: Color,
    /** twilight: soft nebula glows */
    val nebula: List<Color>
) {
    companion object {
        fun of(p: AppPalette, dark: Boolean): Deco = when (p) {
            AppPalette.GARDEN -> garden(dark)
            AppPalette.OCEAN -> ocean(dark)
            AppPalette.TWILIGHT -> twilight(dark)
        }

        private fun garden(dark: Boolean) = if (dark) {
            Deco(
                petals = listOf(Color(0xFF2FA15A), Color(0xFF4CC074), Color(0xFF7ED9A0)),
                flowerCenter = Color(0xFFFFE082),
                twinkle = Color(0xFFEFFFD9),
                dust = Color(0xFFCFF5D6),
                fireflyGlow = Color(0xFFD4FF3D),
                fireflyCore = Color(0xFFFFFDE0),
                scrim = Color(0xFF031009),
                accent = Color.Transparent,
                nebula = emptyList()
            )
        } else {
            Deco(
                petals = listOf(Color(0xFF43A047), Color(0xFF66BB6A), Color(0xFF2E7D32)),
                flowerCenter = Color(0xFFFFE57F),
                twinkle = Color.White,
                dust = Color(0xFFFFFFFF),
                fireflyGlow = Color(0xFFF9A825),
                fireflyCore = Color(0xFFFFF59D),
                scrim = Color(0xFFEFFAEA),
                accent = Color.Transparent,
                nebula = emptyList()
            )
        }

        private fun ocean(dark: Boolean) = if (dark) {
            Deco(
                petals = emptyList(),
                flowerCenter = Color.Transparent,
                twinkle = Color(0xFFE1F5FE),
                dust = Color(0xFFB3E5FC),
                fireflyGlow = Color(0xFF26C6DA),
                fireflyCore = Color(0xFFE0FFFF),
                scrim = Color(0xFF030E18),
                accent = Color(0xFF4FC3F7),
                nebula = emptyList()
            )
        } else {
            Deco(
                petals = emptyList(),
                flowerCenter = Color.Transparent,
                twinkle = Color.White,
                dust = Color.White,
                fireflyGlow = Color(0xFF29B6F6),
                fireflyCore = Color(0xFFFFFFFF),
                scrim = Color(0xFFEAF6FC),
                accent = Color(0xFF1E88C8),
                nebula = emptyList()
            )
        }

        private fun twilight(dark: Boolean) = if (dark) {
            Deco(
                petals = emptyList(),
                flowerCenter = Color.Transparent,
                twinkle = Color(0xFFF3EAFF),
                dust = Color(0xFFD9C8FF),
                fireflyGlow = Color(0xFFE879F9),
                fireflyCore = Color(0xFFFFF1FF),
                scrim = Color(0xFF0B0716),
                accent = Color(0xFFFFE9A8),
                nebula = listOf(Color(0xFF7E57C2), Color(0xFFD1409A), Color(0xFF3F51B5))
            )
        } else {
            Deco(
                petals = emptyList(),
                flowerCenter = Color.Transparent,
                twinkle = Color(0xFF8E5CD6),
                dust = Color.White,
                fireflyGlow = Color(0xFFD46FB0),
                fireflyCore = Color(0xFFFFF4FB),
                scrim = Color(0xFFF6EFFC),
                accent = Color(0xFFE0A63A),
                nebula = listOf(Color(0xFFB794F6), Color(0xFFF48FB1), Color(0xFF90CAF9))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Data (generated once, deterministic)
// ---------------------------------------------------------------------------
private class BgFlower(
    val x: Float,
    val y: Float,
    val size: Float,
    val petals: Int,
    val rotation: Float,
    val tone: Int,
    val alpha: Float,
    val phase: Float
)

private class BgFirefly(
    val x: Float,
    val y: Float,
    val ampX: Float,
    val ampY: Float,
    val freqX: Int,
    val freqY: Int,
    val phaseX: Float,
    val phaseY: Float,
    val blinkFreq: Int,
    val blinkPhase: Float,
    val size: Float
)

private class BgDust(
    val x: Float,
    val y: Float,
    val size: Float,
    val freq: Int,
    val phase: Float
)

// small flowers: radius is only ~1.2% - 3.2% of the shorter screen side
private val BgFlowers: List<BgFlower> = run {
    val random = Random(11)
    List(85) {
        BgFlower(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.012f + random.nextFloat() * 0.020f,
            petals = 5 + random.nextInt(3),
            rotation = random.nextFloat() * 360f,
            tone = random.nextInt(3),
            alpha = 0.55f + random.nextFloat() * 0.40f,
            phase = random.nextFloat()
        )
    }
}

private val BgFireflies: List<BgFirefly> = run {
    val random = Random(23)
    List(46) {
        BgFirefly(
            x = random.nextFloat(),
            y = random.nextFloat(),
            ampX = 0.04f + random.nextFloat() * 0.10f,
            ampY = 0.04f + random.nextFloat() * 0.09f,
            freqX = 1 + random.nextInt(3),
            freqY = 2 + random.nextInt(4),
            phaseX = random.nextFloat(),
            phaseY = random.nextFloat(),
            blinkFreq = 4 + random.nextInt(7),
            blinkPhase = random.nextFloat(),
            size = 1.6f + random.nextFloat() * 1.8f
        )
    }
}

private val BgDusts: List<BgDust> = run {
    val random = Random(5)
    List(130) {
        BgDust(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.8f + random.nextFloat() * 1.4f,
            freq = 3 + random.nextInt(8),
            phase = random.nextFloat()
        )
    }
}

private class BgBubble(
    val x: Float, val y: Float, val size: Float, val speed: Int,
    val ampX: Float, val freq: Int, val phase: Float, val alpha: Float
)

private class BgStar(val x: Float, val y: Float, val size: Float, val freq: Int, val phase: Float)

private class BgNebula(val x: Float, val y: Float, val radius: Float, val phase: Float)

// ocean: rising bubbles (speed = whole screen heights per loop, so the loop is seamless)
private val BgBubbles: List<BgBubble> = run {
    val random = Random(31)
    List(46) {
        BgBubble(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.006f + random.nextFloat() * 0.020f,
            speed = 1 + random.nextInt(3),
            ampX = 0.010f + random.nextFloat() * 0.030f,
            freq = 1 + random.nextInt(3),
            phase = random.nextFloat(),
            alpha = 0.45f + random.nextFloat() * 0.5f
        )
    }
}

// twilight: twinkling stars
private val BgStars: List<BgStar> = run {
    val random = Random(47)
    List(60) {
        BgStar(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.9f + random.nextFloat() * 1.5f,
            freq = 2 + random.nextInt(6),
            phase = random.nextFloat()
        )
    }
}

private val BgNebulae: List<BgNebula> = listOf(
    BgNebula(0.15f, 0.18f, 0.60f, 0.00f),
    BgNebula(0.85f, 0.45f, 0.55f, 0.33f),
    BgNebula(0.35f, 0.82f, 0.65f, 0.66f)
)

private const val LOOP_SECONDS = 12L
private const val LOOP_NANOS = LOOP_SECONDS * 1_000_000_000L

/** The picture shown when the background does not move (no shooting star is on screen at this moment of the loop). */
private const val STILL_TIME = 0.25f

private const val TWO_PI = (2.0 * PI).toFloat()

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

/** Many tiny dots shimmering across the whole screen. */
private fun DrawScope.drawDust(t: Float, palette: Deco) {
    BgDusts.forEach { d ->
        val s = sin(TWO_PI * (t * d.freq + d.phase)) * 0.5f + 0.5f
        val a = s * s
        if (a > 0.04f) {
            drawCircle(
                color = palette.dust.copy(alpha = a),
                radius = d.size.dp.toPx(),
                center = Offset(d.x * size.width, d.y * size.height)
            )
        }
    }
}

/** Small green vector flowers with tiny twinkles on their petals. */
private fun DrawScope.drawFlowers(t: Float, palette: Deco) {
    val base = size.minDimension
    BgFlowers.forEach { f ->
        val c = Offset(f.x * size.width, f.y * size.height)
        val r = base * f.size
        // gentle sway (one full cycle per loop)
        val rot = f.rotation + 9f * sin(TWO_PI * (t + f.phase))
        val color = palette.petals[f.tone].copy(alpha = f.alpha)

        for (i in 0 until f.petals) {
            rotate(degrees = rot + i * 360f / f.petals, pivot = c) {
                drawOval(
                    color = color,
                    topLeft = Offset(c.x - r * 0.30f, c.y - r),
                    size = Size(r * 0.60f, r)
                )
            }
        }
        drawCircle(
            color = palette.flowerCenter.copy(alpha = 0.95f),
            radius = r * 0.24f,
            center = c
        )

        // two twinkles per flower
        for (k in 0 until 2) {
            val angle = Math.toRadians((rot + k * 180f + 40f).toDouble())
            val dist = r * (0.55f + 0.35f * k)
            val p = Offset(
                c.x + (cos(angle) * dist).toFloat(),
                c.y + (sin(angle) * dist).toFloat()
            )
            val s = sin(TWO_PI * (t * 5f + f.phase + k * 0.5f)) * 0.5f + 0.5f
            val a = s * s * s
            if (a > 0.05f) {
                val rad = 0.7.dp.toPx() + 1.1.dp.toPx() * a
                drawCircle(color = palette.twinkle.copy(alpha = a), radius = rad, center = p)
                if (a > 0.7f) {
                    val len = rad * 3f
                    val w = 0.8.dp.toPx()
                    drawLine(
                        color = palette.twinkle.copy(alpha = a),
                        start = Offset(p.x - len, p.y),
                        end = Offset(p.x + len, p.y),
                        strokeWidth = w,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = palette.twinkle.copy(alpha = a),
                        start = Offset(p.x, p.y - len),
                        end = Offset(p.x, p.y + len),
                        strokeWidth = w,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/** Glowing fireflies (jonaki poka) drifting and blinking. */
private fun DrawScope.drawFireflies(t: Float, palette: Deco) {
    BgFireflies.forEach { f ->
        val nx = f.x + f.ampX * sin(TWO_PI * (t * f.freqX + f.phaseX))
        val ny = f.y + f.ampY * sin(TWO_PI * (t * f.freqY + f.phaseY))
        val p = Offset(nx * size.width, ny * size.height)

        val s = sin(TWO_PI * (t * f.blinkFreq + f.blinkPhase)) * 0.5f + 0.5f
        val a = 0.10f + 0.90f * s * s * s
        val core = f.size.dp.toPx()

        // soft glow layers
        drawCircle(palette.fireflyGlow.copy(alpha = 0.07f * a), radius = core * 6.5f, center = p)
        drawCircle(palette.fireflyGlow.copy(alpha = 0.16f * a), radius = core * 3.8f, center = p)
        drawCircle(palette.fireflyGlow.copy(alpha = 0.38f * a), radius = core * 2.0f, center = p)
        // bright body
        drawCircle(palette.fireflyCore.copy(alpha = a), radius = core, center = p)
    }
}

// ---------------------------------------------------------------------------
// Ocean
// ---------------------------------------------------------------------------

/** Three slow translucent waves along the bottom edge. */
private fun DrawScope.drawWaves(t: Float, c: Deco) {
    val w = size.width
    val h = size.height
    for (i in 0 until 3) {
        val path = Path()
        val baseY = h * (0.905f + i * 0.032f)
        val amp = h * (0.010f + i * 0.004f)
        val dir = if (i % 2 == 0) 1f else -1f
        path.moveTo(0f, h)
        val step = w / 36f
        var x = 0f
        while (x <= w + step) {
            val y = baseY + amp * sin(TWO_PI * (x / w * (1.4f + i * 0.5f) + t * (i + 1) * dir))
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(w + step, h)
        path.close()
        drawPath(path, c.accent.copy(alpha = 0.09f + 0.04f * i))
    }
}

/** Glassy bubbles drifting upward, fading at the top and bottom. */
private fun DrawScope.drawBubbles(t: Float, c: Deco) {
    val base = size.minDimension
    val ring = 1.dp.toPx()
    BgBubbles.forEach { b ->
        val yy = (((b.y - t * b.speed) % 1f) + 1f) % 1f
        val xx = b.x + b.ampX * sin(TWO_PI * (t * b.freq + b.phase))
        val edge = minOf(yy, 1f - yy)
        val a = b.alpha * (edge / 0.08f).coerceIn(0f, 1f)
        if (a < 0.02f) return@forEach
        val r = base * b.size
        val p = Offset(xx * size.width, yy * size.height)
        drawCircle(c.twinkle.copy(alpha = 0.12f * a), radius = r, center = p)
        drawCircle(c.accent.copy(alpha = 0.70f * a), radius = r, center = p, style = Stroke(width = ring))
        drawCircle(Color.White.copy(alpha = 0.90f * a), radius = r * 0.22f, center = Offset(p.x - r * 0.35f, p.y - r * 0.35f))
    }
}

// ---------------------------------------------------------------------------
// Twilight
// ---------------------------------------------------------------------------

/** Big soft colour clouds drifting slowly behind the stars. */
private fun DrawScope.drawNebula(t: Float, c: Deco) {
    BgNebulae.forEachIndexed { i, n ->
        val cx = (n.x + 0.05f * sin(TWO_PI * (t + n.phase))) * size.width
        val cy = (n.y + 0.04f * cos(TWO_PI * (t + n.phase))) * size.height
        val color = c.nebula[i % c.nebula.size]
        val r = size.minDimension * n.radius
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.26f), Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r,
            center = Offset(cx, cy)
        )
    }
}

/** Twinkling stars; the brightest ones get a small cross-shaped glint. */
private fun DrawScope.drawStars(t: Float, c: Deco) {
    BgStars.forEach { s ->
        val k = sin(TWO_PI * (t * s.freq + s.phase)) * 0.5f + 0.5f
        val a = 0.12f + 0.88f * k * k
        val p = Offset(s.x * size.width, s.y * size.height)
        val r = s.size.dp.toPx() * (0.7f + 0.5f * k)
        drawCircle(c.twinkle.copy(alpha = a), radius = r, center = p)
        if (a > 0.6f) {
            val len = r * 3.6f * a
            val w = 0.8.dp.toPx()
            drawLine(c.twinkle.copy(alpha = a), Offset(p.x - len, p.y), Offset(p.x + len, p.y), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c.twinkle.copy(alpha = a), Offset(p.x, p.y - len), Offset(p.x, p.y + len), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

// (start of the streak in the loop, end, from x, from y, dx, dy) - all as fractions of the screen / the loop
private val Shoots = listOf(
    floatArrayOf(0.08f, 0.20f, 0.10f, 0.06f, 0.42f, 0.20f),
    floatArrayOf(0.55f, 0.67f, 0.58f, 0.30f, 0.38f, 0.18f)
)

/** Two shooting stars per loop. */
private fun DrawScope.drawShootingStars(t: Float, c: Deco) {
    Shoots.forEach { s ->
        if (t < s[0] || t > s[1]) return@forEach
        val p = (t - s[0]) / (s[1] - s[0])
        val head = Offset((s[2] + s[4] * p) * size.width, (s[3] + s[5] * p) * size.height)
        val tail = Offset(head.x - s[4] * size.width * 0.22f, head.y - s[5] * size.height * 0.22f)
        val a = sin(PI.toFloat() * p)
        drawLine(
            brush = Brush.linearGradient(listOf(Color.Transparent, c.accent.copy(alpha = a)), start = tail, end = head),
            start = tail, end = head, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
        )
        drawCircle(Color.White.copy(alpha = a), radius = 1.6.dp.toPx(), center = head)
    }
}

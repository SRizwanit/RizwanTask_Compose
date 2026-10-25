package com.srizwan.task

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
 *       darkTheme  = ...    -> force night or day (default: follows the system)
 * ---------------------------------------------------------------------------
 */

// ---------------------------------------------------------------------------
// Material colors (day / night)
// ---------------------------------------------------------------------------
private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

/** Day / night choice: Auto = follow the phone, Light, Dark (same 3 modes as the web app). */
enum class ThemeMode(val label: String, val icon: String) {
    Auto("সিস্টেম অনুযায়ী", "🌗"),
    Light("লাইট মোড", "☀️"),
    Dark("ডার্ক মোড", "🌙");

    fun next(): ThemeMode = when (this) {
        Auto -> Light
        Light -> Dark
        Dark -> Auto
    }
}

/** Saved on the phone. Call AppTheme.init(context) once in MainActivity.onCreate. */
object AppTheme {
    private const val PREFS = "rizwantask_theme"
    private const val KEY = "mode"

    var mode by mutableStateOf(ThemeMode.Auto)
        private set

    fun init(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.Auto
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.name).apply()
    }
}

/** true when the app is in night mode (follows the AppTheme switch). */
val LocalAppDark = staticCompositionLocalOf { false }

@Composable
fun appDarkTheme(): Boolean = when (AppTheme.mode) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun RizwanTaskTheme(
    darkTheme: Boolean = appDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}

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
    content: @Composable BoxScope.() -> Unit = {}
) {
    // One looping clock (0..1 every LOOP_SECONDS). It is driven by the screen's
    // frame clock, so it keeps running even when the phone's "animator duration
    // scale" is off or battery saver shortens system animations.
    // Every motion below uses whole multiples of this clock, so the loop is seamless.
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time.floatValue = ((now - start) % LOOP_NANOS) / LOOP_NANOS.toFloat()
            }
        }
    }

    val gradient = remember(darkTheme) {
        Brush.verticalGradient(
            colors = if (darkTheme) {
                listOf(Color(0xFF020B07), Color(0xFF052418), Color(0xFF0A3A27), Color(0xFF031109))
            } else {
                listOf(Color(0xFFF4FCEF), Color(0xFFDDF3D4), Color(0xFFBFE5BC))
            }
        )
    }

    val palette = remember(darkTheme) { GardenPalette.of(darkTheme) }

    Box(modifier = modifier.background(gradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = time.floatValue
            drawDust(t, palette)
            if (flowers) drawFlowers(t, palette)
            if (scrim) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.scrim.copy(alpha = 0.62f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.75f
                    )
                )
            }
            if (fireflies) drawFireflies(t, palette)
        }
        content()
    }
}

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------
private class GardenPalette(
    val petals: List<Color>,
    val flowerCenter: Color,
    val twinkle: Color,
    val dust: Color,
    val fireflyGlow: Color,
    val fireflyCore: Color,
    val scrim: Color
) {
    companion object {
        fun of(dark: Boolean) = if (dark) {
            GardenPalette(
                petals = listOf(Color(0xFF2FA15A), Color(0xFF4CC074), Color(0xFF7ED9A0)),
                flowerCenter = Color(0xFFFFE082),
                twinkle = Color(0xFFEFFFD9),
                dust = Color(0xFFCFF5D6),
                fireflyGlow = Color(0xFFD4FF3D),
                fireflyCore = Color(0xFFFFFDE0),
                scrim = Color(0xFF031009)
            )
        } else {
            GardenPalette(
                petals = listOf(Color(0xFF43A047), Color(0xFF66BB6A), Color(0xFF2E7D32)),
                flowerCenter = Color(0xFFFFE57F),
                twinkle = Color.White,
                dust = Color(0xFFFFFFFF),
                fireflyGlow = Color(0xFFF9A825),
                fireflyCore = Color(0xFFFFF59D),
                scrim = Color(0xFFEFFAEA)
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

private const val LOOP_SECONDS = 12L
private const val LOOP_NANOS = LOOP_SECONDS * 1_000_000_000L

private const val TWO_PI = (2.0 * PI).toFloat()

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

/** Many tiny dots shimmering across the whole screen. */
private fun DrawScope.drawDust(t: Float, palette: GardenPalette) {
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
private fun DrawScope.drawFlowers(t: Float, palette: GardenPalette) {
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
private fun DrawScope.drawFireflies(t: Float, palette: GardenPalette) {
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

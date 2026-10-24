package com.srizwan.task

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Timing configuration
// ---------------------------------------------------------------------------
private const val BRAND_NAME = "RizwanTask"
private const val START_DELAY_MS = 600L          // pause before typing starts
private const val TYPE_DELAY_MS = 160L           // delay between each typed letter
private const val WAIT_AFTER_TYPING_MS = 20_000L // 20 seconds after typing completes -> Login

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RizwanTaskTheme {
                AppRoot()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Theme (follows the system day / night mode)
// ---------------------------------------------------------------------------
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    background = Color(0xFFF1F8E9),
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
    background = Color(0xFF07130B),
    onBackground = Color(0xFFE2F3E3),
    surface = Color(0xFF0F1F14),
    onSurface = Color(0xFFE2F3E3),
    surfaceVariant = Color(0xFF1B3322),
    onSurfaceVariant = Color(0xFFB5D3B8),
    outline = Color(0xFF4F7A56)
)

@Composable
fun RizwanTaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Root: Splash -> Login (Login.kt)
// ---------------------------------------------------------------------------
@Composable
fun AppRoot() {
    // rememberSaveable keeps the state across rotation / process recreation
    var showLogin by rememberSaveable { mutableStateOf(false) }

    Crossfade(
        targetState = showLogin,
        animationSpec = tween(durationMillis = 700),
        label = "root_crossfade"
    ) { loginVisible ->
        if (loginVisible) {
            LoginScreen()
        } else {
            SplashScreen(onFinished = { showLogin = true })
        }
    }
}

// ---------------------------------------------------------------------------
// Splash screen
// ---------------------------------------------------------------------------
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Typewriter state
    var typedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(START_DELAY_MS)
        for (i in 1..BRAND_NAME.length) {
            typedCount = i
            delay(TYPE_DELAY_MS)
        }
        delay(WAIT_AFTER_TYPING_MS)
        currentOnFinished()
    }

    // Shared infinite animations (all read lazily inside draw / graphicsLayer blocks)
    val infinite = rememberInfiniteTransition(label = "splash_infinite")
    val sparkle = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sparkle"
    )
    val drift = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(150_000, easing = LinearEasing)),
        label = "drift"
    )
    val dots = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "dots"
    )
    val pulse = infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val cursor = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "cursor"
    )

    val backgroundBrush = Brush.verticalGradient(
        colors = if (dark) {
            listOf(Color(0xFF06110A), Color(0xFF0C2214), Color(0xFF07130B))
        } else {
            listOf(Color(0xFFF7FCF1), Color(0xFFDFF0D2), Color(0xFFC8E6C9))
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Green vector flowers + twinkling sparkles
        FlowerBackground(
            dark = dark,
            sparkle = sparkle,
            drift = drift,
            scrimColor = if (dark) Color(0xFF07130B) else Color(0xFFF1F8E9)
        )

        // Center: logo + typewriter text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    },
                shape = CircleShape,
                color = colors.surface.copy(alpha = if (dark) 0.85f else 0.92f),
                shadowElevation = 14.dp,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rizwantask),
                        contentDescription = "RizwanTask logo",
                        modifier = Modifier.size(118.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            TypewriterText(
                fullText = BRAND_NAME,
                typedCount = typedCount,
                cursor = cursor
            )
        }

        // Bottom: dotted loading animation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 56.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            DottedLoader(progress = dots, color = colors.primary)
        }
    }
}

// ---------------------------------------------------------------------------
// Typewriter text (fixed layout width, so the text does not jump while typing)
// ---------------------------------------------------------------------------
private val BrandFont = FontFamily(
    Font(R.font.cormorantgaramondbold, FontWeight.Bold)
)

@Composable
private fun TypewriterText(
    fullText: String,
    typedCount: Int,
    cursor: State<Float>
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val cursorColor = MaterialTheme.colorScheme.primary
    val style = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 1.5.sp
    )

    val cursorOn = cursor.value < 0.5f
    val typed = fullText.take(typedCount)

    Box(contentAlignment = Alignment.CenterStart) {
        // Invisible full text (+ cursor) reserves the final width
        Text(
            text = "$fullText|",
            style = style,
            color = Color.Transparent
        )
        // Visible typed text with blinking cursor
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = textColor)) { append(typed) }
                withStyle(
                    SpanStyle(color = if (cursorOn) cursorColor else Color.Transparent)
                ) { append("|") }
            },
            style = style
        )
    }
}

// ---------------------------------------------------------------------------
// Dotted loader animation (wave of bouncing dots)
// ---------------------------------------------------------------------------
@Composable
private fun DottedLoader(progress: State<Float>, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        val phase = progress.value - index * 0.12f
                        val p = (sin(2f * PI.toFloat() * phase) * 0.5f) + 0.5f
                        val s = 0.55f + 0.65f * p
                        scaleX = s
                        scaleY = s
                        alpha = 0.35f + 0.65f * p
                        translationY = -8.dp.toPx() * p
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Flower vector background (drawn with Canvas, no XML)
// ---------------------------------------------------------------------------
private data class Flower(
    val x: Float,        // 0..1 of width
    val y: Float,        // 0..1 of height
    val size: Float,     // fraction of min(width, height)
    val petals: Int,
    val rotation: Float,
    val tone: Int,
    val alpha: Float,
    val spin: Float      // +1 or -1 slow rotation direction
)

private val Flowers: List<Flower> = run {
    val random = Random(7)
    List(18) {
        Flower(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.07f + random.nextFloat() * 0.13f,
            petals = 5 + random.nextInt(4),
            rotation = random.nextFloat() * 360f,
            tone = random.nextInt(3),
            alpha = 0.40f + random.nextFloat() * 0.40f,
            spin = if (random.nextBoolean()) 1f else -1f
        )
    }
}

private const val SPARKLES_PER_FLOWER = 6

@Composable
private fun FlowerBackground(
    dark: Boolean,
    sparkle: State<Float>,
    drift: State<Float>,
    scrimColor: Color
) {
    val palette = if (dark) {
        listOf(Color(0xFF2E9E4F), Color(0xFF1F7A3A), Color(0xFF3CB35E))
    } else {
        listOf(Color(0xFF43A047), Color(0xFF66BB6A), Color(0xFF2E7D32))
    }
    val centerColor = if (dark) Color(0xFFA5D66B) else Color(0xFFC6E48B)
    val sparkleColor = if (dark) Color(0xFFE8FFD0) else Color.White

    Canvas(modifier = Modifier.fillMaxSize()) {
        val base = size.minDimension
        val twoPi = 2f * PI.toFloat()
        val outline = 1.2.dp.toPx()

        // 1) Flowers
        Flowers.forEach { f ->
            val c = Offset(f.x * size.width, f.y * size.height)
            val r = base * f.size
            val rot = f.rotation + drift.value * f.spin
            val color = palette[f.tone].copy(alpha = f.alpha)
            val inner = lerp(palette[f.tone], Color.White, 0.28f).copy(alpha = f.alpha)

            // outer petals
            for (i in 0 until f.petals) {
                rotate(degrees = rot + i * 360f / f.petals, pivot = c) {
                    val topLeft = Offset(c.x - r * 0.28f, c.y - r)
                    val petalSize = Size(r * 0.56f, r)
                    drawOval(color = color, topLeft = topLeft, size = petalSize)
                    drawOval(
                        color = color.copy(alpha = (f.alpha + 0.2f).coerceAtMost(1f)),
                        topLeft = topLeft,
                        size = petalSize,
                        style = Stroke(width = outline)
                    )
                }
            }
            // inner petals (shifted by half a step)
            for (i in 0 until f.petals) {
                rotate(
                    degrees = rot + (i + 0.5f) * 360f / f.petals,
                    pivot = c
                ) {
                    drawOval(
                        color = inner,
                        topLeft = Offset(c.x - r * 0.17f, c.y - r * 0.62f),
                        size = Size(r * 0.34f, r * 0.62f)
                    )
                }
            }
            // flower center
            drawCircle(color = centerColor.copy(alpha = 0.95f), radius = r * 0.17f, center = c)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = r * 0.17f,
                center = c,
                style = Stroke(width = outline)
            )
        }

        // 2) Soft scrim in the middle so the logo and text stay readable
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(scrimColor.copy(alpha = 0.78f), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = base * 0.85f
            )
        )

        // 3) Twinkling sparkles on the flowers
        Flowers.forEachIndexed { index, f ->
            val c = Offset(f.x * size.width, f.y * size.height)
            val r = base * f.size
            val rot = f.rotation + drift.value * f.spin

            for (k in 0 until SPARKLES_PER_FLOWER) {
                val angle = Math.toRadians((rot + k * 60f + index * 17f).toDouble())
                val dist = r * (0.40f + 0.55f * (((k * 37 + index * 13) % 10) / 10f))
                val p = Offset(
                    c.x + (cos(angle) * dist).toFloat(),
                    c.y + (sin(angle) * dist).toFloat()
                )

                val phase = (sparkle.value + ((k * 0.17f + index * 0.31f) % 1f)) % 1f
                val s = sin(twoPi * phase) * 0.5f + 0.5f
                val a = s * s * s
                if (a < 0.03f) continue

                val radius = 1.2.dp.toPx() + 2.2.dp.toPx() * a
                drawCircle(color = sparkleColor.copy(alpha = a), radius = radius, center = p)

                // bright four-point star on peak twinkle
                if (a > 0.6f) {
                    val len = radius * 3.2f
                    val stroke = 1.dp.toPx()
                    drawLine(
                        color = sparkleColor.copy(alpha = a),
                        start = Offset(p.x - len, p.y),
                        end = Offset(p.x + len, p.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = sparkleColor.copy(alpha = a),
                        start = Offset(p.x, p.y - len),
                        end = Offset(p.x, p.y + len),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

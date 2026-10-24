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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Timing configuration
// ---------------------------------------------------------------------------
private const val BRAND_NAME = "RizwanTask"
private const val START_DELAY_MS = 600L          // pause before typing starts
private const val TYPE_DELAY_MS = 160L           // delay between each typed letter
private const val WAIT_AFTER_TYPING_MS = 5_000L // 20 seconds after typing completes -> Login

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme + colors live in Theme.kt
            RizwanTaskTheme {
                AppRoot()
            }
        }
    }
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

    // Animations for logo, cursor and dotted loader
    val infinite = rememberInfiniteTransition(label = "splash_infinite")
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

    // Animated flower + firefly background from Theme.kt
    RizwanBackground(
        modifier = Modifier.fillMaxSize(),
        scrim = true
    ) {
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
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 56.dp)
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

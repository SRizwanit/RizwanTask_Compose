package com.srizwan.task.ui

import android.provider.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.ripple.RippleAlpha
import com.srizwan.task.LocalAppDark

/**
 * React `view/useRipple.js` + the `.rip` rules of `View.css` -> the Material ripple that every Compose `clickable` / button already has.
 *
 * Nothing of the JavaScript is ported, on purpose: the script only existed to spawn a circle at the touch point, let it grow to the farthest
 * corner (750 ms, fading out), keep it alive when the button's label changes (listen -> pause, play -> stop) and skip disabled buttons.
 * Compose's ripple does all of these by itself — it starts at the touch point (or the centre for keyboard / accessibility clicks), is clipped to
 * the button, lives in the button's interaction state (so a changed label does not remove it) and is not drawn for a disabled `clickable`.
 *
 * What is left is the *look*: this wraps the View page so the ripple is the same light green as in React.
 *  - normal button: wave `rgba(34,197,94,.6)` (dark: `rgba(74,222,128,.6)`), a light-green tint `.18` while held;
 *  - on a filled primary button ([onPrimary], React `button.on .rip`): light `rgba(220,252,231,.9)`, dark `rgba(21,128,61,.55)`.
 * "মোশন কমান" (React `prefers-reduced-motion`): when the phone's animations are switched off (animator scale 0) no ripple is drawn at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewRippleHost(onPrimary: Boolean = false, content: @Composable () -> Unit) {
    val dark = LocalAppDark.current
    val resolver = LocalContext.current.contentResolver
    val reduce = remember { try { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false } }
    val cfg = remember(dark, onPrimary, reduce) {
        if (reduce) null else RippleConfiguration(
            color = when {
                onPrimary && dark -> Color(0xFF15803D)
                onPrimary -> Color(0xFFDCFCE7)
                dark -> Color(0xFF4ADE80)
                else -> Color(0xFF22C55E)
            },
            // Material draws the wave with the pressed alpha; the held tint is the focused / dragged alpha
            rippleAlpha = RippleAlpha(
                pressedAlpha = if (onPrimary) (if (dark) 0.55f else 0.9f) else 0.6f,
                focusedAlpha = 0.18f, draggedAlpha = 0.18f, hoveredAlpha = 0.08f
            )
        )
    }
    CompositionLocalProvider(LocalRippleConfiguration provides cfg) { content() }
}

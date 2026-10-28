package com.srizwan.task.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small haptic "tick" for buttons. Goes through the view's haptic feedback, so the phone's own
 * "touch feedback / vibration" setting is respected (switched off there = no tick here).
 */
@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** `clickable` + a light haptic tick on every tap (a disabled button neither clicks nor ticks). */
@Composable
fun Modifier.hapticClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val tick = rememberTick()
    return this.clickable(enabled = enabled) { tick(); onClick() }
}

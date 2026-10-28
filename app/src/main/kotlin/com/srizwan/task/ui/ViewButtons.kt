package com.srizwan.task.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.LocalAppDark

/** Look of a [ViewBtn] — React `View.css`: plain button, `.on` (green), `.primary` (blue), `.off` (red, the auto-OCR switch when off). */
enum class ViewBtnKind { Normal, On, Primary, Off }

/**
 * The button of the View page (React `button`, `button.ico`, `.on`, `.primary`, `.off` of `View.css`): a vector icon, a label, or both.
 * No label = a square icon button. [pulse] = the icon fades in and out (React `#btnApi.run .i`, `apiPulse`). [hint] = the React `title` tooltip: a phone has no
 * hover, so a long press passes it to [onHint] (the page shows it as a toast); [desc] is the screen-reader label. A disabled button is 50% faded.
 * The ripple is the green one of [ViewRippleHost] (the filled kinds use the light ripple).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    label: String? = null,
    kind: ViewBtnKind = ViewBtnKind.Normal,
    enabled: Boolean = true,
    pulse: Boolean = false,
    hint: String? = null,
    desc: String? = null,
    onHint: (String) -> Unit = {},
    height: Dp = 38.dp,
    padH: Dp = 12.dp,
    width: Dp? = null,
    radius: Dp = 10.dp,
    iconSize: Dp = 18.dp,
    fontSize: TextUnit = 14.sp,
    flat: Boolean = false
) {
    val c = viewColors()
    val tick = rememberTick()
    val dark = LocalAppDark.current
    val filledText = if (dark) Color(0xFF0B0D10) else Color.White
    val (bg, fg, edge) = when (kind) {
        ViewBtnKind.Normal -> Triple(c.bg, c.fg, c.line)
        ViewBtnKind.On -> Triple(c.ok, filledText, c.ok)
        ViewBtnKind.Primary -> Triple(c.pri, c.priFg, c.pri)
        ViewBtnKind.Off -> Triple(c.err, filledText, c.err)
    }
    val shape = RoundedCornerShape(if (flat) 0.dp else radius)
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 0.3f at 500; 1f at 1000 }, RepeatMode.Restart),
        label = "pulseAlpha"
    )
    val iconAlpha = if (pulse) pulseAlpha else 1f
    val sizeMod = if (width != null) Modifier.width(width).height(height)
        else if (label == null) Modifier.defaultMinSize(minWidth = height).height(height) else Modifier.height(height)
    ViewRippleHost(onPrimary = kind != ViewBtnKind.Normal) {
        Row(
            modifier.then(sizeMod).alpha(if (enabled) 1f else 0.5f).clip(shape).background(if (flat) Color.Transparent else bg).then(if (flat) Modifier else Modifier.border(1.dp, edge, shape))
                .semantics { contentDescription = desc ?: label ?: hint.orEmpty(); role = Role.Button }
                .combinedClickable(
                    enabled = enabled, onClick = { tick(); onClick() },
                    onLongClick = if (hint != null) ({ onHint(hint) }) else null
                )
                .padding(horizontal = if (label != null) padH else if (width != null) 0.dp else 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                ViewIcon(icon, Modifier.alpha(iconAlpha), size = iconSize, tint = fg)
            }
            if (label != null) {
                if (icon != null) Spacer(Modifier.width(6.dp))
                Text(label, color = fg, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

package com.srizwan.task.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.LocalAppDark
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.ThemeMode
import com.srizwan.task.core.ThemeStore

// ---- React CSS variables that Material3 has no slot for ----
@Composable fun errColor(): Color = if (LocalAppDark.current) Color(0xFFF87171) else Color(0xFFDC2626)
@Composable fun okColor(): Color = if (LocalAppDark.current) Color(0xFF6FD08C) else Color(0xFF15803D)
@Composable fun warnColor(): Color = if (LocalAppDark.current) Color(0xFFFBBF24) else Color(0xFFB45309)
@Composable fun goldColor(): Color = if (LocalAppDark.current) Color(0xFFE8C56F) else Color(0xFFC9972B)

/** `.brand`: logo tile + title + subtitle (+ optional extra, e.g. a badge). */
@Composable
fun BrandHeader(
    title: String,
    subtitle: String? = null,
    icon: String = "mark",
    extra: (@Composable () -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(icon, size = if (icon == "mark") 42.dp else 30.dp, tint = cs.onPrimary)
        }
        Text(title, color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        extra?.invoke()
    }
}

/** `.card`: rounded panel with the golden gradient edge. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cs.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(gold, cs.outline, cs.outline, gold))),
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** `label.f` */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth().padding(top = if (first) 0.dp else 14.dp, bottom = 6.dp)
    )
}

/** `input` / `input.pin` */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    pin: Boolean = false,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = if (pin) {
        TextStyle(fontFamily = AnekBangla, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 13.2.sp, color = cs.onSurface)
    } else {
        TextStyle(fontFamily = AnekBangla, fontSize = 14.sp, color = cs.onSurface)
    }
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(cs.primary),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onAction() }, onGo = { onAction() }, onNext = { onAction() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.55f), shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) cs.primary else cs.outline.copy(alpha = 0.6f), shape)
                    .padding(horizontal = 12.dp, vertical = 11.dp)
                    .padding(start = if (pin) 13.dp else 0.dp),
                contentAlignment = if (pin) Alignment.Center else Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = cs.onSurfaceVariant.copy(alpha = 0.55f)))
                inner()
            }
        }
    )
}

/** `button.primary.block`: green gradient + a light shine sweeping across every 5.5 s (theme.css kpShine). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val inf = rememberInfiniteTransition(label = "shine")
    val sweep = inf.animateFloat(
        initialValue = -1.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5500
                -1.3f at 0
                -1.3f at 3410
                1.3f at 5500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    Box(
        modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
            .clickable(enabled = enabled, onClick = onClick)
            .drawWithContent {
                drawContent()
                val w = size.width
                val t = sweep.value
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                        startX = w * (0.38f + t),
                        endX = w * (0.62f + t)
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** plain `button.block` (outlined panel) */
@Composable
fun BlockButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = cs.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** `button.help.block` (primary outline) / `button.danger.block` (red outline) */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(shape)
            .border(1.dp, color, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** `button.link` / `button.link.mut` */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, muted: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (muted) cs.onSurfaceVariant else cs.primary,
            fontSize = 12.5.sp,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

/** `.spin` */
@Composable
fun Spinner(on: Boolean) {
    if (on) {
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** `.err` */
@Composable
fun ErrText(text: String) {
    Text(
        text, color = errColor(), fontSize = 12.5.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp).padding(top = 10.dp)
    )
}

/** `.hint` */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = modifier.fillMaxWidth()
    )
}

/** `.seg` radio buttons (value to label) */
@Composable
fun SegChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).clip(shape)
                    .then(
                        if (on) Modifier.background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
                        else Modifier.background(cs.surfaceVariant.copy(alpha = 0.55f))
                    )
                    .border(1.dp, if (on) cs.primary else cs.outline.copy(alpha = 0.6f), shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) cs.onPrimary else cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/** `.badge.ok` */
@Composable
fun OkBadge(text: String, modifier: Modifier = Modifier) {
    val c = okColor()
    Box(modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 1.dp)) {
        Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Header theme button (ThemeToggle.jsx): system -> light -> dark. */
@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val icon = when (ThemeStore.mode) {
        ThemeMode.DARK -> "moon"
        ThemeMode.LIGHT -> "sun"
        ThemeMode.AUTO -> "auto"
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.size(36.dp).clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .clickable {
                ThemeStore.set(ThemeStore.next())
                AppToast.show("থিম: " + ThemeStore.label())
            },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, size = 18.dp, tint = cs.onSurface)
    }
}

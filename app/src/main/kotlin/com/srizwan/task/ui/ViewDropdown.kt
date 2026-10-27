package com.srizwan.task.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** React `Dropdown` option `{ value, label }`. */
class ViewDropdownOption(val value: String, val label: String)

/**
 * React `closeDropdown()` — only one dropdown list is open at a time. [close] closes the open one and says whether there was one
 * (used when the page list is re-arranged, and by the dialogs' close).
 */
object ViewDropdowns {
    private var active: (() -> Unit)? = null
    fun close(): Boolean { val f = active ?: return false; f(); return true }
    internal fun register(f: () -> Unit) { active = f }
    internal fun unregister(f: () -> Unit) { if (active === f) active = null }
}

/** Popup place: below the button (above when there is more room there); pulled back inside the screen sideways. */
private class DropdownPos(private val gapPx: Int, private val marginPx: Int, private val needPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val below = windowSize.height - anchorBounds.bottom - marginPx
        val above = anchorBounds.top - marginPx
        val down = below >= needPx || below >= above
        val y = if (down) anchorBounds.bottom + gapPx else maxOf(marginPx, anchorBounds.top - gapPx - popupContentSize.height)
        var x = anchorBounds.left
        if (x + popupContentSize.width > windowSize.width - marginPx) x = maxOf(marginPx, windowSize.width - marginPx - popupContentSize.width)
        return IntOffset(x, y)
    }
}

/**
 * React `view/Dropdown.jsx` — own dropdown (button + popup list, the same font as the rest of the page), because the system picker has its own look.
 * [options] = `{value, label}`, [value] = current value (string); when it matches nothing the first item shows, like a `<select>`.
 * Button: the current label (one line, cut with "…") and a down arrow (vector `down`); popup: at least 150dp / the button's width, at most 60% of
 * the screen height, scrolled so the current item is in the middle, the current item highlighted (primary colour, bold) with a tick (vector `check`).
 * Tap outside / Back = close. [width] fixes the button width (React `#sel` 130/150dp, `#ttsRate` 70dp); null = fill what the parent gives.
 */
@Composable
fun ViewDropdown(
    value: String,
    options: List<ViewDropdownOption>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    ariaLabel: String? = null,
    compact: Boolean = false
) {
    val c = viewColors()
    var open by remember { mutableStateOf(false) }
    val idx = options.indexOfFirst { it.value == value }
    val curIdx = if (idx >= 0) idx else if (options.isNotEmpty()) 0 else -1
    val label = options.getOrNull(curIdx)?.label.orEmpty()
    val closer = remember { { open = false } }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val shape = RoundedCornerShape(10.dp)

    Box(modifier) {
        Row(
            Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.line, shape)
                .onGloballyPositioned { anchor = it.boundsInWindow() }
                .clickable(role = Role.DropdownList) { if (open) open = false else { ViewDropdowns.close(); open = true } }
                .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 5.dp else 8.dp)
                .semantics {
                    if (ariaLabel != null) contentDescription = ariaLabel
                    stateDescription = if (open) "খোলা" else "বন্ধ"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, color = c.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ViewIcon("down", size = 14.dp, tint = c.muted)
        }
        if (open) {
            DisposableEffect(Unit) { ViewDropdowns.register(closer); onDispose { ViewDropdowns.unregister(closer) } }
            val density = LocalDensity.current
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val itemH = 44.dp
            val below = with(density) { (screenH.toPx() - anchor.bottom - 8.dp.toPx()) }
            val above = with(density) { (anchor.top - 8.dp.toPx()) }
            val down = below >= with(density) { 260.dp.toPx() } || below >= above
            val maxH = with(density) { maxOf(120.dp.toPx(), minOf(screenH.toPx() * 0.6f, if (down) below else above)) }
            val minW = maxOf(with(density) { anchor.width.toDp() }, 150.dp)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, curIdx - 3))
            LaunchedEffect(Unit) {
                if (curIdx >= 0) listState.scrollToItem(curIdx, -(maxH / 2f - with(density) { itemH.toPx() } / 2f).toInt().coerceAtLeast(0))
            }
            Popup(
                popupPositionProvider = remember(density) { DropdownPos(with(density) { 4.dp.roundToPx() }, with(density) { 8.dp.roundToPx() }, with(density) { 260.dp.roundToPx() }) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                val pshape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .widthIn(min = minW)
                        .heightIn(max = with(density) { maxH.toDp() })
                        .shadow(8.dp, pshape)
                        .clip(pshape)
                        .background(c.bg)
                        .border(BorderStroke(1.dp, c.line), pshape)
                        .padding(4.dp)
                ) {
                    LazyColumn(state = listState) {
                        itemsIndexed(options, key = { i, o -> "$i|${o.value}" }) { i, o ->
                            val sel = i == curIdx
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = itemH)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) c.panel2 else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(role = Role.Button) { open = false; onChange(o.value) }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    o.label, color = if (sel) c.pri else c.fg, fontSize = 15.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f)
                                )
                                if (sel) ViewIcon("check", size = 16.dp, tint = c.pri)
                            }
                        }
                    }
                }
            }
        }
    }
}

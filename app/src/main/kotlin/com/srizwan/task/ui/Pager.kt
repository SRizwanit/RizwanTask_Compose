package com.srizwan.task.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import kotlinx.coroutines.launch

/**
 * Shared pagination: every long list (admin books / users / review / payments / log / notices / dashboard,
 * Task books / payment history ...) shows [PAGE_SIZE] rows per page with a vector page bar underneath.
 * No emoji, no text arrows - the buttons use the app's own vector icons (first / left / right / last).
 */
const val PAGE_SIZE = 20

/** Number of pages for [total] items (at least 1). */
fun pageCountOf(total: Int, size: Int = PAGE_SIZE): Int = if (total <= 0) 1 else (total + size - 1) / size

/** The items of the 0-based [page] (the page number is clamped, so a shrinking list never shows an empty page). */
fun <T> List<T>.pageSlice(page: Int, size: Int = PAGE_SIZE): List<T> {
    if (isEmpty()) return this
    val p = page.coerceIn(0, pageCountOf(this.size, size) - 1)
    return subList(p * size, minOf(this.size, (p + 1) * size))
}

/** If a screen scrolls its content in one [ScrollState], it provides it here so that a page change jumps back to the top. */
val LocalPagerScroll = compositionLocalOf<ScrollState?> { null }

@Composable
fun ProvidePagerScroll(state: ScrollState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPagerScroll provides state) { content() }
}

/** One round-cornered page button: either a vector icon or a (Bengali) number. */
@Composable
private fun PgBtn(
    icon: String? = null, label: String? = null, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
            .clip(shape)
            .background(if (on) cs.primary else cs.surface.copy(alpha = 0.72f))
            .background(if (on) androidx.compose.ui.graphics.Color.Transparent else cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, if (on) cs.primary else cs.outline.copy(alpha = 0.6f), shape)
            .hapticClickable(enabled = enabled && !on, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (on) cs.onPrimary else cs.onSurface
        if (icon != null) AppIcon(icon, size = 18.dp, tint = tint)
        if (label != null) Text(label, color = tint, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/** The page numbers to show: first, last, and a window around the current page; null = a gap ("...") drawn as three vector dots. */
private fun windowOf(page: Int, pages: Int): List<Int?> {
    if (pages <= 7) return (0 until pages).toList()
    val out = ArrayList<Int?>()
    val from = maxOf(1, page - 1)
    val to = minOf(pages - 2, page + 1)
    out.add(0)
    if (from > 1) out.add(null)
    for (i in from..to) out.add(i)
    if (to < pages - 2) out.add(null)
    out.add(pages - 1)
    return out
}

@Composable
private fun GapDots() {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { Box(Modifier.defaultMinSize(minWidth = 3.dp, minHeight = 3.dp).clip(RoundedCornerShape(99.dp)).background(c)) }
    }
}

/**
 * Page bar for a list that is fully loaded: [total] items, 0-based [page]. Hidden when everything fits on one page.
 * Shows "1-20 / 125" style caption, first / previous / numbers / next / last.
 */
@Composable
fun PagerBar(page: Int, total: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier, size: Int = PAGE_SIZE) {
    val pages = pageCountOf(total, size)
    if (pages <= 1) return
    val cs = MaterialTheme.colorScheme
    val p = page.coerceIn(0, pages - 1)
    val scroll = LocalPagerScroll.current
    val scope = rememberCoroutineScope()
    fun go(n: Int) {
        onPage(n.coerceIn(0, pages - 1))
        if (scroll != null) scope.launch { scroll.animateScrollTo(0) }
    }
    val a = p * size + 1
    val b = minOf(total, (p + 1) * size)
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${Fmt.bn(a)}-${Fmt.bn(b)} / মোট ${Fmt.bn(total)}টি • পেইজ ${Fmt.bn(p + 1)}/${Fmt.bn(pages)}",
            color = cs.onSurfaceVariant, fontSize = 12.sp
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PgBtn(icon = "first", enabled = p > 0) { go(0) }
            PgBtn(icon = "left", enabled = p > 0) { go(p - 1) }
            windowOf(p, pages).forEach { n ->
                if (n == null) GapDots() else PgBtn(label = Fmt.bn(n + 1), on = n == p) { go(n) }
            }
            PgBtn(icon = "right", enabled = p < pages - 1) { go(p + 1) }
            PgBtn(icon = "last", enabled = p < pages - 1) { go(pages - 1) }
        }
    }
}

/**
 * Page bar for a list that is loaded from the server one page at a time (total unknown): previous / current / next.
 * [hasNext] = the server returned more than one page of rows. Hidden on the first page when there is no next page.
 */
@Composable
fun PagerBarOpen(page: Int, hasNext: Boolean, onPage: (Int) -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    if (page <= 0 && !hasNext) return
    val cs = MaterialTheme.colorScheme
    val scroll = LocalPagerScroll.current
    val scope = rememberCoroutineScope()
    fun go(n: Int) {
        onPage(maxOf(0, n))
        if (scroll != null) scope.launch { scroll.animateScrollTo(0) }
    }
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("পেইজ ${Fmt.bn(page + 1)}", color = cs.onSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PgBtn(icon = "first", enabled = page > 0 && !busy) { go(0) }
            PgBtn(icon = "left", enabled = page > 0 && !busy) { go(page - 1) }
            PgBtn(label = Fmt.bn(page + 1), on = true) { }
            PgBtn(icon = "right", enabled = hasNext && !busy) { go(page + 1) }
        }
    }
}

package com.srizwan.task.screens.task

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.str
import com.srizwan.task.core.BookSum
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.Spinner
import com.srizwan.task.ui.goldColor
import com.srizwan.task.ui.okColor
import org.json.JSONObject

/**
 * React task/BookGrid.jsx + the search row of Task.jsx (3-খ-২): the search box with the refresh button, and the grid of
 * book cards (cover, done / remaining pages with a progress bar, rate, "শেষ কাজ" tag) with a loading skeleton and the
 * empty / failed messages. All icons are vectors.
 */

/** `div.row` with the search input (search icon inside) and the `#bReload` refresh button. */
@Composable
fun TaskSearchRow(query: String, onQuery: (String) -> Unit, onReload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppField(
            value = query, onValueChange = onQuery, placeholder = "কিতাব বা লেখকের নাম খুঁজুন",
            modifier = Modifier.weight(1f), leadingIcon = "search"
        )
        Box(
            Modifier.clip(shape)
                .background(cs.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
                .clickable(onClick = onReload)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) { AppIcon("refresh", size = 18.dp, tint = cs.onSurface) }
    }
}

/**
 * React `BookGrid`. [books] = the books to show (already without hidden ones); [status] = loading | ready | error;
 * [byBook] = my pages per book (`sum.byBook`); [resume] = last book on this device.
 */
@Composable
fun BookGrid(
    status: String,
    spinning: Boolean,
    books: List<JSONObject>,
    query: String,
    bookCounts: Map<String, Int>,
    byBook: Map<String, BookSum>,
    resume: ResumeRec?,
    rateOf: (JSONObject) -> Double,
    onOpen: (String) -> Unit
) {
    val q = query.trim().lowercase()
    val list = books.filter { b ->
        q.isEmpty() || (b.str("name") ?: "").lowercase().contains(q) || (b.str("author") ?: "").lowercase().contains(q)
    }
    val skeleton = books.isEmpty() && status == "loading"
    val failed = books.isEmpty() && status == "error"
    val showEmpty = !skeleton && (failed || list.isEmpty())

    Spinner(spinning)
    // .grid: repeat(auto-fill, minmax(150px, 1fr)), gap 14
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(1, ((maxWidth.value + 14f) / (150f + 14f)).toInt())
        // null = a skeleton placeholder, otherwise a book
        val cells: List<JSONObject?> = if (skeleton) List(4) { null } else if (failed) emptyList() else list
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            cells.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { b ->
                        val m = Modifier.weight(1f).fillMaxHeight()
                        if (b == null) SkeletonCard(m)
                        else {
                            val id = b.opt("id")?.toString() ?: ""
                            BookCard(
                                b, done = bookCounts[id] ?: 0, total = (b.finiteNum("total_pages") ?: 0.0).toInt(),
                                mine = byBook[id]?.n ?: 0, last = resume != null && resume.b == id, rate = rateOf(b),
                                modifier = m, onClick = { onOpen(id) }
                            )
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (showEmpty) {
        Text(
            if (failed) "কিতাব লোড হয়নি — নেট দেখে রিফ্রেশ চাপুন" else "কোনো কিতাব পাওয়া যায়নি",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 40.dp)
        )
    }
}

/** `button.book` — cover on top (3:4), then name, author, progress and rate. */
@Composable
private fun BookCard(b: JSONObject, done: Int, total: Int, mine: Int, last: Boolean, rate: Double, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape)
            .background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
    ) {
        // .cv
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Cover(b.str("cover_url"))
            if (last) {
                Text(
                    "শেষ কাজ", color = Color(0xFF4A3200), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFA8761F), Color(0xFFF2D27A))))
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
        }
        // .bt
        Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(b.str("name") ?: "", color = cs.onSurface, fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(b.str("author") ?: "", color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 19.sp)
            Box(Modifier.padding(top = 1.dp)) { Progress(done, total) }
            Text(
                "রেট ${rateTxt(rate)}/পেইজ" + (if (mine > 0) " • আমার ${Fmt.bn(mine)}" else ""),
                color = goldColor(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** React `Cover`: the cover image, or a book icon when there is no URL / it fails to load. */
@Composable
private fun Cover(url: String?) {
    var bad by remember(url) { mutableStateOf(false) }
    if (url.isNullOrEmpty() || bad) {
        AppIcon("book", size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            onError = { bad = true }, modifier = Modifier.fillMaxSize()
        )
    }
}

/** React `Progress`: bar + "N সম্পন্ন / M বাকি"; with no total page count "বাকি অজানা"; nothing when nothing is done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Progress(done: Int, total: Int) {
    val cs = MaterialTheme.colorScheme
    val ok = okColor()
    val gold = goldColor()
    if (total > 0) {
        val rem = maxOf(0, total - done)
        val pct = minOf(1f, done.toFloat() / total)
        Column {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(cs.surfaceVariant)) {
                if (pct > 0f) Box(Modifier.fillMaxWidth(pct).height(8.dp).background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary))))
            }
            FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.Center) {
                Text("${Fmt.bn(done)} সম্পন্ন", color = ok, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text("/", color = cs.onSurfaceVariant, fontSize = 12.5.sp)
                Text("${Fmt.bn(rem)} বাকি", color = gold, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else if (done > 0) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.Center) {
            Text("${Fmt.bn(done)} সম্পন্ন", color = ok, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text("/", color = cs.onSurfaceVariant, fontSize = 12.5.sp)
            Text("বাকি অজানা", color = cs.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** `.skl` shimmer (kpSk): a light band sliding across the panel colour. */
@Composable
internal fun Modifier.shimmer(): Modifier {
    val cs = MaterialTheme.colorScheme
    val t by rememberInfiniteTransition(label = "skl").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing), RepeatMode.Restart), label = "sklT"
    )
    val a = cs.surfaceVariant
    val b = cs.surface
    return this.background(
        Brush.horizontalGradient(
            listOf(a, b, a),
            startX = -400f + 1200f * t, endX = 400f + 1200f * t
        )
    )
}

/** `.book.sk`: a card-shaped placeholder shown while the first load runs. */
@Composable
private fun SkeletonCard(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(cs.surface.copy(alpha = 0.94f)).border(1.dp, cs.outline.copy(alpha = 0.6f), shape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).shimmer())
        Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth().padding(top = 0.dp).height(8.dp).clip(RoundedCornerShape(99.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

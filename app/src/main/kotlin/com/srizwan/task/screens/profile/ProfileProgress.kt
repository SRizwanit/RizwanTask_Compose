package com.srizwan.task.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.finiteNum
import com.srizwan.task.screens.task.TaskData
import com.srizwan.task.ui.PagerBar
import com.srizwan.task.ui.pageSlice
import org.json.JSONObject

/**
 * React Profile.jsx `ProfileProg` part (`#profProg`): "আমার কাজ — কিতাব অনুযায়ী" — one block per book I worked on:
 * the book name, "আমার N পেইজ" and a [ProgLine]. Shown only when I have at least one proofed page. The done count is the
 * book's overall count from `rpc/book_proof_counts` ([TaskData.bookCounts]) when known, else my own pages of it.
 */
@Composable
fun ProfileProgress(data: TaskData, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    // pagination: 20 books per page
    var page by remember { mutableStateOf(0) }
    val sum = data.sum ?: return
    if (sum.byBook.isEmpty()) return
    val entries = sum.byBook.entries.toList()
    Column(modifier.fillMaxWidth()) {
        Text("আমার কাজ — কিতাব অনুযায়ী", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        entries.pageSlice(page).forEach { (id, x) ->
            val b = data.booksById[id]
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        b?.optString("name")?.takeIf { it.isNotEmpty() } ?: "কিতাব", color = cs.onSurface, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text("আমার ${Fmt.bn(x.n)} পেইজ", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
                ProgLine(b, data.bookCounts[id] ?: x.n)
            }
        }
        PagerBar(page = page, total = entries.size, onPage = { page = it })
    }
}

/**
 * React `ProgLine`: with a known total (`total_pages` > 0) a bar plus "সম্পন্ন N / M (P%)" and "বাকি R";
 * otherwise only "মোট সম্পন্ন N পেইজ".
 */
@Composable
fun ProgLine(book: JSONObject?, done: Int) {
    val cs = MaterialTheme.colorScheme
    val total = book?.finiteNum("total_pages")?.toInt() ?: 0
    if (total > 0) {
        val pct = minOf(100.0, done.toDouble() / total * 100)
        val left = maxOf(0, total - done)
        Column(Modifier.fillMaxWidth()) {
            // .pg > i
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(cs.surfaceVariant)) {
                if (pct > 0) Box(Modifier.fillMaxWidth((pct / 100).toFloat()).height(8.dp).background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary))))
            }
            // .pgt
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("সম্পন্ন ${Fmt.bn(done)} / ${Fmt.bn(total)} (${Fmt.bn(Math.round(pct))}%)", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text("বাকি ${Fmt.bn(left)}", color = cs.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    } else {
        Text("মোট সম্পন্ন ${Fmt.bn(done)} পেইজ", color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

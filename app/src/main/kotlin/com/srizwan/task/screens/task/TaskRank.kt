package com.srizwan.task.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import com.srizwan.task.screens.admin.Chips
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.Spinner
import com.srizwan.task.ui.goldColor
import kotlinx.coroutines.CancellationException

/**
 * React task/RankPane.jsx + Task.jsx `loadRank` (3-ঘ): the leaderboard tab — today / this week / all time, my own place.
 * Table / RPC (rizwantask_full_schema.sql): `rpc/leaderboard(p_since timestamptz)` -> (user_id text, n bigint), top 200,
 * "correction" pages excluded; fallback: count `page_proofs` rows per user; names from `app_users(id, full_name)`.
 */

class RankRow(val id: String, val n: Int)

/** React `rank.data`: the rows of one [period] (only n > 0, best first) and the names by user id. */
class RankResult(val period: String, val rows: List<RankRow>, val names: Map<String, String>)

/** React `rankP`, `rank`, `rankCache`, `rankReq` + `loadRank` / `pickPeriod`. */
class RankState(private val data: TaskData) {
    var period by mutableStateOf("day")
    var loading by mutableStateOf(false); private set
    var result by mutableStateOf<RankResult?>(null); private set
    var error by mutableStateOf(""); private set

    private class Cached(val t: Long, val rows: List<Pair<String, Double>>, val names: Map<String, String>)
    private val cache = HashMap<String, Cached>()   // 60 seconds per period
    private var req = 0                              // a quick tab change: the old answer must not cover the newer one

    /** React `pickPeriod` (the caller launches [load]). */
    fun pick(k: String) { period = k }

    suspend fun load(p: String) {
        if (data.me == null) return
        val token = ++req
        loading = true
        try {
            val since = when (p) {
                "day" -> Fmt.startOfDay()
                "week" -> Fmt.startOfDay().minusDays(6)
                else -> null
            }
            val c = cache[p]
            val rows: List<Pair<String, Double>>
            val names: Map<String, String>
            if (c != null && System.currentTimeMillis() - c.t < 60000) { rows = c.rows; names = c.names }
            else {
                val iso = since?.toInstant()?.toString()
                rows = try {
                    Sb.post("rpc/leaderboard", jsonOf("p_since" to iso)).objects()
                        .map { (it.opt("user_id")?.toString() ?: "null") to (it.finiteNum("n") ?: 0.0) }
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    var all: List<org.json.JSONObject>
                    val f = if (iso != null) "&created_at=gte.${enc(iso)}" else ""
                    try {
                        all = Sb.getAll("page_proofs?select=user_id,review_status,book_id,stem$f&order=user_id.asc,book_id.asc,stem.asc")
                        all = all.filter { it.str("review_status") != "correction" }
                    } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) {
                        all = Sb.getAll("page_proofs?select=user_id,book_id,stem&order=user_id.asc,book_id.asc,stem.asc")
                    }
                    val m = LinkedHashMap<String, Int>()
                    for (r in all) { val k = r.opt("user_id")?.toString() ?: "null"; m[k] = (m[k] ?: 0) + 1 }
                    m.entries.map { it.key to it.value.toDouble() }.sortedByDescending { it.second }
                }
                val nm = LinkedHashMap<String, String>()
                for (u in Sb.get("app_users?select=id,full_name&limit=5000").objects()) nm[u.opt("id")?.toString() ?: ""] = u.str("full_name") ?: ""
                names = nm
                cache[p] = Cached(System.currentTimeMillis(), rows, names)
            }
            val list = rows.filter { it.second > 0 }.map { RankRow(it.first, it.second.toInt()) }
            if (token != req) return
            result = RankResult(p, list, names); loading = false; error = ""
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (token != req) return
            loading = false; error = e.message ?: ""
        }
    }
}

private val LABEL = mapOf("day" to "আজ", "week" to "এই সপ্তাহে", "all" to "সব মিলিয়ে")
private val CHIPS = listOf("day" to "আজ", "week" to "এই সপ্তাহ", "all" to "সব সময়")

/** React `RankPane` (section#pRank, max width 520, centred). [meId] = my user id. */
@Composable
fun RankPane(rank: RankState, meId: String, onPeriod: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val d = rank.result
    var myRank = ""
    if (d != null) {
        val idx = d.rows.indexOfFirst { it.id == meId }
        val lab = LABEL[d.period] ?: ""
        myRank = when {
            idx < 0 -> "$lab আপনার কোনো পেইজ নেই — একটি পেইজ প্রুফ করলেই তালিকায় আসবেন!"
            idx == 0 -> "$lab আপনি ১ নম্বরে! মাশাআল্লাহ, এগিয়ে থাকুন।"
            else -> "$lab আপনার অবস্থান ${Fmt.bn(idx + 1)} নম্বর • পরের জনের থেকে ${Fmt.bn(d.rows[idx - 1].n - d.rows[idx].n)} পেইজ পিছনে"
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 20.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // .av: gold-ringed round badge with the trophy
                    Box(Modifier.size(82.dp).border(2.dp, goldColor(), CircleShape).padding(3.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFA8761F), Color(0xFFF2D27A)))),
                            contentAlignment = Alignment.Center
                        ) { AppIcon("trophy", size = 30.dp, tint = Color(0xFF4A3200)) }
                    }
                    Text("লিডারবোর্ড", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Text(myRank, color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Chips(CHIPS, rank.period, onPeriod)
            }
            Spinner(rank.loading)
            // #rankList
            if (rank.error.isNotEmpty()) RankEmpty("লিডারবোর্ড আনা যায়নি: ${rank.error}")
            else if (d != null) {
                if (d.rows.isEmpty()) RankEmpty("এই সময়ে এখনো কেউ কোনো পেইজ প্রুফড করেননি — প্রথম হওয়ার সুযোগ আপনার!")
                else {
                    val idx = d.rows.indexOfFirst { it.id == meId }
                    d.rows.take(20).forEachIndexed { i, r -> RankItem(r, i, meId, d.names) }
                    if (idx >= 20) {
                        Text("⋮", color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        RankItem(d.rows[idx], idx, meId, d.names)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankEmpty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 40.dp))
}

/** `.lbr`: rank circle (gold / silver / bronze for the top three), name, page count; my own row has the gold edge. */
@Composable
private fun RankItem(r: RankRow, i: Int, meId: String, names: Map<String, String>) {
    val cs = MaterialTheme.colorScheme
    val mine = r.id == meId
    val shape = RoundedCornerShape(12.dp)
    val gold = goldColor()
    val (rkBg, rkFg) = when (i) {
        0 -> Color(0xFFF2D27A) to Color(0xFF5A3D00)
        1 -> Color(0xFFD9DDE3) to Color(0xFF333333)
        2 -> Color(0xFFE3B98A) to Color(0xFF5A3300)
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape)
            .background(if (mine) Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.09f), gold.copy(alpha = 0.16f))) else Brush.linearGradient(listOf(cs.background, cs.background)))
            .border(1.dp, if (mine) gold else cs.outline.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(rkBg), contentAlignment = Alignment.Center) {
            Text(Fmt.bn(i + 1), color = rkFg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(
            (names[r.id]?.takeIf { it.isNotEmpty() } ?: "ইউজার") + (if (mine) " (আপনি)" else ""),
            color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(Fmt.bn(r.n) + " পেইজ", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

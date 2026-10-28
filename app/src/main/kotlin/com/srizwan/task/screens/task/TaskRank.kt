package com.srizwan.task.screens.task

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.srizwan.task.ui.EmptyArt
import com.srizwan.task.ui.Spinner
import com.srizwan.task.ui.glass
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
    /** Weekly / monthly reward badges (TaskRewards.kt); empty until loaded or when the table does not exist yet. */
    var rewards by mutableStateOf<List<RewardBadge>>(emptyList()); private set

    private class Cached(val t: Long, val rows: List<Pair<String, Double>>, val names: Map<String, String>)
    private val cache = HashMap<String, Cached>()   // 60 seconds per period
    private var req = 0                              // a quick tab change: the old answer must not cover the newer one

    /** React `pickPeriod` (the caller launches [load]). */
    fun pick(k: String) { period = k }

    /** [force] = pull-to-refresh: ignore the 60 second cache and ask the server again. */
    suspend fun load(p: String, force: Boolean = false) {
        if (data.me == null) return
        val token = ++req
        loading = true
        try {
            val since = when (p) {
                "day" -> Fmt.startOfDay()
                "week" -> Fmt.startOfDay().minusDays(6)
                "month" -> Fmt.startOfDay().withDayOfMonth(1)
                else -> null
            }
            val c = cache[p]
            val rows: List<Pair<String, Double>>
            val names: Map<String, String>
            if (!force && c != null && System.currentTimeMillis() - c.t < 60000) { rows = c.rows; names = c.names }
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
            rewards = Rewards.fetch(rewards, force)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (token != req) return
            loading = false; error = e.message ?: ""
        }
    }
}

private val LABEL = mapOf("day" to "আজ", "week" to "এই সপ্তাহে", "month" to "এই মাসে", "all" to "সব মিলিয়ে")
private val CHIPS = listOf("day" to "আজ", "week" to "এই সপ্তাহ", "month" to "এই মাস", "all" to "সব সময়")

/** Podium block heights for 1st / 2nd / 3rd (also used by the loading skeleton, TaskSkeletons.kt). */
internal val PODIUM_H = listOf(84.dp, 64.dp, 48.dp)

/** Podium colours for 1st / 2nd / 3rd: (top, bottom, text on it) — gold, silver, bronze. */
internal val PODIUM_METAL = listOf(
    Triple(Color(0xFFF2D27A), Color(0xFFA8761F), Color(0xFF5A3D00)),
    Triple(Color(0xFFE6E9EE), Color(0xFFA3AAB4), Color(0xFF333333)),
    Triple(Color(0xFFE3B98A), Color(0xFF9A6232), Color(0xFF5A3300))
)

/**
 * React `RankPane` (section#pRank, max width 520, centred). [meId] = my user id.
 * [pulling] = a pull-to-refresh is running (its own indicator is shown, so the small spinner stays hidden).
 * The first three places stand on the podium card; the list below continues from place 4.
 */
@Composable
fun RankPane(rank: RankState, meId: String, onPeriod: (String) -> Unit, pulling: Boolean = false) {
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
            RewardsCard(rank.rewards, d?.names ?: emptyMap(), meId)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // the filter chips sit on a glass pill, so they stay readable over the background
                Box(Modifier.glass(RoundedCornerShape(99.dp)).padding(5.dp)) { Chips(CHIPS, rank.period, onPeriod) }
            }
            // breathing room between the filter chips and the list below
            Spacer(Modifier.height(16.dp))
            // first load: the same kind of skeleton as the other tabs; later loads: the small spinner (not during a pull)
            if (d == null && rank.error.isEmpty()) RankSkeleton() else Spinner(rank.loading && !pulling)
            // #rankList
            if (rank.error.isNotEmpty()) RankEmpty("লিডারবোর্ড আনা যায়নি: ${rank.error}", "cloud")
            else if (d != null) {
                if (d.rows.isEmpty()) RankEmpty("এই সময়ে এখনো কেউ কোনো পেইজ প্রুফড করেননি — প্রথম হওয়ার সুযোগ আপনার!", "trophy")
                else {
                    val idx = d.rows.indexOfFirst { it.id == meId }
                    val bc = rank.rewards.groupingBy { it.userId }.eachCount()
                    PodiumCard(d.rows.take(3), meId, d.names, bc)
                    d.rows.take(20).forEachIndexed { i, r -> if (i >= 3) RankItem(r, i, meId, d.names, bc[r.id] ?: 0) }
                    if (idx >= 20) {
                        Text("⋮", color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        RankItem(d.rows[idx], idx, meId, d.names, bc[d.rows[idx].id] ?: 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankEmpty(text: String, art: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.glass(RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmptyArt(art, size = 76.dp)
            Text(
                text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/** The podium card: 2nd on the left, 1st in the middle (tallest), 3rd on the right. Missing places stay as empty blocks. */
@Composable
private fun PodiumCard(top: List<RankRow>, meId: String, names: Map<String, String>, badges: Map<String, Int>) {
    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { i -> PodiumSlot(i, top.getOrNull(i), meId, names, Modifier.weight(1f), top.getOrNull(i)?.let { badges[it.id] } ?: 0) }
        }
    }
}

/** One place of the podium ([i] = 0 for 1st): round avatar with the first letter, name, page count and the block that grows up. */
@Composable
private fun PodiumSlot(i: Int, r: RankRow?, meId: String, names: Map<String, String>, modifier: Modifier, badges: Int = 0) {
    val cs = MaterialTheme.colorScheme
    val (topC, bottomC, fg) = PODIUM_METAL[i]
    val mine = r != null && r.id == meId
    val name = if (r == null) "—" else (names[r.id]?.takeIf { it.isNotEmpty() } ?: "ইউজার")
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val h by animateDpAsState(
        targetValue = if (go) PODIUM_H[i] else 0.dp,
        animationSpec = tween(650, delayMillis = when (i) { 0 -> 250; 1 -> 120; else -> 0 }, easing = FastOutSlowInEasing),
        label = "podiumH"
    )
    val avatar = if (i == 0) 56.dp else 46.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (i == 0) AppIcon("trophy", size = 18.dp, tint = goldColor(), modifier = Modifier.padding(bottom = 4.dp))
        Box(
            Modifier.size(avatar).border(2.dp, if (mine) cs.primary else topC, CircleShape).padding(3.dp)
                .clip(CircleShape).background(Brush.linearGradient(listOf(topC, bottomC))),
            contentAlignment = Alignment.Center
        ) {
            Text(if (r == null) "?" else firstGrapheme(name), color = fg, fontWeight = FontWeight.Bold, fontSize = if (i == 0) 20.sp else 17.sp)
        }
        Text(
            name, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
        )
        if (mine) Text("(আপনি)", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
        BadgeCount(badges)
        Text(
            if (r == null) "—" else Fmt.bn(r.n) + " পেইজ", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(h).alpha(if (r == null) 0.35f else 1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Brush.verticalGradient(listOf(topC, bottomC))),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(Fmt.bn(i + 1), color = fg, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The first letter (a whole Bengali letter with its marks, not half a character) for the avatar. */
private fun firstGrapheme(s: String): String {
    if (s.isEmpty()) return "?"
    return try {
        val bi = java.text.BreakIterator.getCharacterInstance()
        bi.setText(s)
        val end = bi.next()
        if (end > 0) s.substring(0, end) else s.take(1)
    } catch (e: Exception) { s.take(1) }
}

/** `.lbr`: rank circle (gold / silver / bronze for the top three), name, page count; my own row has the gold edge. */
@Composable
private fun RankItem(r: RankRow, i: Int, meId: String, names: Map<String, String>, badges: Int = 0) {
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
            .background(cs.background)
            .background(if (mine) Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.09f), gold.copy(alpha = 0.16f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
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
            modifier = Modifier.weight(1f, fill = false)
        )
        BadgeCount(badges)
        Box(Modifier.weight(1f))
        Text(Fmt.bn(r.n) + " পেইজ", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

package com.srizwan.task.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import com.srizwan.task.screens.admin.Chips
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.goldColor
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * Weekly / monthly reward badges on the rank tab.
 * Table + RPC (rizwantask_full_schema.sql, section 10-খ): `reward_badges(period_type week|month, period_start date, user_id, place, pages)`,
 * `rpc/award_rewards()` — gives the top 3 of every finished week (Saturday to Friday) and month their badge (safe to call again and again;
 * it also writes a `reward` notice for each winner, which becomes a push notification). A missing table / function is ignored.
 */
class RewardBadge(val userId: String, val type: String, val start: String, val place: Int, val pages: Int)

object Rewards {
    /** Asks the server to hand out due badges (at most once in 5 minutes), then reads the newest 500. On a failure [old] is kept. */
    private var awardedAt = 0L

    suspend fun fetch(old: List<RewardBadge>, force: Boolean = false): List<RewardBadge> {
        try {
            val now = System.currentTimeMillis()
            if (force || now - awardedAt > 300000L) {
                try { Sb.post("rpc/award_rewards", jsonOf()) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* no function yet */ }
                awardedAt = now
            }
            return Sb.get("reward_badges?select=user_id,period_type,period_start,place,pages&order=period_start.desc,place.asc&limit=500")
                .objects().mapNotNull {
                    val uid = it.str("user_id") ?: return@mapNotNull null
                    RewardBadge(uid, it.str("period_type") ?: "week", it.str("period_start") ?: "", it.optInt("place", 0), it.optInt("pages", 0))
                }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { return old }
    }
}

private val MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
private val PLACE = mapOf(1 to "১ম", 2 to "২য়", 3 to "৩য়")

/** "১৭ – ২৩ অক্টোবর" for a week starting [start] (yyyy-MM-dd), "অক্টোবর ২০২৬" for a month. */
private fun periodLabel(type: String, start: String): String {
    val d = try { LocalDate.parse(start.take(10)) } catch (e: Exception) { return "" }
    if (type == "month") return MONTHS[d.monthValue - 1] + " " + Fmt.bn(d.year).replace(",", "")
    val e = d.plusDays(6)
    return if (e.monthValue == d.monthValue) Fmt.bn(d.dayOfMonth) + " – " + Fmt.bn(e.dayOfMonth) + " " + MONTHS[e.monthValue - 1]
    else Fmt.bn(d.dayOfMonth) + " " + MONTHS[d.monthValue - 1] + " – " + Fmt.bn(e.dayOfMonth) + " " + MONTHS[e.monthValue - 1]
}

/** The rank tab card: winners of the last finished week / month, and my own badges. */
@Composable
fun RewardsCard(rewards: List<RewardBadge>, names: Map<String, String>, meId: String) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    var kind by remember { mutableStateOf("week") }
    val ofKind = rewards.filter { it.type == kind }
    val latest = ofKind.maxOfOrNull { it.start }
    val winners = ofKind.filter { it.start == latest }.sortedBy { it.place }
    val mine = rewards.filter { it.userId == meId }.sortedWith(compareByDescending<RewardBadge> { it.start }.thenBy { it.place })

    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon("star", size = 18.dp, tint = gold)
                Text("রিওয়ার্ড ব্যাজ", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Box(Modifier.padding(top = 10.dp)) { Chips(listOf("week" to "সাপ্তাহিক", "month" to "মাসিক"), kind) { kind = it } }
            if (winners.isEmpty()) {
                Text(
                    "এখনো কোনো ব্যাজ দেওয়া হয়নি — প্রতি সপ্তাহের (শনি–শুক্র) ও প্রতি মাসের সেরা ৩ জন রিওয়ার্ড ব্যাজ পান।",
                    color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                Text(
                    (if (kind == "week") "গত সপ্তাহের বিজয়ী • " else "গত মাসের বিজয়ী • ") + periodLabel(kind, latest ?: ""),
                    color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                winners.forEach { w ->
                    val m = PODIUM_METAL[(w.place - 1).coerceIn(0, 2)]
                    val me = w.userId == meId
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (me) gold else cs.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(m.first, m.second))),
                            contentAlignment = Alignment.Center
                        ) { Text(PLACE[w.place] ?: Fmt.bn(w.place), color = m.third, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        Text(
                            (names[w.userId]?.takeIf { it.isNotEmpty() } ?: "ইউজার") + (if (me) " (আপনি)" else ""),
                            color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Text(Fmt.bn(w.pages) + " পেইজ", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Text("আমার ব্যাজ", color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            if (mine.isEmpty()) {
                Text("এখনো কোনো ব্যাজ নেই — সেরা ৩-এ থাকলে পরের হিসাবে পেয়ে যাবেন।", color = cs.onSurfaceVariant, fontSize = 12.sp)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mine.forEach { b ->
                        val m = PODIUM_METAL[(b.place - 1).coerceIn(0, 2)]
                        Row(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(listOf(m.first, m.second)))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AppIcon(if (b.type == "month") "trophy" else "star", size = 13.dp, tint = m.third)
                            Text(
                                (if (b.type == "month") "মাসিক " else "সাপ্তাহিক ") + (PLACE[b.place] ?: Fmt.bn(b.place)) + " • " + periodLabel(b.type, b.start),
                                color = m.third, fontWeight = FontWeight.Bold, fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small star + count next to a name on the leaderboard (nothing when [n] is 0). */
@Composable
fun BadgeCount(n: Int) {
    if (n <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        AppIcon("star", size = 12.dp, tint = goldColor())
        Text(Fmt.bn(n), color = goldColor(), fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

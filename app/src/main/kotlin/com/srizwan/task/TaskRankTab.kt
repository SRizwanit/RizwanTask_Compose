package com.srizwan.task

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * ---------------------------------------------------------------------------
 *  "র‍্যাংক" tab  (React: task/RankPane.jsx)
 *   leaderboard for today / this week / all time, and my own position
 * ---------------------------------------------------------------------------
 */
@Composable
fun RankTab(vm: TaskViewModel, user: AppUser, scroll: ScrollState) {
    val colors = MaterialTheme.colorScheme
    val data = vm.rank
    val rows = data.rows
    val meId = user.id
    val idx = rows.indexOfFirst { it.id == meId }
    val lab = data.period?.label ?: ""

    val myRank = when {
        data.period == null -> ""
        idx < 0 -> "$lab আপনার কোনো পেইজ নেই — একটি পেইজ প্রুফ করলেই তালিকায় আসবেন!"
        idx == 0 -> "$lab আপনি ১ নম্বরে! মাশাআল্লাহ, এগিয়ে থাকুন।"
        else -> "$lab আপনার অবস্থান ${bn(idx + 1)} নম্বর • পরের জনের থেকে ${bn(rows[idx - 1].n - rows[idx].n)} পেইজ পিছনে"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SoftCard {
            Text(
                "🏆",
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "লিডারবোর্ড",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (myRank.isNotEmpty()) {
                Hint(myRank, Modifier.fillMaxWidth())
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            RankPeriod.entries.forEach { p ->
                FilterChip(
                    selected = vm.rankPeriod == p,
                    onClick = { vm.pickPeriod(p) },
                    label = { Text(p.chip) }
                )
            }
        }

        if (data.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        when {
            data.error.isNotEmpty() -> EmptyNote("লিডারবোর্ড আনা যায়নি: ${data.error}")
            data.period == null -> Unit
            rows.isEmpty() -> EmptyNote("এই সময়ে এখনো কেউ কোনো পেইজ প্রুফড করেননি — প্রথম হওয়ার সুযোগ আপনার!")
            else -> {
                rows.take(20).forEachIndexed { i, r -> RankRowItem(r, i, meId) }
                if (idx >= 20) {
                    Text(
                        "⋮",
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RankRowItem(rows[idx], idx, meId)
                }
            }
        }
    }
}

@Composable
private fun RankRowItem(r: RankRow, i: Int, meId: String) {
    val colors = MaterialTheme.colorScheme
    val mine = r.id == meId
    val medal = when (i) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        else -> bn(i + 1)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (mine) colors.primary.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.94f),
        shadowElevation = if (mine) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                medal,
                fontSize = if (i < 3) 20.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp)
            )
            Text(
                (r.name.ifBlank { "ইউজার" }) + if (mine) " (আপনি)" else "",
                fontSize = 15.sp,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${bn(r.n)} পেইজ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
        }
    }
}

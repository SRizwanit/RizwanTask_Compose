package com.srizwan.task.screens.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.goldColor
import com.srizwan.task.ui.okColor
import org.json.JSONObject

/**
 * React task/Dashboard.jsx (3-খ-১): the card on top of the task tab — today's goal (tap = change it), streak in a row,
 * badges, the next badge hint and the "continue" button. Nothing is shown until there is something to show
 * (`#dash:empty{display:none}`). All icons are vectors.
 */
@Composable
fun TaskDashboard(
    ready: Boolean,
    proofs: List<JSONObject>,
    booksById: Map<String, JSONObject>,
    goal: Int,
    resume: ResumeRec?,
    onGoal: () -> Unit,
    onResume: () -> Unit
) {
    val rt = resumeTarget(resume, booksById, proofs)
    val hasStat = ready && (proofs.isEmpty() || proofs[0].has("created_at"))
    if (!hasStat && rt == null) return

    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
        if (hasStat) {
            val s = streakInfo(proofs)   // like React: recalculated on every render (depends on the current time)
            val left = maxOf(0, goal - s.today)
            val hit = s.today >= goal
            val pct = minOf(1f, s.today.toFloat() / goal)
            val next = BADGES.firstOrNull { it.first > s.best }

            // .drow: grid 1.25fr / 1fr, gap 10
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalBox(s.today, goal, pct, hit, left, onGoal, Modifier.weight(1.25f).fillMaxHeight())
                StreakBox(s, Modifier.weight(1f).fillMaxHeight())
            }

            // .dbg: badge row
            BadgeRow(s.best)

            if (next != null) {
                Text(
                    "পরের ব্যাজ \"${next.second}\": টানা ${Fmt.bn(next.first)} দিন (সেরা রেকর্ড থেকে আরও ${Fmt.bn(next.first - s.best)} দিন)।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        if (rt != null) ResumeButton(rt, onResume)
    }
}

/** `.dgoal` — tappable box: label, "N / M পেইজ", progress bar, hint. Green border once the goal is reached. */
@Composable
private fun GoalBox(today: Int, goal: Int, pct: Float, hit: Boolean, left: Int, onGoal: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val ok = okColor()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, if (hit) ok else cs.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onGoal)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BoxLabel("target", "আজকের লক্ষ্য", cs.primary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Fmt.bn(today), color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("/ ${Fmt.bn(goal)} পেইজ", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        // .pg > i
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(cs.surfaceVariant)) {
            if (pct > 0f) Box(
                Modifier.fillMaxWidth(pct).height(8.dp)
                    .background(Brush.horizontalGradient(listOf(cs.primary, if (hit) ok else cs.secondary)))
            )
        }
        // .dh
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hit) AppIcon("check", size = 13.dp, tint = ok)
            Text(
                (if (hit) "লক্ষ্য পূরণ হয়েছে" else "আর ${Fmt.bn(left)} পেইজ বাকি") + " • বদলাতে চাপুন",
                color = if (hit) ok else cs.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
                fontWeight = if (hit) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** `.dstreak` — consecutive days. */
@Composable
private fun StreakBox(s: Streak, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BoxLabel("zap", "টানা কাজ", gold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Fmt.bn(s.cur), color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("দিন", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            (if (s.doneToday) "আজকের দিন গোনা হয়েছে"
            else if (s.cur > 0) "আজ কাজ করলে ${Fmt.bn(s.cur + 1)} দিন হবে"
            else "আজ একটি পেইজ করলেই শুরু") + " • সেরা ${Fmt.bn(s.best)} দিন",
            color = cs.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp
        )
    }
}

/** `.dl` — small coloured label with a vector icon. */
@Composable
private fun BoxLabel(icon: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        AppIcon(icon, size = 14.dp, tint = color)
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** `.dbg` — one pill per badge; earned ones (best >= days) are gold, the rest faded. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(best: Int) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BADGES.forEach { (days, label) ->
            val on = best >= days
            val tint = if (on) gold else cs.onSurfaceVariant
            Row(
                Modifier.clip(CircleShape)
                    .then(
                        if (on) Modifier.background(gold.copy(alpha = 0.2f))
                            .border(BorderStroke(1.dp, gold.copy(alpha = 0.7f)), CircleShape)
                        else Modifier.border(BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f)), CircleShape)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppIcon("trophy", size = 12.dp, tint = tint.copy(alpha = if (on) 1f else 0.7f))
                Text(label, color = tint.copy(alpha = if (on) 1f else 0.7f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** `#dResume` — full-width primary button: "চালিয়ে যান — বইয়ের নাম • পেইজ N". */
@Composable
private fun ResumeButton(rt: ResumeTarget, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape)
            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon("play", size = 16.dp, tint = cs.onPrimary)
        Text(
            "চালিয়ে যান — " + (rt.book.str("name") ?: "") + (if (rt.stem.isNotEmpty()) " • পেইজ ${rt.stem}" else ""),
            color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 8.dp, end = 0.dp)
        )
    }
}

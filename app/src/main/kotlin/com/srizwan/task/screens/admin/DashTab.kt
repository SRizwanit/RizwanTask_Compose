package com.srizwan.task.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.Spinner
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.okColor
import com.srizwan.task.ui.warnColor
import kotlinx.coroutines.launch

/**
 * React admin/DashTab.jsx — 2-ঞ-২: header (title, last refresh time, refresh button), summary tiles and the first two
 * cards (leaderboard, per-user correction rate). All data and calculations come from [DashState] (DashLogic.kt, 2-ঞ-১).
 * 2-ঞ-৩ added: the last card (activity report) and the Dash route in AdminScreen.kt (onReviewUser = vm.reviewUser(uid, true)).
 * [onReviewUser] = open the Review tab for one user (React `onReviewUser`; connected in 2-ঞ-৩).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashTab(vm: AdminViewModel, ask: AskController, openKey: Int, onReviewUser: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val d = vm.dash

    // React: useEffect(load, [openKey]) — every open of the tab reloads the data.
    LaunchedEffect(openKey) { d.load() }

    // ---- header ----
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ড্যাশবোর্ড", color = cs.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (d.atText.isNotEmpty()) Text(d.atText, color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        SmBtn("", "refresh", enabled = !d.loading) { AppScope.scope.launch { d.load() } }
    }

    // ---- summary tiles (2 per row, like the auto-fill grid) ----
    if (d.loading) {
        Spinner(true)
    } else {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t -> Tile(t.n, t.label, Modifier.weight(1f), hl = t.hl) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // ---- card 1: leaderboard ----
    AppCard(Modifier.padding(bottom = 14.dp)) {
        Text("লিডারবোর্ড", color = cs.onSurface, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
            Chips(listOf("day" to "আজ", "week" to "এই সপ্তাহ", "all" to "সব সময়"), d.dP) { d.setDP(it) }
        }
        val lb = d.lb
        if (lb.isEmpty()) {
            Empty("এই সময়ে কোনো প্রুফড পেইজ নেই", pad = true)
        } else {
            lb.forEachIndexed { i, r -> LbRowView(i, vm.uname(r.uid), r.n) }
        }
    }

    // ---- card 2: per-user correction rate ----
    AppCard(Modifier.padding(bottom = 14.dp)) {
        val (wp, wm) = d.limits
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ইউজারভিত্তিক সংশোধনের হার", color = cs.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("সীমা: ${Fmt.bn(wp)}% (কমপক্ষে ${Fmt.bn(wm)}টি রিভিউ)", color = cs.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(
            "রিভিউ হওয়া পেইজের মধ্যে কতটি \"সংশোধন দরকার\" — বেশি ভুলের ইউজার উপরে। সবুজ = অনুমোদিত, লাল = সংশোধন, সোনালি = রিভিউ বাকি।",
            color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        Column(Modifier.padding(bottom = 4.dp)) {
            Chips(listOf("all" to "সবাই", "high" to "সীমার বেশি ভুল", "pend" to "রিভিউ বাকি আছে"), d.crF) { d.setCrF(it) }
        }
        val corr = d.corr
        if (corr.isEmpty()) {
            Empty("এই ফিল্টারে কোনো ইউজার নেই", pad = true)
        } else {
            corr.take(40).forEach { r ->
                val uid = r.u.idStr()
                val hi = d.high(r)
                val pcColor: Color = when {
                    r.pc == null -> cs.onSurface
                    hi -> errColor()
                    r.pc >= wp / 2 -> warnColor()
                    else -> okColor()
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                        .background(cs.background)
                        .border(1.dp, cs.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            r.u.str("full_name").orEmpty(), color = cs.onSurface, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (hi) Badge("সীমার বেশি", "bad")
                        Text(if (r.pc == null) "–" else Fmt.bn(r.pc) + "%", color = pcColor, fontWeight = FontWeight.Bold)
                    }
                    SegBar(r.ap, r.pe, r.co, maxOf(r.tot, 1), Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "অনুমোদিত ${Fmt.bn(r.ap)} • রিভিউ বাকি ${Fmt.bn(r.pe)} • সংশোধন ${Fmt.bn(r.co)}",
                            color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f)
                        )
                        Text("মোট ${Fmt.bn(r.tot)} পেইজ", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.pe > 0) {
                            SmBtn("এক ক্লিকে সব ${Fmt.bn(r.pe)}টি অনুমোদন", "check", tone = "ok", enabled = d.busyUid != uid) {
                                AppScope.scope.launch { d.onApprove(uid, ask) }
                            }
                        }
                        SmBtn("রিভিউ খুলুন") { onReviewUser(uid) }
                    }
                }
            }
        }
    }

    // ---- card 3: activity report (React has a 7-column table; on a phone each user is a compact row) ----
    AppCard {
        Text("সক্রিয়তার রিপোর্ট", color = cs.onSurface, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
            Chips(listOf("all" to "সবাই", "act" to "সক্রিয় (৭ দিনে কাজ করেছে)", "idle" to "নিষ্ক্রিয়"), d.rF) { d.setRF(it) }
        }
        val act = d.activity
        if (act.isEmpty()) {
            Empty("কোনো ইউজার নেই", pad = true)
        } else {
            act.forEach { r -> ActivityRowView(r) }
        }
    }
}

/** One row of the activity report: name + phone (+ blocked), status badge, today / 7 days / total, error rate, last active. */
@Composable
private fun ActivityRowView(r: ActRow) {
    val cs = MaterialTheme.colorScheme
    val rv = r.ap + r.co
    val pc = if (rv > 0) Math.round(r.co.toDouble() / rv * 100).toInt() else null
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(cs.background)
            .border(1.dp, cs.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.u.str("full_name").orEmpty(), color = cs.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    r.u.str("phone").orEmpty() + if (r.u.optBoolean("is_blocked", false)) " • ব্লকড" else "",
                    color = cs.onSurfaceVariant, fontSize = 12.sp
                )
            }
            Badge(if (r.act) "সক্রিয়" else "নিষ্ক্রিয়", if (r.act) "ok" else "warn")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DashStat("আজ", Fmt.bn(r.today)); DashStat("৭ দিন", Fmt.bn(r.w7)); DashStat("মোট", Fmt.bn(r.n))
            DashStat("ভুলের হার", if (pc == null) "-" else Fmt.bn(pc) + "%")
        }
        Text(
            "সর্বশেষ সক্রিয়: " + if (r.la > 0L) Fmt.fmtDT(java.time.Instant.ofEpochMilli(r.la).toString()) else "-",
            color = cs.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

@Composable
private fun DashStat(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(value, color = cs.onSurface, fontWeight = FontWeight.Bold)
        Text(label, color = cs.onSurfaceVariant, fontSize = 11.sp)
    }
}

/** React `.lbr`: rank circle (gold / silver / bronze for the first three), name, page count. */
@Composable
private fun LbRowView(i: Int, name: String, n: Int) {
    val cs = MaterialTheme.colorScheme
    val (rkBg, rkFg) = when (i) {
        0 -> Color(0xFFF2D27A) to Color(0xFF5A3D00)
        1 -> Color(0xFFD9DDE3) to Color(0xFF333333)
        2 -> Color(0xFFE3B98A) to Color(0xFF5A3300)
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(cs.background)
            .border(1.dp, cs.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(rkBg), contentAlignment = Alignment.Center) {
            Text(Fmt.bn(i + 1), color = rkFg, fontWeight = FontWeight.Bold)
        }
        Text(name, color = cs.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${Fmt.bn(n)} পেইজ", color = cs.onSurface, fontWeight = FontWeight.Bold)
    }
}

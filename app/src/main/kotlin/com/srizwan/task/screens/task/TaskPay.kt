package com.srizwan.task.screens.task

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Summary
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.str
import com.srizwan.task.screens.admin.Badge
import com.srizwan.task.screens.admin.Empty
import com.srizwan.task.screens.admin.LiCard
import com.srizwan.task.screens.admin.SegBar
import com.srizwan.task.screens.admin.Tile
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.PrimaryButton
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.okColor
import org.json.JSONObject

/**
 * React task/PayPane.jsx — the payment tab. 3-গ-১: loading skeleton ([PayTilesSkeleton]) and the earnings forecast
 * ([Forecast]). 3-গ-২: warning box, the six tiles ([PayTiles]), the request card (hint + button), payment history,
 * per-book earnings (the admin's `SegBar`, same approved / pending / correction bar) and the "correction needed" list.
 * The request dialog itself (`RequestDialog`, TaskDialogs.kt) and `onRequest` / `onSend` live in TaskScreen.kt.
 */

/**
 * React `PayPane` (section#pPay, max width 560, centred). [data].sum = null until the first money load.
 * [reqBusy] = `onRequest` is running (the button is disabled); [onRequest] = the "পেমেন্ট রিকোয়েস্ট করুন" button;
 * [onOpenPage] = "খুলুন" on a correction row -> View screen (book id, page stem).
 */
@Composable
fun PayPane(data: TaskData, reqBusy: Boolean, onRequest: () -> Unit, onOpenPage: (String, String) -> Unit) {
    val s = data.sum
    val S = data.fin.S
    val me = data.me
    val pays = data.fin.pays
    val proofs = data.fin.proofs
    val booksById = data.booksById
    val min = minPayout(S)
    val corr = if (s != null) proofs.filter { it.str("review_status") == "correction" } else emptyList()
    val hasReq = pays.any { it.str("status") == "requested" }
    val payNumber = me?.paymentNumber
    val ok = s != null && s.available >= min && s.available > 0

    // React `hint` (the text above the request button)
    val hint = if (s == null) "" else when {
        payNumber.isNullOrEmpty() -> "আগে প্রোফাইল থেকে পেমেন্ট নাম্বার দিন।"
        ok -> "উত্তোলনযোগ্য ${Fmt.money(s.available)} — রিকোয়েস্ট পাঠালে অ্যাডমিন অনুমোদন দিয়ে ${PM[me?.paymentMethod] ?: ""} নাম্বার $payNumber-এ পাঠাবেন।"
        else -> "ন্যূনতম ${Fmt.money(min)} হলে রিকোয়েস্ট করা যাবে। আরও ${Fmt.money(maxOf(0.0, min - s.available))} দরকার।" +
            (if (hasReq) " (একটি রিকোয়েস্ট অপেক্ষমাণ আছে)" else "")
    }

    // React `warn`: the mistake-rate warning (needs enough reviewed pages and a high enough rate)
    var warnReviewed = 0
    var warnPct = 0
    var warn = false
    if (s != null) {
        val reviewed = s.approved + s.correction
        val wp = S.finiteNum("warn_pct") ?: 20.0
        val wm = S.finiteNum("warn_min") ?: 5.0
        val pct = if (reviewed > 0) Math.round(s.correction.toDouble() / reviewed * 100).toInt() else 0
        if (reviewed >= wm && pct >= wp) { warn = true; warnReviewed = reviewed; warnPct = pct }
    }

    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            // #warnBox
            if (s != null && warn) WarnBox(warnReviewed, s.correction, warnPct)
            // #payTiles
            if (s == null) PayTilesSkeleton() else PayTiles(s)
            // #fcBox
            if (s != null) Forecast(s, min, dr = data.rateOf(null))

            // পেমেন্ট রিকোয়েস্ট
            AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
                Column(Modifier.padding(horizontal = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("পেমেন্ট রিকোয়েস্ট", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (s != null) Badge("ডিফল্ট রেট ${rateTxt(data.rateOf(null))} / পেইজ", "gold")
                    }
                    Text(
                        hint, color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    PrimaryButton(
                        "পেমেন্ট রিকোয়েস্ট করুন", onClick = onRequest, icon = "points",
                        enabled = !(reqBusy || (s != null && (!ok || payNumber.isNullOrEmpty())))
                    )
                }
            }

            // পেমেন্ট ইতিহাস
            AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
                Column(Modifier.padding(horizontal = 2.dp)) {
                    Text("পেমেন্ট ইতিহাস", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Column(Modifier.padding(top = 10.dp)) {
                        if (s != null) {
                            if (pays.isEmpty()) Empty("এখনো কোনো পেমেন্ট ইতিহাস নেই", pad = true)
                            else pays.forEach { PayRow(it) }
                        }
                    }
                }
            }

            // কিতাব অনুযায়ী পয়েন্টস
            AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
                Column(Modifier.padding(horizontal = 2.dp)) {
                    Text("কিতাব অনুযায়ী পয়েন্টস", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        buildAnnotatedString {
                            append("পয়েন্টস গোনা হয় শুধু অ্যাডমিন যে পেইজ ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("অনুমোদিত") }
                            append(" করেছেন সেগুলোর।")
                        },
                        color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    if (s != null) {
                        if (s.byBook.isEmpty()) Empty("এখনো কোনো পেইজ প্রুফড করেননি", pad = true)
                        else s.byBook.forEach { (id, x) ->
                            val b = booksById[id]
                            Column(Modifier.fillMaxWidth().kvLine().padding(vertical = 12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(b?.str("name") ?: "কিতাব", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(Fmt.money(x.earned), color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                SegBar(x.approved, x.pending, x.correction, x.n, Modifier.padding(top = 6.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "অনুমোদিত ${Fmt.bn(x.approved)} • বাকি ${Fmt.bn(x.pending)} • সংশোধন ${Fmt.bn(x.correction)}",
                                        color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f)
                                    )
                                    Text("রেট ${rateTxt(data.rateOf(b))}", color = cs.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // সংশোধন দরকার (#corrCard, hidden when empty)
            if (corr.isNotEmpty()) AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
                Column(Modifier.padding(horizontal = 2.dp)) {
                    Text("সংশোধন দরকার", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        buildAnnotatedString {
                            append("অ্যাডমিন এই পেইজগুলো সংশোধন করতে বলেছেন। ঠিক করে আবার ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("প্রুফড") }
                            append(" চাপলে পুনরায় রিভিউতে যাবে।")
                        },
                        color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    corr.take(50).forEach { p ->
                        val bid = p.opt("book_id")?.toString() ?: ""
                        val stem = p.str("stem") ?: ""
                        val b = booksById[bid]
                        LiCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (b?.str("name") ?: "কিতাব") + " — " + stem,
                                    color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f)
                                )
                                Box(Modifier.clip(CircleShape).clickable { onOpenPage(bid, stem) }) { Badge("খুলুন", "bad") }
                            }
                            val note = p.str("review_note")
                            if (!note.isNullOrEmpty()) Text("অ্যাডমিন: $note", color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 20.sp)
                        }
                    }
                    if (corr.size > 50) Text("আরও ${Fmt.bn(corr.size - 50)}টি আছে…", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

/** `.kv` row divider: a 1px line under the row. */
@Composable
private fun Modifier.kvLine(): Modifier {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    return this.drawBehind { drawLine(line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
}

/** `#warnBox .noticebox.bad`: "সতর্কতা: …" — shown when [pct] % of the [reviewed] reviewed pages needed correction. */
@Composable
private fun WarnBox(reviewed: Int, correction: Int, pct: Int) {
    val c = errColor()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.padding(vertical = 10.dp).fillMaxWidth().clip(shape)
            .background(c.copy(alpha = 0.10f)).border(1.dp, c.copy(alpha = 0.5f), shape).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("সতর্কতা:") }
                append(" রিভিউ হওয়া ${Fmt.bn(reviewed)}টি পেইজের মধ্যে ${Fmt.bn(correction)}টিতে ভুল ধরা পড়েছে (${Fmt.bn(pct)}%)। দয়া করে মনোযোগ দিয়ে প্রুফ করুন — ভুল পেইজে পয়েন্টস গোনা হয় না।")
            },
            color = c, fontSize = 13.sp, lineHeight = 21.sp
        )
    }
}

/** `#payTiles`: the six summary tiles in the same `.tiles` grid as the skeleton (min 150, gap 10). */
@Composable
private fun PayTiles(s: Summary) {
    val items = listOf(
        Triple(Fmt.money(s.earned), "মোট পয়েন্টস (অনুমোদিত ${Fmt.bn(s.approved)} পেইজ)", true),
        Triple(Fmt.money(s.available), "উত্তোলনযোগ্য (বাকি)", false),
        Triple(Fmt.money(s.paid), "এ পর্যন্ত পেয়েছেন", false),
        Triple(Fmt.money(s.requested), "রিকোয়েস্টে আছে (অপেক্ষমাণ)", false),
        Triple(Fmt.bn(s.pending), "রিভিউ বাকি পেইজ (${Fmt.money(s.pendingAmt)})", false),
        Triple(Fmt.bn(s.correction), "সংশোধন দরকার", false)
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        val cols = maxOf(1, ((maxWidth.value + 10f) / (150f + 10f)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (n, l, hl) -> Tile(n, l, Modifier.weight(1f).fillMaxHeight(), hl = hl) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One payment of the history (`.li`): amount + status badge, request / processed time, method + number, pages, note. */
@Composable
private fun PayRow(x: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val status = x.str("status")
    val (label, tone) = when (status) {
        "paid" -> "পরিশোধিত" to "ok"
        "rejected" -> "বাতিল" to "bad"
        else -> "অপেক্ষমাণ (বাকি)" to "warn"
    }
    val pages = x.finiteNum("pages") ?: 0.0
    val processed = x.str("processed_at")
    val note = x.str("note")
    val meta = buildString {
        append("রিকোয়েস্ট: ").append(Fmt.fmtDT(x.str("created_at")))
        if (!processed.isNullOrEmpty()) append("\n").append(if (status == "paid") "পরিশোধ" else "প্রক্রিয়া").append(": ").append(Fmt.fmtDT(processed))
        append("\n").append(PM[x.str("method")] ?: x.str("method") ?: "").append(" ").append(x.str("number") ?: "")
        if (pages != 0.0) append(" • ").append(Fmt.bn(pages)).append(" পেইজ")
        if (!note.isNullOrEmpty()) append("\nনোট: ").append(note)
    }
    LiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.money(x.finiteNum("amount")), color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Badge(label, tone)
        }
        Text(meta, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 20.sp)
    }
}

/** React `Skeleton` of the pay tab: four `.tile.sk` placeholders in the `.tiles` grid (min 150, gap 10). */
@Composable
fun PayTilesSkeleton() {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        val cols = maxOf(1, ((maxWidth.value + 10f) / (150f + 10f)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            List(4) { it }.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { _ ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 66.dp).clip(shape)
                                .background(cs.surface.copy(alpha = 0.94f))
                                .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                        ) {
                            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                            Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * React `Forecast` (`.card.fc`): what the earnings become once the pages still in review are approved.
 * [min] = minimum payout, [dr] = the default page rate (`rateOf(null)`).
 */
@Composable
fun Forecast(s: Summary, min: Double, dr: Double) {
    val cs = MaterialTheme.colorScheme
    val reviewedN = s.approved + s.correction
    val apr: Double? = if (reviewedN >= 3) s.approved.toDouble() / reviewedN else null
    val allEarn = Fmt.r2(s.earned + s.pendingAmt)
    val allAvail = Fmt.r2(s.available + s.pendingAmt)

    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text("পয়েন্টসের পূর্বাভাস", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (s.pending > 0) {
                val tip = if (s.available >= min) "এখনই রিকোয়েস্ট করার মতো পয়েন্টস আছে।"
                else if (allAvail >= min) "রিভিউ হয়ে গেলে ন্যূনতম পেমেন্ট (${Fmt.money(min)}) ছুঁয়ে রিকোয়েস্ট করতে পারবেন।"
                else "ন্যূনতম পেমেন্ট (${Fmt.money(min)}) ছুঁতে আরও ≈ ${Fmt.bn(Math.ceil((min - allAvail) / dr).toLong())} পেইজ প্রুফ করতে হবে।"
                val showApr = apr != null && apr < 1
                FcRow("রিভিউ বাকি ${Fmt.bn(s.pending)} পেইজ সব অনুমোদিত হলে যোগ হবে", "≈ +" + Fmt.money(s.pendingAmt), up = true, dashed = true)
                FcRow("তখন মোট পয়েন্টস হবে", "≈ " + Fmt.money(allEarn), dashed = true)
                FcRow("তখন উত্তোলনযোগ্য হবে", "≈ " + Fmt.money(allAvail), dashed = showApr)
                if (showApr) FcRow(
                    "আপনার অনুমোদনের হার ${Fmt.bn(Math.round(apr!! * 100))}% — সেই হিসাবে যোগ হবে",
                    "≈ +" + Fmt.money(s.pendingAmt * apr), dashed = false
                )
                Text(
                    "$tip অ্যাডমিন অনুমোদন না দেওয়া পর্যন্ত এটি শুধু আনুমানিক হিসাব।",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                val more = if (s.available < min) "; ন্যূনতম পেমেন্ট (${Fmt.money(min)}) ছুঁতে আরও ≈ ${Fmt.bn(Math.ceil((min - s.available) / dr).toLong())} পেইজ দরকার" else ""
                Text(
                    "এখন রিভিউ বাকি কোনো পেইজ নেই। আরও পেইজ প্রুফড করলে অনুমোদিত হওয়ার পর প্রতি পেইজে ≈ ${rateTxt(dr)} যোগ হবে$more।",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** `.fc .fr`: label left, value right (bold, tabular), a dashed line below (not on the last row). */
@Composable
private fun FcRow(label: String, value: String, up: Boolean = false, dashed: Boolean) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline.copy(alpha = 0.6f)
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                if (dashed) {
                    val y = size.height - 0.5f
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                }
            }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, color = cs.onSurface, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (up) okColor() else cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

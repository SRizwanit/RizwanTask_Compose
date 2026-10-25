package com.srizwan.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * ---------------------------------------------------------------------------
 *  "পেমেন্ট" tab  (React: task/PayPane.jsx)
 *   earning tiles, forecast, payment request, history, earnings per book,
 *   pages that need correction, error-rate warning
 * ---------------------------------------------------------------------------
 */
@Composable
fun PayTab(
    vm: TaskViewModel,
    user: AppUser,
    scroll: ScrollState,
    onOpenBook: (bookId: String, stem: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = vm.summary
    val settings = vm.settings
    val min = settings.minPayout
    val defaultRate = settings.pageRate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- error-rate warning ----
        if (s != null) {
            val reviewed = s.approved + s.correction
            val pct = if (reviewed > 0) Math.round(s.correction * 100.0 / reviewed).toInt() else 0
            if (reviewed >= settings.warnMin && pct >= settings.warnPct) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.error.copy(alpha = 0.14f)
                ) {
                    Text(
                        "সতর্কতা: রিভিউ হওয়া ${bn(reviewed)}টি পেইজের মধ্যে ${bn(s.correction)}টিতে ভুল ধরা পড়েছে (${bn(pct)}%)। " +
                            "দয়া করে মনোযোগ দিয়ে প্রুফ করুন — ভুল পেইজে আয় গোনা হয় না।",
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // ---- tiles ----
        if (s == null) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(Modifier.weight(1f).height(76.dp))
                    SkeletonBox(Modifier.weight(1f).height(76.dp))
                }
            }
        } else {
            val tiles = listOf(
                Triple(money(s.earned), "মোট আয় (অনুমোদিত ${bn(s.approved)} পেইজ)", true),
                Triple(money(s.available), "উত্তোলনযোগ্য (বাকি)", false),
                Triple(money(s.paid), "এ পর্যন্ত পেয়েছেন", false),
                Triple(money(s.requested), "রিকোয়েস্টে আছে (অপেক্ষমাণ)", false),
                Triple(bn(s.pending), "রিভিউ বাকি পেইজ (${money(s.pendingAmt)})", false),
                Triple(bn(s.correction), "সংশোধন দরকার", false)
            )
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (num, label, highlight) ->
                        Tile(num, label, highlight, Modifier.weight(1f))
                    }
                }
            }
            Forecast(s, min, defaultRate)
        }

        // ---- payment request ----
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "পেমেন্ট রিকোয়েস্ট",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (s != null) Badge("ডিফল্ট রেট ${rateTxt(defaultRate)} / পেইজ", GoldColor)
            }
            val ok = s != null && s.available >= min && s.available > 0
            val hasReq = vm.payments.any { it.status == "requested" }
            val hint = when {
                s == null -> ""
                user.payNumber.isBlank() -> "আগে প্রোফাইল থেকে পেমেন্ট নাম্বার দিন।"
                ok -> "উত্তোলনযোগ্য ${money(s.available)} — রিকোয়েস্ট পাঠালে অ্যাডমিন অনুমোদন দিয়ে " +
                    "${PAY_METHOD_LABEL[user.payMethod] ?: ""} নাম্বার ${user.payNumber}-এ পাঠাবেন।"
                else -> "ন্যূনতম ${money(min)} হলে রিকোয়েস্ট করা যাবে। আরও ${money(maxOf(0.0, min - s.available))} আয় দরকার।" +
                    if (hasReq) " (একটি রিকোয়েস্ট অপেক্ষমাণ আছে)" else ""
            }
            if (hint.isNotEmpty()) Hint(hint)
            Button(
                onClick = { vm.onRequest() },
                enabled = !vm.requestBusy && (s == null || (ok && user.payNumber.isNotBlank())),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("👛 পেমেন্ট রিকোয়েস্ট করুন", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ---- history ----
        SoftCard {
            Text("পেমেন্ট ইতিহাস", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface)
            if (s != null) {
                if (vm.payments.isEmpty()) {
                    EmptyNote("এখনো কোনো পেমেন্ট ইতিহাস নেই")
                } else {
                    vm.payments.forEach { PaymentItem(it) }
                }
            }
        }

        // ---- per-book earnings ----
        SoftCard {
            Text("কিতাব অনুযায়ী আয়", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface)
            Hint("আয় গোনা হয় শুধু অ্যাডমিন যে পেইজ অনুমোদিত করেছেন সেগুলোর।")
            if (s != null) {
                if (s.byBook.isEmpty()) {
                    EmptyNote("এখনো কোনো পেইজ প্রুফড করেননি")
                } else {
                    s.byBook.forEach { (id, x) ->
                        val b = vm.booksById[id]
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row {
                                Text(
                                    b?.name ?: "কিতাব",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(money(x.earned), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.onSurface)
                            }
                            SegBar(x.approved, x.pending, x.correction, x.n)
                            Row {
                                Text(
                                    "অনুমোদিত ${bn(x.approved)} • বাকি ${bn(x.pending)} • সংশোধন ${bn(x.correction)}",
                                    fontSize = 11.5.sp,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "রেট ${rateTxt(vm.rateOfBook(b))}",
                                    fontSize = 11.5.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- pages that need correction ----
        val corr = vm.proofs.filter { it.status == "correction" }
        if (s != null && corr.isNotEmpty()) {
            SoftCard {
                Text("সংশোধন দরকার", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface)
                Hint("অ্যাডমিন এই পেইজগুলো সংশোধন করতে বলেছেন। ঠিক করে আবার প্রুফড চাপলে পুনরায় রিভিউতে যাবে।")
                corr.take(50).forEach { p ->
                    val b = vm.booksById[p.bookId]
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${b?.name ?: "কিতাব"} — ${p.stem}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onOpenBook(p.bookId, p.stem) }) {
                                Text("খুলুন", color = colors.error, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (p.note.isNotBlank()) {
                            Text("অ্যাডমিন: ${p.note}", fontSize = 12.5.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
                if (corr.size > 50) Hint("আরও ${bn(corr.size - 50)}টি আছে…")
            }
        }
    }
}

// ---------------------------------------------------------------------------
@Composable
private fun Tile(number: String, label: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) colors.primary else colors.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                number,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) colors.onPrimary else colors.onSurface
            )
            Text(
                label,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = if (highlight) colors.onPrimary.copy(alpha = 0.9f) else colors.onSurfaceVariant
            )
        }
    }
}

/** What the earnings will look like once the pages waiting for review are approved. */
@Composable
private fun Forecast(s: Summary, min: Double, defaultRate: Double) {
    val colors = MaterialTheme.colorScheme
    val reviewedN = s.approved + s.correction
    val approvalRate = if (reviewedN >= 3) s.approved.toDouble() / reviewedN else null
    val allEarn = r2(s.earned + s.pendingAmt)
    val allAvail = r2(s.available + s.pendingAmt)

    SoftCard {
        Text("আয়ের পূর্বাভাস", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface)
        if (s.pending > 0) {
            val tip = when {
                s.available >= min -> "এখনই রিকোয়েস্ট করার মতো টাকা আছে।"
                allAvail >= min -> "রিভিউ হয়ে গেলে ন্যূনতম পেমেন্ট (${money(min)}) ছুঁয়ে রিকোয়েস্ট করতে পারবেন।"
                else -> "ন্যূনতম পেমেন্ট (${money(min)}) ছুঁতে আরও ≈ ${bn(pagesFor(min - allAvail, defaultRate))} পেইজ প্রুফ করতে হবে।"
            }
            KvRow("রিভিউ বাকি ${bn(s.pending)} পেইজ সব অনুমোদিত হলে যোগ হবে", "≈ +${money(s.pendingAmt)}", OkColor)
            KvRow("তখন মোট আয় হবে", "≈ ${money(allEarn)}")
            KvRow("তখন উত্তোলনযোগ্য হবে", "≈ ${money(allAvail)}")
            if (approvalRate != null && approvalRate < 1.0) {
                KvRow(
                    "আপনার অনুমোদনের হার ${bn(Math.round(approvalRate * 100).toInt())}% — সেই হিসাবে যোগ হবে",
                    "≈ +${money(s.pendingAmt * approvalRate)}"
                )
            }
            Hint("$tip অ্যাডমিন অনুমোদন না দেওয়া পর্যন্ত এটি শুধু আনুমানিক হিসাব।")
        } else {
            Hint(
                "এখন রিভিউ বাকি কোনো পেইজ নেই। আরও পেইজ প্রুফড করলে অনুমোদিত হওয়ার পর প্রতি পেইজে ≈ ${rateTxt(defaultRate)} যোগ হবে" +
                    (if (s.available < min) "; ন্যূনতম পেমেন্ট (${money(min)}) ছুঁতে আরও ≈ ${bn(pagesFor(min - s.available, defaultRate))} পেইজ দরকার" else "") +
                    "।"
            )
        }
    }
}

@Composable
private fun PaymentItem(x: Payment) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (x.status) {
        "paid" -> "পরিশোধিত" to OkColor
        "rejected" -> "বাতিল" to colors.error
        else -> "অপেক্ষমাণ (বাকি)" to WarnColor
    }
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                money(x.amount),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Badge(label, color)
        }
        Text("রিকোয়েস্ট: ${fmtDT(x.createdAt)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
        if (x.processedAt != null) {
            Text(
                (if (x.status == "paid") "পরিশোধ" else "প্রক্রিয়া") + ": ${fmtDT(x.processedAt)}",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }
        Text(
            (PAY_METHOD_LABEL[x.method] ?: x.method) + " " + x.number +
                (if (x.pages > 0) " • ${bn(x.pages)} পেইজ" else ""),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant
        )
        if (x.note.isNotBlank()) Text("নোট: ${x.note}", fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}

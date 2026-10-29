package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.copyText
import com.srizwan.task.core.enc
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import com.srizwan.task.ui.PagerBar
import com.srizwan.task.ui.Spinner
import com.srizwan.task.ui.pageSlice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/** `const sum = (a) => a.reduce((s, x) => s + Number(x.amount || 0), 0)` */
private fun sum(a: List<JSONObject>): Double = a.sumOf { it.finiteNum("amount") ?: 0.0 }

/**
 * React admin/PayTab.jsx — payment requests: pending / paid / rejected.
 * Marking a request paid, or rejecting it, sends the user a notice (kind = payment) and writes the activity log.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayTab(vm: AdminViewModel, ask: AskController, openKey: Int) {
    val ctx = LocalContext.current
    val pyF = vm.payFilter
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf("") }
    // pagination: 20 payment requests per page; a new status filter starts again from page 1
    var page by remember(pyF) { mutableStateOf(0) }
    val cs = MaterialTheme.colorScheme

    // Opening the tab re-fetches payments and proofs (React openKey effect: Promise.all([loadPaysQuiet(), loadProofs(true)])).
    LaunchedEffect(openKey) {
        if (openKey > 0) {
            loading = true
            try {
                coroutineScope {
                    launch { vm.loadPaysQuiet() }
                    launch { vm.loadProofs(true) }
                }
            } finally { loading = false }
        }
    }

    /** copy: number to clipboard; paid / rej: ask + server write + local patch + notice + log + toast. */
    fun act(a: String, x: JSONObject) {
        if (a == "copy") {
            val num = x.str("number").orEmpty()
            if (copyText(ctx, num)) AppToast.show("নাম্বার কপি হয়েছে") else AppToast.show(num, false)
            return
        }
        val id = x.idStr()
        busyId = id
        // App scope: a tab switch must not cancel a write that is already going out.
        AppScope.scope.launch {
            try {
                val now = Instant.now().toString()
                val amount = x.finiteNum("amount") ?: 0.0
                val userId = x.str("user_id").orEmpty()
                val method = PM[x.str("method")] ?: ""
                val number = x.str("number").orEmpty()
                if (a == "paid") {
                    val note = ask.ask(
                        title = "পেমেন্ট নিশ্চিত করুন",
                        label = Fmt.money(amount) + " → " + method + " " + number + " (পয়েন্টস পাঠানোর পরেই চিহ্নিত করুন)। ট্রানজেকশন আইডি / নোট (ঐচ্ছিক):",
                        ok = "পরিশোধিত চিহ্নিত করুন"
                    ) ?: return@launch
                    val sm = vm.userSum(userId)
                    if (sm.earned < sm.paid + amount && !ask.confirm(
                            "সতর্কতা: ইউজারের অনুমোদিত পয়েন্টস (" + Fmt.money(sm.earned) + ") পরিশোধিত + এই রিকোয়েস্টের (" +
                                Fmt.money(sm.paid + amount) + ") চেয়ে কম। তবুও পরিশোধিত চিহ্নিত করবেন?"
                        )
                    ) return@launch
                    val patch = jsonOf("status" to "paid", "processed_at" to now, "note" to note.ifEmpty { null })
                    Sb.patch("payments?id=eq." + enc(id), patch)
                    vm.patchPay(id, patch)
                    try {
                        sendNotice(
                            userId, "পেমেন্ট পাঠানো হয়েছে",
                            Fmt.money(amount) + " আপনার " + method + " নাম্বার " + number + "-এ পাঠানো হয়েছে।" +
                                (if (note.isNotEmpty()) "\nনোট: $note" else ""),
                            "payment"
                        )
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { /* ignore */ }
                    ActivityLog.log("admin_pay", vm.uname(userId) + " • " + Fmt.money(amount))
                    AppToast.show("পরিশোধিত চিহ্নিত হয়েছে")
                } else if (a == "rej") {
                    val why = ask.ask(title = "রিকোয়েস্ট বাতিল", label = "বাতিলের কারণ (ইউজার দেখতে পাবে)", ok = "বাতিল করুন") ?: return@launch
                    val patch = jsonOf("status" to "rejected", "processed_at" to now, "note" to why.ifEmpty { null })
                    Sb.patch("payments?id=eq." + enc(id), patch)
                    vm.patchPay(id, patch)
                    try {
                        sendNotice(
                            userId, "পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে",
                            Fmt.money(amount) + " এর রিকোয়েস্টটি বাতিল হয়েছে।" +
                                (if (why.isNotEmpty()) "\nকারণ: $why" else "") +
                                "\nপয়েন্টস আপনার উত্তোলনযোগ্য ব্যালেন্সে ফিরে গেছে।",
                            "payment"
                        )
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { /* ignore */ }
                    ActivityLog.log("admin_reject", vm.uname(userId) + " • " + Fmt.money(amount) + (if (why.isNotEmpty()) " • $why" else ""))
                    AppToast.show("বাতিল করা হয়েছে")
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                AppToast.show(e.message.orEmpty(), true)
            } finally { busyId = "" }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (!vm.payTbl) {
            NoticeBox("payments টেবিল নেই — $SQLHINT।")
            return@Column
        }

        val pays = vm.pays
        val rq = pays.filter { it.str("status") == "requested" }
        val pd = pays.filter { it.str("status") == "paid" }
        val list = pays.filter { pyF.isEmpty() || it.str("status") == pyF }

        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val m = Modifier.widthIn(min = 150.dp)
            Tile(Fmt.bn(rq.size), "অপেক্ষমাণ রিকোয়েস্ট (" + Fmt.money(sum(rq)) + ")", m, hl = true)
            Tile(Fmt.money(sum(pd)), "মোট পরিশোধিত (" + Fmt.bn(pd.size) + "টি)", m)
            Tile(Fmt.bn(pays.count { it.str("status") == "rejected" }), "বাতিল", m)
        }

        Column(Modifier.padding(bottom = 12.dp)) {
            Chips(
                items = listOf("requested" to "অপেক্ষমাণ", "paid" to "পরিশোধিত", "rejected" to "বাতিল", "" to "সব"),
                value = pyF, onPick = { vm.updatePayFilter(it) }
            )
        }

        if (loading) {
            Spinner(true)
        } else if (list.isEmpty()) {
            Empty("কোনো পেমেন্ট নেই", art = "wallet")
        } else {
            list.pageSlice(page).forEach { x ->
                val id = x.idStr()
                val stat = x.str("status")
                val st = when (stat) {
                    "paid" -> "পরিশোধিত" to "ok"
                    "rejected" -> "বাতিল" to "bad"
                    else -> "অপেক্ষমাণ" to "warn"
                }
                val userId = x.str("user_id").orEmpty()
                val sm = vm.userSum(userId)
                val busy = busyId == id
                val method = PM[x.str("method")] ?: x.str("method").orEmpty()

                LiCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            vm.uname(userId).ifEmpty { x.str("user_name").orEmpty() },
                            color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f)
                        )
                        Badge(st.first, st.second)
                    }
                    Text(Fmt.money(x.finiteNum("amount")), color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildAnnotatedString {
                                append("$method: ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) {
                                    append(x.str("number")?.takeIf { it.isNotEmpty() } ?: "-")
                                }
                            },
                            color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp
                        )
                        SmBtn("কপি", compact = true) { act("copy", x) }
                    }
                    val pages = x.finiteNum("pages") ?: 0.0
                    val meta = buildString {
                        append("রিকোয়েস্ট: ${Fmt.fmtDT(x.str("created_at"))}")
                        if (pages != 0.0) append(" • অনুমোদিত ${Fmt.bn(pages)} পেইজ")
                        if (!x.str("processed_at").isNullOrEmpty()) append("\nপ্রক্রিয়া: ${Fmt.fmtDT(x.str("processed_at"))}")
                        if (!x.str("note").isNullOrEmpty()) append("\nনোট: ${x.str("note")}")
                        append("\nইউজারের হিসাব: মোট পয়েন্টস ${Fmt.money(sm.earned)} • পরিশোধিত ${Fmt.money(sm.paid)} • রিকোয়েস্টে ${Fmt.money(sm.requested)}")
                    }
                    Text(meta, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)

                    if (stat == "requested") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmBtn("পরিশোধ করেছি (অনুমোদন)", "check", tone = "ok", enabled = !busy) { act("paid", x) }
                            SmBtn("বাতিল", "x", tone = "bad", enabled = !busy) { act("rej", x) }
                        }
                    }
                }
            }
            PagerBar(page = page, total = list.size, onPage = { page = it })
        }
    }
}

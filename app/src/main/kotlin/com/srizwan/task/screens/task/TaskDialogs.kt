package com.srizwan.task.screens.task

import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.ui.errColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.srizwan.task.screens.admin.Badge
import org.json.JSONObject
import com.srizwan.task.core.str

/**
 * React task/Dialogs.jsx — the small dialogs of the Task page. Opening / closing is done by the parent (a `show` flag in
 * TaskScreen instead of `dlgRef.showModal()/close()`).
 * 3-খ-১: [GoalDialog]. 3-খ-২: [HiddenBookDialog], [LockedBookDialog]. 3-খ-৩: [NoticeDialog]. 3-গ-২: [RequestDialog].
 */

/** React `GoalDialog`: daily goal — quick chips (10 / 20 / 30 / 50) or my own number; [err] shows below the field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDialog(
    value: String,
    onValue: (String) -> Unit,
    err: String,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("দৈনিক লক্ষ্য", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "প্রতিদিন কত পেইজ প্রুফড করতে চান? (এই ডিভাইসে সংরক্ষিত থাকবে)",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp
                )
                // .chips: quick values
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 20, 30, 50).forEach { g ->
                        Text(
                            Fmt.bn(g), color = cs.onSurface, fontSize = 12.5.sp,
                            modifier = Modifier.clip(CircleShape)
                                .border(1.dp, cs.outline.copy(alpha = 0.6f), CircleShape)
                                .clickable { onValue(g.toString()) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
                // label.f + input[type=number]
                Text("অথবা নিজের সংখ্যা", color = cs.onSurfaceVariant, fontSize = 13.sp)
                OutlinedTextField(
                    value = value,
                    onValueChange = { v -> onValue(v.filter { it in '0'..'9' }.take(4)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("সংরক্ষণ", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("বন্ধ") } }
    )
}

/** React `#hidDlg`: the admin hid this book — [book] = its name ("এই" is used when empty). */
@Composable
fun HiddenBookDialog(book: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("কিতাবটি সাময়িক বন্ধ আছে", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                buildAnnotatedString {
                    if (book.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("\"$book\"") } else append("এই")
                    append(" কিতাবটির এক্সেস অ্যাডমিন সাময়িক বন্ধ রেখেছেন।\nআপনি অন্য কিতাবের কাজ করতে পারেন।")
                },
                color = cs.onSurfaceVariant, fontSize = 15.sp, lineHeight = 27.sp
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("ঠিক আছে", fontWeight = FontWeight.Bold) } }
    )
}

/** React `#lockDlg`: another user has this book open right now ([name] = who, [book] = its name). */
@Composable
fun LockedBookDialog(name: String, book: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("কিতাবটি এখন ব্যবহৃত হচ্ছে", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    append(" " + (if (book.isNotEmpty()) "\"$book\" " else "") + "কিতাবটির কাজ করছেন...\nউনি কাজ অফ করলে আপনি করতে পারবেন।")
                },
                color = cs.onSurfaceVariant, fontSize = 15.sp, lineHeight = 27.sp
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("ঠিক আছে", fontWeight = FontWeight.Bold) } }
    )
}

/**
 * React `NoticeDialog`: notices one by one (kind badge, date, title, body, dots) with "আগের" / "পরের" buttons.
 * [onIdx] changes the shown notice; [onClose] is called for every way of closing (it marks them as seen).
 */
@Composable
fun NoticeDialog(list: List<JSONObject>, idx: Int, onIdx: (Int) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = list.getOrNull(idx)
    val last = idx >= list.size - 1
    AlertDialog(
        onDismissRequest = onClose,
        title = null,
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(cur?.let { NOTICE_KIND[it.str("kind")] ?: "নোটিশ" } ?: "নোটিশ", "gold")
                    Box(Modifier.weight(1f))
                    Text(cur?.let { Fmt.fmtDT(it.str("created_at")) } ?: "", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(cur?.str("title") ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = cs.onSurface, modifier = Modifier.padding(top = 8.dp))
                Text(
                    cur?.str("body") ?: "", color = cs.onSurface, fontSize = 14.sp, lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 10.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
                if (list.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                        list.indices.forEach { i ->
                            Box(
                                Modifier.padding(horizontal = 3.dp).size(7.dp).clip(CircleShape)
                                    .background(if (i == idx) cs.primary else cs.outline.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (idx != 0) TextButton(onClick = { onIdx(maxOf(0, idx - 1)) }) { Text("আগের") }
                if (!last) TextButton(onClick = { onIdx(minOf(list.size - 1, idx + 1)) }) { Text("পরের") }
                TextButton(onClick = onClose) { Text(if (last) "ঠিক আছে" else "বন্ধ করুন", fontWeight = FontWeight.Bold) }
            }
        }
    )
}

/** React `RequestDialog` `info`: the values shown above the amount field (texts already formatted) + the largest allowed amount. */
class RequestInfo(val avail: String, val min: String, val pmLabel: String, val number: String, val max: Double)

/**
 * React `RequestDialog` (`#rqDlg`): "পেমেন্ট রিকোয়েস্ট" — shows the withdrawable amount, the minimum and my payment number,
 * and asks how much to request ([amt], pre-filled with everything available). [err] shows below the hint;
 * [busy] disables the send button while the request is being sent. Checks are in TaskScreen `onSend`.
 */
@Composable
fun RequestDialog(
    info: RequestInfo,
    amt: String,
    onAmt: (String) -> Unit,
    err: String,
    busy: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("পেমেন্ট রিকোয়েস্ট", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RqKv("উত্তোলনযোগ্য", info.avail)
                RqKv("ন্যূনতম রিকোয়েস্ট", info.min)
                RqKv(info.pmLabel, info.number)
                // label.f + input[type=number][inputmode=decimal][step=0.01]
                Text("কত টাকা চাইবেন (৳)", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                OutlinedTextField(
                    value = amt,
                    onValueChange = { v ->
                        // digits and a single decimal point only
                        var dot = false
                        val sb = StringBuilder()
                        for (c in v) {
                            if (c in '0'..'9') sb.append(c)
                            else if (c == '.' && !dot) { dot = true; sb.append(c) }
                        }
                        onAmt(sb.toString().take(12))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "অ্যাডমিন অনুমোদন দিয়ে এই নাম্বারে টাকা পাঠাবেন। নাম্বার ভুল হলে আগে প্রোফাইল থেকে ঠিক করুন।",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)
                )
                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onSend, enabled = !busy) { Text("রিকোয়েস্ট পাঠান", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("বন্ধ") } }
    )
}

/** `.kv`: label (muted) on the left, bold value on the right, a thin line below. */
@Composable
private fun RqKv(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline.copy(alpha = 0.5f)))
    }
}

package com.srizwan.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Daily goal
// ---------------------------------------------------------------------------
@Composable
fun GoalDialog(initial: Int, error: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    AppDialog(onDismiss = onCancel) {
        Text("দৈনিক লক্ষ্য", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Hint("প্রতিদিন কত পেইজ প্রুফড করতে চান? (এই ফোনে সংরক্ষিত থাকবে)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 50).forEach { g ->
                AssistChip(onClick = { text = g.toString() }, label = { Text(bn(g)) })
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("অথবা নিজের সংখ্যা") },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (error.isNotEmpty()) ErrorText(error)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onCancel) { Text("বন্ধ") }
            Button(onClick = { onSave(text) }) { Text("সংরক্ষণ") }
        }
    }
}

// ---------------------------------------------------------------------------
// Payment request
// ---------------------------------------------------------------------------
@Composable
fun RequestDialog(
    info: RequestInfo,
    error: String,
    sending: Boolean,
    onCancel: () -> Unit,
    onSend: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var amt by rememberSaveable {
        mutableStateOf(info.available.toString().removeSuffix(".0"))
    }
    AppDialog(onDismiss = onCancel, dismissable = !sending) {
        Text("পেমেন্ট রিকোয়েস্ট", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        KvRow("উত্তোলনযোগ্য", money(info.available))
        KvRow("ন্যূনতম রিকোয়েস্ট", money(info.min))
        KvRow(info.methodLabel, info.number)
        OutlinedTextField(
            value = amt,
            onValueChange = { v ->
                val clean = v.filter { it.isDigit() || it == '.' }
                if (clean.count { it == '.' } <= 1) amt = clean.take(10)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !sending,
            label = { Text("কত টাকা চাইবেন (৳)") },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Hint("অ্যাডমিন অনুমোদন দিয়ে এই নাম্বারে টাকা পাঠাবেন। নাম্বার ভুল হলে আগে প্রোফাইল থেকে ঠিক করুন।")
        if (error.isNotEmpty()) ErrorText(error)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onCancel, enabled = !sending) { Text("বন্ধ") }
            Button(onClick = { onSend(amt) }, enabled = !sending) {
                Text(if (sending) "পাঠানো হচ্ছে…" else "রিকোয়েস্ট পাঠান")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Notices / announcements (one by one)
// ---------------------------------------------------------------------------
@Composable
fun NoticeDialog(list: List<Notice>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val i = index.coerceIn(0, list.lastIndex)
    val cur = list[i]
    val last = i >= list.lastIndex
    AppDialog(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(NOTICE_KIND_LABEL[cur.kind] ?: "নোটিশ", GoldColor)
            Box(modifier = Modifier.weight(1f))
            Text(fmtDT(cur.createdAt), fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Text(cur.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(cur.body, fontSize = 15.sp, lineHeight = 22.sp, color = colors.onSurface)
        if (list.size > 1) Dots(count = list.size, current = i)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (i > 0) TextButton(onClick = { onIndex(i - 1) }) { Text("আগের") }
            if (!last) TextButton(onClick = { onIndex(i + 1) }) { Text("পরের") }
            Button(onClick = onClose) { Text(if (last) "ঠিক আছে" else "বন্ধ করুন") }
        }
    }
}

@Composable
fun Dots(count: Int, current: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        repeat(count) { n ->
            Box(
                modifier = Modifier
                    .size(if (n == current) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (n == current) colors.primary else colors.outline.copy(alpha = 0.4f))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// First-time guide (texts are the same as the web guide; the animated
// screenshots are replaced by a big icon per step)
// ---------------------------------------------------------------------------
private data class GuideStep(
    val icon: String,
    val tag: String,
    val title: String,
    val text: String,
    val subs: List<String> = emptyList()
)

private val GUIDE = listOf(
    GuideStep("👋", "স্বাগতম", "আসসালামু আলাইকুম, স্বাগতম!",
        "এই অ্যাপে কিতাবের পেইজ এ আই কে দিয়ে টেক্সট করিয়ে, সে টেক্সট দেখে ভুল শুধরে প্রুফ করবেন। কাজের বিনিময়ে প্রতি অনুমোদিত পেইজে নির্দিষ্ট রেটে আয় হবে।"),
    GuideStep("📚", "শুরুতে", "কিতাব বেছে নিন",
        "কিতাবের কার্ডে চাপ দিন। প্রতিটি কার্ডে রেট ও অগ্রগতির বার (কত পেইজ হলো, কত বাকি) দেখা যায়।"),
    GuideStep("⬇️", "ধাপ ১", "ছবি ডাউনলোড করে প্রম্পট কপি করুন",
        "পেইজ খুললে উপরের টুলবারে ছবি ডাউনলোডের বোতাম ও তার পাশে এআই (✦) চিহ্ন পাবেন।",
        listOf("টাস্ক থেকে ডাউনলোড বোতামে চাপ দিয়ে পেইজের ছবিটি ফোনে নামান।", "পাশের এআই (✦) চিহ্নে ক্লিক করুন — প্রম্পট কপি হয়ে যাবে।")),
    GuideStep("🤖", "ধাপ ২", "ছবি ও প্রম্পট Meta AI বা ChatGPT-কে দিন",
        "meta.ai বা chatgpt.com খুলে ডাউনলোড করা ছবি এবং কপি করা প্রম্পট একসাথে পাঠান।",
        listOf("\"+\" চেপে ডাউনলোড করা ছবিটি যোগ করুন।", "কপি করা প্রম্পট লেখার ঘরে পেস্ট করুন।", "পাঠানোর তীর (↑) চাপুন — Meta AI ছবি থেকে টেক্সট বের করে দেবে।")),
    GuideStep("📋", "ধাপ ৩", "AI-র দেওয়া টেক্সট কপি করুন",
        "Meta AI বা ChatGPT-এর উত্তর শেষ হলে নিচের কপি আইকনে চাপ দিন। এতে পুরো টেক্সট একবারে কপি হবে।",
        listOf("উত্তরের নিচের কপি আইকনে চাপ দিন, তারপর অ্যাপে ফিরে আসুন।")),
    GuideStep("📝", "ধাপ ৪", "টেক্সট বক্সে হেডিং চালু করে পেস্ট করুন",
        "অ্যাপের পেইজে ফিরে টেক্সট বক্সে কপি করা টেক্সট বসান। পেইজে শিরোনাম (হেডিং) থাকলে টেক্সট বক্সে হেডিং চালু করে নিন।",
        listOf("টেক্সট বক্সের পেস্ট (📋) আইকনে চাপ দিন — কপি করা টেক্সট বসে যাবে।", "অক্ষর সংখ্যা দেখে নিশ্চিত হন যে টেক্সট এসেছে।", "টেক্সট বক্সে লেখাটি ছবির সাথে মিলছে কি না পড়ে দেখুন।")),
    GuideStep("💾", "ধাপ ৫", "সেভ করুন",
        "টেক্সট বসানোর পর নিচের বারের \"সেভ\" দিয়ে সংরক্ষণ করুন।",
        listOf("\"সেভ\" — শুধু এই পেইজ সংরক্ষণ হবে।", "\"সেভ ও পরের\" — সংরক্ষণ করে সরাসরি পরের পেইজে চলে যাবে।")),
    GuideStep("✅", "ধাপ ৬", "প্রুফ করে \"প্রুফড\" চিহ্ন দিন",
        "ছবি ও টেক্সট মিলিয়ে ভুল ঠিক করুন, তারপর প্রুফড চিহ্ন দিন। অ্যাডমিন পরে আপনার কাজ রিভিউ করে \"অনুমোদিত\" বা \"সংশোধন দরকার\" চিহ্ন দেবেন।",
        listOf("ছবির সাথে টেক্সট মিলিয়ে ভুল শুধরে নিন।", "নিচের \"প্রুফড\" বক্সে টিক দিন — পেইজটি রিভিউতে যাবে।", "\"সেভ ও পরের\" চেপে পরের পেইজে যান।")),
    GuideStep("💰", "ফলাফল", "আয় ও পেমেন্ট",
        "\"পেমেন্ট\" ট্যাবে মোট আয়, বাকি টাকা ও ইতিহাস দেখুন। আয় গোনা হয় শুধু অ্যাডমিন যে পেইজ অনুমোদিত করেছেন সেগুলোর।",
        listOf("\"অনুমোদিত\" পেইজ আপনার আয়ে যোগ হয়।", "\"সংশোধন দরকার\" হলে ঠিক করে আবার \"প্রুফড\" চাপুন — ভুল পেইজে আয় গোনা হয় না।", "নির্দিষ্ট পরিমাণ হলে \"পেমেন্ট রিকোয়েস্ট\" চেপে টাকা চাইতে পারবেন।")),
    GuideStep("🎯", "নতুন", "লক্ষ্য, টানা দিন ও চালিয়ে যান",
        "টাস্ক ট্যাবের উপরের কার্ডে আজকের লক্ষ্য, টানা কাজের দিন ও ব্যাজ দেখা যায়। \"চালিয়ে যান\" বাটনে শেষ কাজের জায়গায় ফেরা যায়।",
        listOf("লক্ষ্যের ঘরে চাপ দিয়ে দৈনিক লক্ষ্য (কত পেইজ) বদলান।", "প্রতিদিন অন্তত একটি পেইজ প্রুফড করলে টানা দিন বাড়ে; ৩, ৭, ১৫, ৩০ দিনে ব্যাজ মেলে।", "\"চালিয়ে যান\" চাপলে শেষ যে কিতাব ও পেইজে ছিলেন সেখানে সরাসরি খুলবে।")),
    GuideStep("🏆", "সাহায্য", "লিডারবোর্ড, নোটিশ ও সাহায্য",
        "\"র‍্যাংক\" ট্যাবে সবার মধ্যে আপনার অবস্থান দেখুন। অ্যাডমিনের ঘোষণা ডায়ালগে আসবে। উপরের টুলবারের প্রোফাইল আইকনে আপনার সব তথ্য আছে।",
        listOf("\"র‍্যাংক\" ট্যাবে আজ, এই সপ্তাহ ও সব সময়ের তালিকা দেখুন।", "অ্যাডমিনের ঘোষণা ডায়ালগে আসবে; আগেরগুলো প্রোফাইলের \"নোটিশ ও ঘোষণা\"-তে।", "সমস্যা হলে প্রোফাইল থেকে টিকেট খুলুন বা হোয়াটসঅ্যাপে যোগাযোগ করুন।", "উপরের প্রোফাইল আইকনে মোবাইল/পেমেন্ট নাম্বার, থিম ও লগআউট।"))
)

@Composable
fun GuideDialog(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var step by rememberSaveable { mutableStateOf(0) }
    val g = GUIDE[step]
    val last = step == GUIDE.lastIndex

    AppDialog(onDismiss = onClose) {
        Badge(g.tag, GoldColor)
        Text(g.icon, fontSize = 54.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(g.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(g.text, fontSize = 15.sp, lineHeight = 22.sp, color = colors.onSurfaceVariant)
        g.subs.forEachIndexed { n, sub ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(bn(n + 1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary)
                }
                Text(sub, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
            }
        }
        Dots(count = GUIDE.size, current = step)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
        ) {
            if (!last) TextButton(onClick = onClose) { Text("বাদ দিন") }
            if (step > 0) TextButton(onClick = { step-- }) { Text("আগের") }
            Button(onClick = { if (last) onClose() else step++ }) { Text(if (last) "শুরু করি" else "পরের") }
        }
    }
}

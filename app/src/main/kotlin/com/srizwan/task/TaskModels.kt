package com.srizwan.task

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * ---------------------------------------------------------------------------
 *  TaskModels.kt  -  data + pure helpers for the Task page
 *  (port of React: pages/task/helpers.js and lib/common.js -> summarize / rateOf)
 * ---------------------------------------------------------------------------
 */

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------
data class Book(
    val id: String,
    val name: String,
    val author: String,
    val coverUrl: String,
    val pageRate: Double?,     // null = default rate from settings
    val totalPages: Int        // 0 = unknown
)

data class Proof(
    val bookId: String,
    val stem: String,
    val status: String,        // pending / approved / correction
    val note: String,
    val rate: Double?,         // rate saved when the page was approved
    val createdAt: Long?,      // epoch millis
    val reviewedAt: Long?
)

data class Payment(
    val id: String,
    val amount: Double,
    val pages: Int,
    val method: String,
    val number: String,
    val status: String,        // requested / paid / rejected
    val note: String,
    val createdAt: Long?,
    val processedAt: Long?
)

data class Notice(
    val id: String,
    val title: String,
    val body: String,
    val kind: String,          // notice / review / payment
    val createdAt: Long?
)

data class Settings(
    val pageRate: Double = DEFAULT_RATE,
    val minPayout: Double = DEFAULT_MIN_PAYOUT,
    val warnPct: Int = 20,
    val warnMin: Int = 5
)

data class RankRow(val id: String, val name: String, val n: Int)

data class BookSum(
    val n: Int = 0,
    val approved: Int = 0,
    val pending: Int = 0,
    val correction: Int = 0,
    val earned: Double = 0.0
)

data class Summary(
    val pages: Int,
    val approved: Int,
    val pending: Int,
    val correction: Int,
    val earned: Double,
    val pendingAmt: Double,
    val paid: Double,
    val requested: Double,
    val available: Double,
    val byBook: Map<String, BookSum>
)

const val DEFAULT_RATE = 0.2
const val DEFAULT_MIN_PAYOUT = 50.0

val PAY_METHOD_LABEL = mapOf("bkash" to "বিকাশ", "nagad" to "নগদ")
val NOTICE_KIND_LABEL = mapOf("notice" to "ঘোষণা", "review" to "রিভিউ", "payment" to "পেমেন্ট")

/** Streak badges: days in a row -> name. */
val BADGES = listOf(
    3 to "শুরু",
    7 to "এক সপ্তাহ",
    15 to "পনেরো দিন",
    30 to "এক মাস",
    60 to "দুই মাস",
    100 to "শতক"
)

// ---------------------------------------------------------------------------
// JSON -> models
// ---------------------------------------------------------------------------
private fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key, "")

private fun JSONObject.dbl(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

fun parseTime(value: String?): Long? {
    if (value.isNullOrBlank() || value == "null") return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(block(it)) }
    return out
}

fun parseBooks(array: JSONArray?): List<Book> = array.mapObjects { j ->
    Book(
        id = j.str("id"),
        name = j.str("name"),
        author = j.str("author"),
        coverUrl = j.str("cover_url"),
        pageRate = j.dbl("page_rate"),
        totalPages = j.dbl("total_pages")?.toInt() ?: 0
    )
}

fun parseProofs(array: JSONArray?): List<Proof> = array.mapObjects { j ->
    Proof(
        bookId = j.str("book_id"),
        stem = j.str("stem"),
        status = j.str("review_status").ifBlank { "pending" },
        note = j.str("review_note"),
        rate = j.dbl("rate"),
        createdAt = parseTime(j.str("created_at")),
        reviewedAt = parseTime(j.str("reviewed_at"))
    )
}

fun parsePayments(array: JSONArray?): List<Payment> = array.mapObjects { j ->
    Payment(
        id = j.str("id"),
        amount = j.dbl("amount") ?: 0.0,
        pages = j.dbl("pages")?.toInt() ?: 0,
        method = j.str("method"),
        number = j.str("number"),
        status = j.str("status"),
        note = j.str("note"),
        createdAt = parseTime(j.str("created_at")),
        processedAt = parseTime(j.str("processed_at"))
    )
}

fun parseNotices(array: JSONArray?): List<Notice> = array.mapObjects { j ->
    Notice(
        id = j.str("id"),
        title = j.str("title"),
        body = j.str("body"),
        kind = j.str("kind"),
        createdAt = parseTime(j.str("created_at"))
    )
}

fun parseSettings(j: JSONObject?): Settings {
    if (j == null) return Settings()
    return Settings(
        pageRate = j.dbl("page_rate") ?: DEFAULT_RATE,
        minPayout = j.dbl("min_payout") ?: DEFAULT_MIN_PAYOUT,
        warnPct = j.dbl("warn_pct")?.toInt() ?: 20,
        warnMin = j.dbl("warn_min")?.toInt() ?: 5
    )
}

fun parseRank(array: JSONArray?): List<RankRow> = array.mapObjects { j ->
    RankRow(id = j.str("user_id"), name = j.str("name"), n = j.dbl("n")?.toInt() ?: 0)
}.filter { it.n > 0 }

fun parseCounts(j: JSONObject?): Map<String, Int> {
    if (j == null) return emptyMap()
    val out = HashMap<String, Int>()
    j.keys().forEach { k -> out[k] = j.optInt(k, 0) }
    return out
}

// ---------------------------------------------------------------------------
// Earnings (same rules as the web app)
//  - rate: book rate, else default rate from settings (0.20)
//  - only APPROVED pages earn money (rate saved at approval time)
//  - pending pages are shown separately, "correction" pages earn nothing
// ---------------------------------------------------------------------------
fun r2(x: Double): Double = (x * 100.0).roundToLong() / 100.0

fun rateOf(book: Book?, s: Settings): Double = book?.pageRate ?: s.pageRate

fun summarize(
    proofs: List<Proof>,
    booksById: Map<String, Book>,
    s: Settings,
    payments: List<Payment>
): Summary {
    var pages = 0
    var approved = 0
    var pending = 0
    var correction = 0
    var earned = 0.0
    var pendingAmt = 0.0
    var paid = 0.0
    var requested = 0.0
    val byBook = LinkedHashMap<String, BookSum>()

    for (p in proofs) {
        val cur = rateOf(booksById[p.bookId], s)
        var bb = byBook[p.bookId] ?: BookSum()
        pages++
        bb = bb.copy(n = bb.n + 1)
        when (p.status) {
            "approved" -> {
                val r = p.rate ?: cur
                approved++
                earned += r
                bb = bb.copy(approved = bb.approved + 1, earned = bb.earned + r)
            }
            "correction" -> {
                correction++
                bb = bb.copy(correction = bb.correction + 1)
            }
            else -> {
                pending++
                pendingAmt += cur
                bb = bb.copy(pending = bb.pending + 1)
            }
        }
        byBook[p.bookId] = bb
    }
    for (x in payments) {
        when (x.status) {
            "paid" -> paid += x.amount
            "requested" -> requested += x.amount
        }
    }
    earned = r2(earned)
    paid = r2(paid)
    requested = r2(requested)
    return Summary(
        pages = pages,
        approved = approved,
        pending = pending,
        correction = correction,
        earned = earned,
        pendingAmt = r2(pendingAmt),
        paid = paid,
        requested = requested,
        available = max(0.0, r2(earned - paid - requested)),
        byBook = byBook
    )
}

// ---------------------------------------------------------------------------
// Streak / daily goal
// ---------------------------------------------------------------------------
data class StreakInfo(val today: Int, val current: Int, val best: Int, val doneToday: Boolean)

private fun dayNumber(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

fun startOfToday(): Long =
    java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun startOfDaysAgo(days: Long): Long =
    java.time.LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun streakInfo(proofs: List<Proof>): StreakInfo {
    val days = HashSet<Long>()
    val t0 = startOfToday()
    var today = 0
    for (p in proofs) {
        val t = p.createdAt ?: continue
        days.add(dayNumber(t))
        if (t >= t0) today++
    }
    val todayN = dayNumber(System.currentTimeMillis())
    val doneToday = days.contains(todayN)

    var cur = 0
    var d = if (doneToday) todayN else todayN - 1
    while (days.contains(d)) {
        cur++
        d--
    }

    var best = 0
    var run = 0
    var prev: Long? = null
    for (n in days.sorted()) {
        run = if (prev != null && n == prev + 1) run + 1 else 1
        prev = n
        if (run > best) best = run
    }
    return StreakInfo(today, cur, best, doneToday)
}

/** Does this list carry usable dates? (old databases without created_at -> no streak card) */
fun hasDates(proofs: List<Proof>): Boolean = proofs.isEmpty() || proofs.first().createdAt != null

/** "Continue": last page opened on this phone, else the last proofed book. */
data class ResumeTarget(val book: Book, val stem: String)

fun resumeTarget(
    resume: Pair<String, String>?,
    booksById: Map<String, Book>,
    proofs: List<Proof>
): ResumeTarget? {
    if (resume != null) booksById[resume.first]?.let { return ResumeTarget(it, resume.second) }
    val last = proofs.firstOrNull()
    if (last != null && last.createdAt != null) {
        booksById[last.bookId]?.let { return ResumeTarget(it, "") }
    }
    return null
}

// ---------------------------------------------------------------------------
// Bangla numbers / money / dates
// ---------------------------------------------------------------------------
private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

fun String.toBn(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c)
    return sb.toString()
}

/** 1234567 -> "12,34,567" (Bangladesh grouping) */
private fun groupBd(intPart: String): String {
    if (intPart.length <= 3) return intPart
    val last3 = intPart.takeLast(3)
    val rest = intPart.dropLast(3)
    val sb = StringBuilder()
    rest.reversed().chunked(2).forEachIndexed { i, chunk ->
        if (i > 0) sb.append(',')
        sb.append(chunk)
    }
    return sb.toString().reversed() + "," + last3
}

/** 1234 -> "১,২৩৪" */
fun bn(n: Int): String = bn(n.toLong())

fun bn(n: Long): String {
    val neg = n < 0
    val s = groupBd(kotlin.math.abs(n).toString())
    return ((if (neg) "-" else "") + s).toBn()
}

/** 12.5 -> "১২.৫০ ৳" */
fun money(x: Double): String {
    val v = r2(x)
    val text = String.format(Locale.US, "%.2f", kotlin.math.abs(v))
    val (i, f) = text.split('.')
    return ((if (v < 0) "-" else "") + groupBd(i) + "." + f).toBn() + " ৳"
}

/** rate text, e.g. "০.২০ ৳" */
fun rateTxt(r: Double): String = String.format(Locale.US, "%.2f", r).toBn() + " ৳"

private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("bn-BD"))

/** "২৪ অক্টোবর, ৯:৪০ PM" in Bangla */
fun fmtDT(millis: Long?): String {
    if (millis == null) return "-"
    return runCatching {
        DATE_TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).toBn()
    }.getOrDefault("-")
}

/** Pages needed to earn [missing] taka at [rate]. */
fun pagesFor(missing: Double, rate: Double): Int =
    if (rate <= 0.0) 0 else ceil(max(0.0, missing) / rate).toInt()

fun clampPct(done: Int, total: Int): Float =
    if (total <= 0) 0f else min(1f, done.toFloat() / total)

fun floorInt(x: Double): Int = floor(x).toInt()

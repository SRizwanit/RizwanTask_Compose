package com.srizwan.task.screens.task

import com.srizwan.task.core.AppToast
import com.srizwan.task.core.DEF_MIN
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * React task/helpers.js (3-খ-১): the pure (UI-free) helpers of the Task dashboard — daily goal, streak, badges,
 * "goal reached" toast and the "continue" target. localStorage -> [Prefs] (same key names as the React app).
 */

/** React `BADGES`: (days in a row, label). */
val BADGES: List<Pair<Int, String>> = listOf(
    3 to "শুরু", 7 to "এক সপ্তাহ", 15 to "পনেরো দিন", 30 to "এক মাস", 60 to "দুই মাস", 100 to "শতক"
)

/** React `PM`: payment method -> label. */
val PM = mapOf("bkash" to "বিকাশ", "nagad" to "নগদ")

/** React `minPayout(S)`: the minimum payout from the settings, else the default (50). */
fun minPayout(S: JSONObject): Double = S.finiteNum("min_payout") ?: DEF_MIN.toDouble()

/** React `KIND`: notice kind -> label. */
val NOTICE_KIND = mapOf("notice" to "ঘোষণা", "review" to "রিভিউ", "payment" to "পেমেন্ট")

/** React `rateTxt`: "১২.০০ পয়েন্টস". */
fun rateTxt(r: Double): String = Fmt.money(r)

/* ---- Prefs keys (one set per user) ---- */
fun resumeKey(id: String) = "kp_resume_v1_$id"
fun goalKey(id: String) = "kp_goal_v1_$id"
fun noticeKey(id: String) = "kp_notice_seen_$id"
fun guideKey(id: String) = "kp_guide_v2_$id"

/** React `readGoal`: the saved daily goal (whole number > 0), else 20. */
fun readGoal(id: String): Int {
    val g = Math.floor(Prefs.getString(goalKey(id))?.trim()?.toDoubleOrNull() ?: 0.0)
    return if (g.isFinite() && g > 0) g.toInt() else 20
}

fun writeGoal(id: String, g: Int) = Prefs.putString(goalKey(id), g.toString())

/** React `getResume`: where I stopped last — `{ b: bookId, s: pageStem }`, written by the View screen (step 4). */
class ResumeRec(val b: String, val s: String)

fun getResume(id: String): ResumeRec? = try {
    val o = JSONObject(Prefs.getString(resumeKey(id)) ?: "null")
    if (o.has("b") && !o.isNull("b")) ResumeRec(o.opt("b").toString(), o.str("s") ?: "") else null
} catch (e: Exception) { null }

/* ---- streak ---- */
private val zone: ZoneId get() = ZoneId.systemDefault()
private fun dayOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

/** React `streakInfo` result: today's count, current streak, best streak, whether today is already counted. */
class Streak(val today: Int, val cur: Int, val best: Int, val doneToday: Boolean)

/** React `streakInfo(proofs)`: pages proofed today, consecutive days (current / best) from `created_at`. */
fun streakInfo(proofs: List<JSONObject>): Streak {
    val days = HashSet<LocalDate>()
    val t0 = Fmt.startOfDay().toInstant().toEpochMilli()
    var today = 0
    for (p in proofs) {
        val t = Fmt.parseInstant(p.str("created_at"))?.toEpochMilli() ?: continue
        days.add(dayOf(t))
        if (t >= t0) today++
    }
    val now = LocalDate.now(zone)
    val doneToday = days.contains(now)
    var cur = 0
    var d = if (doneToday) now else now.minusDays(1)
    while (days.contains(d)) { cur++; d = d.minusDays(1) }
    val nums = days.map { it.toEpochDay() }.sorted()
    var best = 0
    var run = 0
    var prev: Long? = null
    for (n in nums) {
        run = if (prev != null && n == prev + 1) run + 1 else 1
        prev = n
        if (run > best) best = run
    }
    return Streak(today, cur, best, doneToday)
}

/** React `goalToast`: once a day, when today's goal is reached. Skipped on an old schema (no `created_at` column). */
fun goalToast(proofs: List<JSONObject>, goal: Int, userId: String) {
    if (proofs.isEmpty() || !proofs[0].has("created_at")) return
    val s = streakInfo(proofs)
    val k = "kp_goalhit_v1_${userId}_${LocalDate.now(zone)}"
    if (s.today >= goal) {
        if (Prefs.getString(k) != null) return
        Prefs.putString(k, "1")
        AppToast.show("মাশাআল্লাহ! আজকের লক্ষ্য পূরণ হয়েছে (${Fmt.bn(s.today)}/${Fmt.bn(goal)} পেইজ)")
    }
}

/** React `resumeTarget` result. */
class ResumeTarget(val book: JSONObject, val bid: String, val stem: String)

/**
 * React `resumeTarget`: the book/page recorded on this device, else the book of my latest proof
 * (the first row — proofs are newest first).
 */
fun resumeTarget(resume: ResumeRec?, booksById: Map<String, JSONObject>, proofs: List<JSONObject>): ResumeTarget? {
    if (resume != null) booksById[resume.b]?.let { return ResumeTarget(it, resume.b, resume.s) }
    val p = proofs.firstOrNull()
    if (p != null && p.str("created_at") != null) {
        val bid = p.opt("book_id")?.toString()
        if (bid != null) booksById[bid]?.let { return ResumeTarget(it, bid, "") }
    }
    return null
}

/* ---- notices: which ones this user has already seen (kept on this device) ---- */

/** React `readSeen`: ids (as Strings) of the notices already shown. */
fun readSeen(id: String): MutableSet<String> = try {
    val a = JSONArray(Prefs.getString(noticeKey(id)) ?: "[]")
    LinkedHashSet<String>().also { set -> for (i in 0 until a.length()) set.add(a.opt(i).toString()) }
} catch (e: Exception) { LinkedHashSet() }

/** React `writeSeen`: keeps the newest 300. */
fun writeSeen(id: String, s: Set<String>) {
    try { Prefs.putString(noticeKey(id), JSONArray(s.toList().takeLast(300)).toString()) } catch (e: Exception) { /* ignore */ }
}

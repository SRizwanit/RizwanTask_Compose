package com.srizwan.task.screens.admin

import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** React admin/helpers.js — constants and pure helpers (GitHub Actions / local prefs come in step 2-ঝ). */

val PM = mapOf("bkash" to "বিকাশ", "nagad" to "নগদ")

val DEF = mapOf(
    "github_username" to "SunniPedia", "repo_name" to "pdftojpg", "pages_dir" to "pages",
    "texts_dir" to "texts", "proof_file" to "proofed.json", "proof_dir" to "proofed"
)

const val SQLHINT = "Supabase SQL Editor-এ rizwantask_full_schema.sql চালান"

/** review status -> (label, tone: ok / warn / bad) */
val STB = mapOf(
    "pending" to ("রিভিউ বাকি" to "warn"),
    "approved" to ("অনুমোদিত" to "ok"),
    "correction" to ("সংশোধন দরকার" to "bad")
)

val TKST = mapOf(
    "open" to ("খোলা" to "ok"),
    "pending" to ("পেন্ডিং" to "warn"),
    "closed" to ("বন্ধ" to "bad")
)

val ACT = mapOf(
    "login" to "লগইন", "logout" to "লগআউট", "signup" to "নতুন অ্যাকাউন্ট", "proof" to "প্রুফড চিহ্ন",
    "unproof" to "প্রুফড তোলা", "resubmit" to "সংশোধন করে আবার জমা",
    "payment_request" to "পেমেন্ট রিকোয়েস্ট", "payment_number_changed" to "পেমেন্ট নাম্বার বদল",
    "ticket_open" to "টিকেট খোলা",
    "admin_approve" to "[অ্যাডমিন] পেইজ অনুমোদন", "admin_correction" to "[অ্যাডমিন] সংশোধন দরকার",
    "admin_pay" to "[অ্যাডমিন] পেমেন্ট পরিশোধ", "admin_reject" to "[অ্যাডমিন] পেমেন্ট বাতিল",
    "admin_block" to "[অ্যাডমিন] ব্লক", "admin_unblock" to "[অ্যাডমিন] আনব্লক",
    "admin_notice" to "[অ্যাডমিন] নোটিশ", "admin_rate" to "[অ্যাডমিন] রেট বদল",
    "admin_pin" to "[অ্যাডমিন] PIN", "admin_device" to "[অ্যাডমিন] ডিভাইস রিসেট",
    "admin_delete_user" to "[অ্যাডমিন] ইউজার ডিলিট", "admin_settings" to "[অ্যাডমিন] সেটিংস"
)

// ---- small utilities ----
fun tm(t: String?): Long = Fmt.parseInstant(t)?.toEpochMilli() ?: 0L
fun t0(): Long = Fmt.startOfDay().toInstant().toEpochMilli()
fun d7(): Long = Fmt.startOfDay().minusDays(6).toInstant().toEpochMilli()
fun sl(v: String?): String = (v ?: "").trim().trim('/')
private val fullFmt: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.forLanguageTag("bn-BD"))

/** new Date(t).toLocaleString('bn-BD') — date + time with the year. */
fun fmtFull(t: String?): String =
    Fmt.parseInstant(t)?.atZone(java.time.ZoneId.systemDefault())?.format(fullFmt) ?: "-"

fun rateTxt(r: Double): String = Fmt.money(r)

fun blkErr(message: String?): String {
    val m = message.orEmpty()
    return if (Regex("is_blocked|blocked_reason", RegexOption.IGNORE_CASE).containsMatchIn(m)) "ব্লক কলাম নেই — $SQLHINT" else m
}

fun noTbl(e: Throwable?): Boolean =
    Regex("PGRST205|42P01|PGRST200|404|does not exist", RegexOption.IGNORE_CASE).containsMatchIn(e?.message.orEmpty())

fun rvKey(r: JSONObject): String = (r.opt("book_id")?.toString() ?: "null") + "\u0001" + (r.str("stem") ?: "")

fun status(p: JSONObject): String = p.str("review_status")?.takeIf { it.isNotEmpty() } ?: "pending"

/** (warn %, warn minimum) */
fun warnLimits(S: JSONObject): Pair<Double, Double> =
    (S.finiteNum("warn_pct") ?: 20.0) to (S.finiteNum("warn_min") ?: 5.0)

private fun String?.nz(): String? = this?.takeIf { it.isNotEmpty() }

/** Book value, else settings value, else default. */
fun eff(S: JSONObject, b: JSONObject, k: String): String = b.str(k).nz() ?: S.str(k).nz() ?: DEF[k] ?: ""
fun prefixOf(b: JSONObject): String = sl(b.str("folder").nz() ?: b.str("name"))
fun outDirOf(S: JSONObject, b: JSONObject): String = prefixOf(b) + "_" + (S.str("pages_dir").nz() ?: DEF.getValue("pages_dir"))

/** What is written to a page when a review approves / asks for a correction. */
fun bodyFor(S: JSONObject, st: String, note: String?, bk: JSONObject?): JSONObject {
    val now = Instant.now().toString()
    return if (st == "approved") {
        jsonOf("review_status" to "approved", "review_note" to null, "reviewed_at" to now, "rate" to Earnings.rateOf(bk, S))
    } else {
        jsonOf("review_status" to "correction", "review_note" to note?.takeIf { it.isNotEmpty() }, "reviewed_at" to now, "rate" to null)
    }
}

/** Immutable `{...this, ...patch}`. */
fun JSONObject.copyWith(patch: JSONObject): JSONObject {
    val o = JSONObject(toString())
    val it = patch.keys()
    while (it.hasNext()) {
        val k = it.next()
        o.put(k, if (patch.isNull(k)) JSONObject.NULL else patch.opt(k))
    }
    return o
}

fun JSONObject.idStr(): String = opt("id")?.toString() ?: ""

/** Send a notice (user_id null = everyone). */
suspend fun sendNotice(userId: String?, title: String, body: String? = null, kind: String = "notice"): JSONArray =
    Sb.post("notices", jsonOf("user_id" to userId?.takeIf { it.isNotEmpty() }, "title" to title, "body" to body?.takeIf { it.isNotEmpty() }, "kind" to kind))

/** Image-creation workflow file name (React localStorage kp_wf_file_v1); blank = default. Used by Settings and Books (2-ঝ). */
const val WF_DEF = "ultimatepdftojpg(2).yml"
private const val WK = "kp_wf_file_v1"

fun wfFile(): String = (Prefs.getString(WK) ?: "").trim().ifEmpty { WF_DEF }

fun saveWfFile(v: String) {
    val t = v.trim()
    if (t.isNotEmpty()) Prefs.putString(WK, t) else Prefs.remove(WK)
}

/** JS String(number): 50.0 -> "50", 0.2 -> "0.2"; text stays as is; null -> "". */
fun numStr(v: Any?): String = when {
    v == null || v == JSONObject.NULL -> ""
    v is Number -> {
        val d = v.toDouble()
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }
    else -> v.toString()
}

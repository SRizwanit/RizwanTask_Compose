package com.srizwan.task.core

import org.json.JSONObject

/**
 * common.js earnings: rate = book.page_rate, else the default page_rate from settings (0.20 পয়েন্টস).
 * Only "approved" pages earn (the rate at approval time is stored in proofs.rate); pending pages are shown
 * separately; "correction" pages earn nothing.
 */
const val DEF_RATE = 0.2
const val DEF_MIN = 50

class BookSum(
    var n: Int = 0,
    var approved: Int = 0,
    var pending: Int = 0,
    var correction: Int = 0,
    var earned: Double = 0.0
)

class Summary {
    var pages = 0
    var approved = 0
    var pending = 0
    var correction = 0
    var earned = 0.0
    var pendingAmt = 0.0
    var paid = 0.0
    var requested = 0.0
    var available = 0.0
    val byBook = LinkedHashMap<String, BookSum>()
}

object Earnings {
    fun rateOf(book: JSONObject?, settings: JSONObject?): Double =
        book?.finiteNum("page_rate") ?: settings?.finiteNum("page_rate") ?: DEF_RATE

    fun summarize(
        proofs: List<JSONObject>,
        booksById: Map<String, JSONObject>,
        settings: JSONObject?,
        payments: List<JSONObject>
    ): Summary {
        val o = Summary()
        for (p in proofs) {
            val st = p.str("review_status")?.takeIf { it.isNotEmpty() } ?: "pending"
            val bookKey = p.opt("book_id")?.toString() ?: "null"
            val cur = rateOf(booksById[bookKey], settings)
            val bb = o.byBook.getOrPut(bookKey) { BookSum() }
            o.pages++; bb.n++
            when (st) {
                "approved" -> {
                    val r = p.finiteNum("rate") ?: cur
                    o.approved++; bb.approved++
                    o.earned += r; bb.earned += r
                }
                "correction" -> { o.correction++; bb.correction++ }
                else -> { o.pending++; bb.pending++; o.pendingAmt += cur }
            }
        }
        for (x in payments) {
            when (x.str("status")) {
                "paid" -> o.paid += x.finiteNum("amount") ?: 0.0
                "requested" -> o.requested += x.finiteNum("amount") ?: 0.0
            }
        }
        o.earned = Fmt.r2(o.earned)
        o.pendingAmt = Fmt.r2(o.pendingAmt)
        o.paid = Fmt.r2(o.paid)
        o.requested = Fmt.r2(o.requested)
        o.available = maxOf(0.0, Fmt.r2(o.earned - o.paid - o.requested))
        return o
    }
}

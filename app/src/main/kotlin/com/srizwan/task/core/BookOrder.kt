package com.srizwan.task.core

import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/**
 * One fixed order for books everywhere (Task grid, admin Books tab): newest first by `created_at`.
 * Books saved together (bulk import) share the same / near-same time, so ties are broken by a natural
 * Bengali name order and finally by id — the list never shuffles between loads.
 */
object BookOrder {
    private val collator: Collator by lazy { Collator.getInstance(Locale.forLanguageTag("bn")) }

    /** Newest first; equal times -> name (A→Z), then id. */
    val newestFirst: Comparator<JSONObject> = Comparator { a, b ->
        val ca = a.str("created_at").orEmpty()
        val cb = b.str("created_at").orEmpty()
        val ta = Fmt.parseInstant(ca)
        val tb = Fmt.parseInstant(cb)
        val byTime = if (ta != null && tb != null) tb.compareTo(ta) else cb.compareTo(ca)
        if (byTime != 0) byTime else {
            val byName = collator.compare(a.str("name").orEmpty(), b.str("name").orEmpty())
            if (byName != 0) byName else (a.opt("id")?.toString() ?: "").compareTo(b.opt("id")?.toString() ?: "")
        }
    }

    fun sortNewestFirst(list: List<JSONObject>): List<JSONObject> = list.sortedWith(newestFirst)

    /**
     * Import: gives rows a created_at 1 ms apart, the FIRST file row newest, so "newest first" shows the
     * books exactly in the order of the file (1, 2, 3 …) instead of a random order.
     */
    fun stampInFileOrder(rows: List<JSONObject>): List<JSONObject> {
        val base = System.currentTimeMillis()
        return rows.mapIndexed { i, r ->
            JSONObject(r.toString()).also { it.put("created_at", java.time.Instant.ofEpochMilli(base - i).toString()) }
        }
    }
}

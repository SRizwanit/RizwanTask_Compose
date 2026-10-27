package com.srizwan.task.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** common.js number / money / date helpers (Bangla digits, bn-BD). */
object Fmt {
    private val bnBD: Locale = Locale.forLanguageTag("bn-BD")

    /** JS Math.round (half up), 2 decimals */
    fun r2(x: Double?): Double {
        val v = x ?: 0.0
        return Math.floor(v * 100 + 0.5) / 100
    }

    fun bn(n: Number?): String = NumberFormat.getInstance(bnBD).format((n ?: 0).toDouble())

    fun money(x: Double?): String {
        val nf = NumberFormat.getInstance(bnBD)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return nf.format(r2(x)) + " পয়েন্টস"
    }

    /** Supabase timestamps: "2026-10-26T10:20:30.123+00:00", "...Z", without offset, or a plain date. */
    fun parseInstant(t: String?): Instant? {
        if (t.isNullOrBlank()) return null
        val s = t.trim()
        try { return OffsetDateTime.parse(s).toInstant() } catch (e: Exception) { /* next */ }
        try { return Instant.parse(s) } catch (e: Exception) { /* next */ }
        try { return LocalDateTime.parse(s.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant() } catch (e: Exception) { /* next */ }
        try { return LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant() } catch (e: Exception) { /* next */ }
        return null
    }

    private val dFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", bnBD)
    private val dtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", bnBD)

    fun fmtD(t: String?): String =
        parseInstant(t)?.atZone(ZoneId.systemDefault())?.format(dFmt) ?: "-"

    fun fmtDT(t: String?): String =
        parseInstant(t)?.atZone(ZoneId.systemDefault())?.format(dtFmt) ?: "-"

    fun startOfDay(t: String? = null): ZonedDateTime {
        val zone = ZoneId.systemDefault()
        val base = parseInstant(t)?.atZone(zone) ?: ZonedDateTime.now(zone)
        return base.toLocalDate().atStartOfDay(zone)
    }
}

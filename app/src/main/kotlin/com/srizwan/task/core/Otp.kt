package com.srizwan.task.core

import java.time.Instant

/** OTP pool (table `otp_codes`, same flow as the app): the admin fills the pool, the user is assigned one code for 2 minutes. */
object Otp {
    suspend fun request(phone: String): String {
        val a = Sb.get("otp_codes?status=eq.available&limit=1&order=id.asc")
        if (a.length() == 0) throw SbException("OTP পুল খালি, অ্যাডমিনকে জানান")
        val row = a.getJSONObject(0)
        val now = Instant.now()
        val exp = now.plusSeconds(2 * 60).toString()
        Sb.patch(
            "otp_codes?id=eq.${row.opt("id")}",
            jsonOf("status" to "assigned", "phone" to phone, "assigned_at" to now.toString(), "expires_at" to exp)
        )
        return row.str("code").orEmpty()
    }

    suspend fun verify(phone: String, code: String): Boolean {
        val r = Sb.get("otp_codes?phone=eq.${enc(phone)}&code=eq.${enc(code)}&status=eq.assigned&order=id.desc&limit=1")
        if (r.length() == 0) return false
        val o = r.getJSONObject(0)
        val id = o.opt("id")
        val exp = Fmt.parseInstant(o.str("expires_at"))
        if (exp != null && exp.toEpochMilli() < System.currentTimeMillis()) {
            Sb.patch("otp_codes?id=eq.$id", jsonOf("status" to "expired"))
            return false
        }
        Sb.patch("otp_codes?id=eq.$id", jsonOf("status" to "verified"))
        return true
    }
}

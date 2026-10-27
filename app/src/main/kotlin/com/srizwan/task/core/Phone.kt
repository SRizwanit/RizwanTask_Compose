package com.srizwan.task.core

data class PhoneResult(val ok: Boolean, val phone: String, val msg: String, val foreign: Boolean)

/** common.js `Phone` (port of SupabaseClient.PhoneValidator). */
object Phone {
    private val WS = Regex("[\\s-]")

    fun isBD(raw: String): Boolean {
        val p = raw.trim().replace(WS, "")
        return p.startsWith("01") || p.startsWith("+8801") || p.startsWith("8801") || p.startsWith("+880")
    }

    fun normBD(raw: String): String {
        var p = raw.trim().replace(WS, "")
        if (p.startsWith("+880")) p = "0" + p.substring(4)
        else if (p.startsWith("880")) p = "0" + p.substring(3)
        return p
    }

    fun validate(raw: String?): PhoneResult {
        val t = (raw ?: "").trim()
        if (t.isEmpty()) return PhoneResult(false, "", "ফোন নাম্বার দিন", false)
        val c = t.replace(WS, "")
        if (isBD(t)) {
            val n = normBD(t)
            if (n.length != 11) return PhoneResult(false, n, "বাংলাদেশি নাম্বার ১১ ডিজিটের হতে হবে (যেমন: 017XXXXXXXX)", false)
            if (!Regex("^01[0-9]{9}$").matches(n)) return PhoneResult(false, n, "সঠিক বাংলাদেশি নাম্বার দিন", false)
            return PhoneResult(true, n, "OK", false)
        }
        val f = c.replaceFirst("+", "")
        if (!Regex("^[0-9]{7,15}$").matches(f)) return PhoneResult(false, t, "সঠিক বিদেশি নাম্বার দিন (৭-১৫ ডিজিট, + সহ)", true)
        return PhoneResult(true, if (c.startsWith("+")) c else "+$f", "OK", true)
    }

    /** "01712345678" -> "017*****78" (shown when this device already belongs to another account). */
    fun mask(p: String?): String {
        val t = p.orEmpty().trim()
        if (t.length < 6) return t
        return t.take(3) + "*".repeat(t.length - 5) + t.takeLast(2)
    }

    fun isAdminPhone(p: String?): Boolean = !p.isNullOrEmpty() && normBD(p) == Cfg.ADMIN_PHONE
}

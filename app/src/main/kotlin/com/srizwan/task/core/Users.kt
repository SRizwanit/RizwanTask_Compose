package com.srizwan.task.core

import org.json.JSONObject

/** A row of `app_users`. The raw JSON is kept so later screens can read any column. */
class AppUser(val raw: JSONObject) {
    val id: String get() = raw.opt("id")?.toString() ?: ""
    val phone: String? get() = raw.str("phone")
    val fullName: String? get() = raw.str("full_name")
    var deviceId: String?
        get() = raw.str("device_id")
        set(v) { raw.put("device_id", v ?: JSONObject.NULL) }
    val passwordHash: String? get() = raw.str("password_hash")
    val passwordSalt: String? get() = raw.str("password_salt")
    val isBlocked: Boolean get() = raw.optBoolean("is_blocked", false)
    val blockedReason: String? get() = raw.str("blocked_reason")
    val paymentMethod: String? get() = raw.str("payment_method")
    val paymentNumber: String? get() = raw.str("payment_number")
}

object Users {
    suspend fun findByPhone(phone: String): AppUser? {
        val n = Phone.normBD(phone)
        var r = Sb.get("app_users?phone=eq.${enc(phone)}&limit=1")
        if (r.length() == 0 && n != phone) r = Sb.get("app_users?phone=eq.${enc(n)}&limit=1")
        return r.optJSONObject(0)?.let { AppUser(it) }
    }

    /**
     * The account (other than [excludeId]) that is bound to this phone. Looks for the new hardware id AND the old
     * random id, so an old install is found too.
     */
    suspend fun findByDevice(dev: String, excludeId: String? = null): AppUser? {
        val ids = (Device.all() + dev).filter { !Device.isEmpty(it) }.distinct()
        val inList = ids.joinToString(",") { "\"" + it + "\"" }
        var p = "app_users?device_id=in.(" + enc(inList) + ")&limit=1"
        if (!excludeId.isNullOrEmpty()) p += "&id=neq.${enc(excludeId)}"
        return Sb.get(p).optJSONObject(0)?.let { AppUser(it) }
    }

    suspend fun byId(id: String): AppUser? =
        Sb.get("app_users?id=eq.${enc(id)}&limit=1").optJSONObject(0)?.let { AppUser(it) }
}

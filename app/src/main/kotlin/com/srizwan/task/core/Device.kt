package com.srizwan.task.core

import java.util.UUID

/** One account per device: a random id is created once and stored (localStorage kp_device_v1). */
object Device {
    private const val DK = "kp_device_v1"

    fun id(): String {
        var d = Prefs.getString(DK).orEmpty()
        if (d.isEmpty()) {
            d = UUID.randomUUID().toString()
            Prefs.putString(DK, d)
        }
        return d
    }

    fun isEmpty(d: String?): Boolean = d == null || d.trim().isEmpty() || d.trim().lowercase() == "null"
}

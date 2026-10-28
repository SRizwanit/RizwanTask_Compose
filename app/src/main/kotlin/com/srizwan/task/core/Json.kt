package com.srizwan.task.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** JS encodeURIComponent */
fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    .replace("+", "%20").replace("%21", "!").replace("%27", "'")
    .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

/** JSONObject.put(key, null) would REMOVE the key, so null is stored as JSONObject.NULL. */
fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) o.put(k, v ?: JSONObject.NULL)
    return o
}

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

/** String value, or null when the key is missing / JSON null. */
fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return opt(key)?.toString()
}

/** Finite number from a number or numeric string; null otherwise (JS Number.isFinite(Number(x)) with '' excluded). */
fun JSONObject.finiteNum(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toDouble().takeIf { it.isFinite() }
        is String -> if (v.isEmpty()) null else v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}

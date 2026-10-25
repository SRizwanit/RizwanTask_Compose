package com.srizwan.task

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Small per-user settings that live only on this phone
 * (same things the web app keeps in localStorage):
 *  - daily goal
 *  - which notices were already shown
 *  - first-time guide seen
 *  - "goal reached" toast (once a day)
 *  - resume point (last book + page) - the View page will write it in a later step
 */
class TaskPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rizwantask_task", Context.MODE_PRIVATE)

    // ---- daily goal -----------------------------------------------------
    fun goal(userId: String): Int {
        val g = prefs.getInt("goal_$userId", 0)
        return if (g > 0) g else DEFAULT_GOAL
    }

    fun setGoal(userId: String, value: Int) {
        prefs.edit().putInt("goal_$userId", value).apply()
    }

    // ---- notices already seen ------------------------------------------
    fun seenNotices(userId: String): MutableSet<String> = runCatching {
        val arr = JSONArray(prefs.getString("seen_$userId", "[]"))
        MutableList(arr.length()) { arr.optString(it) }.toMutableSet()
    }.getOrDefault(mutableSetOf())

    fun saveSeenNotices(userId: String, seen: Set<String>) {
        val arr = JSONArray()
        seen.toList().takeLast(300).forEach { arr.put(it) }
        prefs.edit().putString("seen_$userId", arr.toString()).apply()
    }

    // ---- first-time guide ------------------------------------------------
    fun guideSeen(userId: String): Boolean = prefs.getBoolean("guide_v2_$userId", false)

    fun setGuideSeen(userId: String) {
        prefs.edit().putBoolean("guide_v2_$userId", true).apply()
    }

    // ---- "goal reached" toast: once per day ---------------------------------
    fun goalToastShownToday(userId: String): Boolean =
        prefs.getString("goalhit_$userId", null) == LocalDate.now().toString()

    fun markGoalToastShown(userId: String) {
        prefs.edit().putString("goalhit_$userId", LocalDate.now().toString()).apply()
    }

    // ---- resume point ----------------------------------------------------------
    /** (bookId, pageStem) of the last page opened on this phone, or null. */
    fun resume(userId: String): Pair<String, String>? = runCatching {
        val j = JSONObject(prefs.getString("resume_$userId", null) ?: return null)
        val book = j.optString("b")
        if (book.isBlank()) null else book to j.optString("s")
    }.getOrNull()

    /** Called by the View page every time the page changes. */
    fun setResume(userId: String, bookId: String, stem: String) {
        val j = JSONObject().put("b", bookId).put("s", stem)
        prefs.edit().putString("resume_$userId", j.toString()).apply()
    }

    companion object {
        const val DEFAULT_GOAL = 20
    }
}

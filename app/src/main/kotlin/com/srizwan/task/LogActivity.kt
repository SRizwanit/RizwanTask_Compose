package com.srizwan.task

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.srizwan.task.core.CrashHandler
import com.srizwan.task.core.Crumb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "RizwanTask Log": a second launcher icon. It opens even when the app itself closes at start, because it runs in its own
 * ":log" process, uses plain Android views (no Compose, no app services) and only reads files / system info:
 *   - the saved crash report (if the app died with a Java/Kotlin exception),
 *   - the startup steps of the last two launches (how far the app got),
 *   - Android's own exit history of the app (ApplicationExitInfo, Android 11+): crash / native crash / killed / ANR ...
 */
class LogActivity : Activity() {
    private lateinit var body: TextView
    private lateinit var note: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E1712.toInt())
            fitsSystemWindows = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(14), px(14), px(14))
        }
        outer.addView(content, LinearLayout.LayoutParams(match, match))

        content.addView(TextView(this).apply {
            text = "RizwanTask Log"
            setTextColor(0xFFF2D27A.toInt()); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "অ্যাপ চালু হওয়ার সময়কার রেকর্ড: ক্র্যাশ, চালুর ধাপ ও বন্ধ হওয়ার কারণ।"
            setTextColor(0xFF9DB5A6.toInt()); textSize = 13f
            setPadding(0, px(2), 0, px(8))
        })

        body = TextView(this).apply {
            setTextColor(0xFFF3FFF9.toInt()); textSize = 11f; typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        content.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(match, 0, 1f))

        note = TextView(this).apply {
            setTextColor(0xFFF2D27A.toInt()); textSize = 12f
            setPadding(0, px(6), 0, px(2))
        }
        content.addView(note)

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        content.addView(button("WhatsApp-এ রিপোর্ট পাঠান") {
            val ok = CrashHandler.sendToWhatsApp(this, shortText())
            note.text = if (ok) "" else {
                copy(build(true)); "WhatsApp খোলা যায়নি, রিপোর্ট কপি করা হয়েছে।"
            }
        }, LinearLayout.LayoutParams(match, wrap))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("কপি") {
            note.text = if (copy(build(true))) "রিপোর্ট কপি হয়েছে।" else "কপি করা যায়নি।"
        }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(button("রিফ্রেশ") { refresh() }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(button("মুছুন") {
            CrashHandler.clear(this); refresh(); note.text = "রেকর্ড মুছে ফেলা হয়েছে।"
        }, LinearLayout.LayoutParams(0, wrap, 1f))
        content.addView(row, LinearLayout.LayoutParams(match, wrap))

        setContentView(outer)
        refresh()
    }

    private fun refresh() { body.text = build(true) }

    private fun copy(text: String): Boolean = try {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("log", text))
        true
    } catch (_: Throwable) {
        false
    }

    private fun time(ms: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun header(): String =
        "RizwanTask startup log\n" +
            "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n"

    private fun reasonName(r: Int): String = when (r) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        else -> "reason $r"
    }

    /** Android's own record of why the app's processes ended (newest first). */
    private fun exits(max: Int): String {
        if (Build.VERSION.SDK_INT < 30) return "(Android 11+ only)\n"
        return try {
            val am = getSystemService(ActivityManager::class.java)
            val list = am.getHistoricalProcessExitReasons(packageName, 0, max)
            if (list.isEmpty()) "(none)\n" else list.joinToString("\n", postfix = "\n") { e ->
                "${time(e.timestamp)} ${e.processName} ${reasonName(e.reason)} status=${e.status}" +
                    (e.description?.let { " | $it" } ?: "")
            }
        } catch (t: Throwable) {
            "(cannot read: ${t.javaClass.simpleName})\n"
        }
    }

    private fun build(full: Boolean): String {
        val crash = CrashHandler.read(this)
        val (cur, prev) = Crumb.read(this)
        return buildString {
            append(header())
            append("\n== Exit history ==\n").append(exits(6))
            append("\n== Startup steps: last launch ==\n").append(cur.ifBlank { "(none)\n" })
            if (full && prev.isNotBlank()) append("\n== Startup steps: launch before ==\n").append(prev)
            append("\n== Last crash ==\n").append(crash?.full ?: "(none)\n")
        }
    }

    /** Short text for the WhatsApp chat link (long links fail). */
    private fun shortText(): String {
        val crash = CrashHandler.read(this)
        val (cur, _) = Crumb.read(this)
        val t = buildString {
            append(header())
            append("Exit:\n").append(exits(3))
            append("Steps:\n").append(cur.trim().lines().takeLast(10).joinToString("\n")).append('\n')
            append("Crash:\n").append(crash?.short?.take(800) ?: "(none)")
        }
        return if (t.length > 2000) t.take(2000) + "..." else t
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }
}

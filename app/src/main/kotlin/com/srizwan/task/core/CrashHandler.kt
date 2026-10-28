package com.srizwan.task.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import com.srizwan.task.BuildConfig
import com.srizwan.task.CrashActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * App crash -> debug screen ([CrashActivity], own ":crash" process) + report to WhatsApp.
 *
 * 1) [install] is the first call in RizwanApp.onCreate: any uncaught exception (any thread) is turned into a report,
 *    saved to filesDir/crash_report.json, then CrashActivity is started and the broken process is killed.
 * 2) CrashActivity reads the report with [read] and shows it; "send" opens WhatsApp chat with [WA_NUMBER]
 *    ([sendToWhatsApp]) with the short report already typed (the user only taps send in WhatsApp).
 */
object CrashHandler {
    /** Crash reports go to this WhatsApp number. */
    const val WA_NUMBER = "8801714656343"

    private const val FILE = "crash_report.json"
    private const val MAX_TRACE = 20000
    private const val MAX_WA_TEXT = 1700   // wa.me links break when the text is too long, so the chat text is the short report

    @Volatile private var handling = false

    class Data(
        val meta: List<Pair<String, String>>,
        val headline: String,
        val trace: String,
        val full: String,
        val short: String
    )

    /** True inside the ":crash" / ":log" processes (RizwanApp must not start the normal app services there). */
    fun isAuxProcess(): Boolean {
        val name: String = try {
            if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
            else File("/proc/self/cmdline").readText().trim('\u0000')
        } catch (_: Throwable) {
            ""
        }
        return name.endsWith(":crash") || name.endsWith(":log")
    }

    /** Deletes the saved crash report and the startup steps. */
    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE).delete() } catch (_: Throwable) { }
        Crumb.clear(ctx)
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            var shown = false
            if (!handling) {
                handling = true
                try {
                    save(app, build(app, thread, error))
                    shown = launch(app)
                } catch (_: Throwable) {
                    // never crash inside the crash handler
                }
            }
            if (shown) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Build the report
    // ---------------------------------------------------------------------------------------------
    private fun build(app: Application, thread: Thread, error: Throwable): Data {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val build = if (BuildConfig.DEBUG) "debug" else "release"
        val user = try { Session.get()?.id } catch (_: Throwable) { null }
        val heap = try {
            val rt = Runtime.getRuntime()
            "${(rt.totalMemory() - rt.freeMemory()) / 1048576} / ${rt.maxMemory() / 1048576} MB"
        } catch (_: Throwable) { "-" }

        val meta = listOf(
            "App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) $build",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Time" to time,
            "Thread" to thread.name,
            "Heap" to heap,
            "User" to (user ?: "-")
        )

        val chain = chainOf(error)
        val root = chain.last()
        val headline = label(error)

        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val trace = sw.toString().take(MAX_TRACE)

        val full = buildString {
            append("RizwanTask crash report\n")
            meta.forEach { append(it.first).append(": ").append(it.second).append('\n') }
            append('\n').append(trace)
        }

        val frames = root.stackTrace
            .filter { it.className.startsWith("com.srizwan.task") }
            .take(8)
            .ifEmpty { root.stackTrace.take(6) }
        val short = buildString {
            append("RizwanTask crash\n")
            append("v").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append(build).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ")
                .append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append(time).append(" | thread: ").append(thread.name).append(" | user: ").append(user ?: "-").append('\n')
            append("Error: ").append(headline).append('\n')
            if (root !== error) append("Root: ").append(label(root)).append('\n')
            append("At:\n")
            frames.forEach {
                append(" ").append(it.className.substringAfterLast('.')).append('.').append(it.methodName)
                    .append('(').append(it.fileName ?: "?").append(':').append(it.lineNumber).append(")\n")
            }
        }.let { if (it.length > MAX_WA_TEXT) it.take(MAX_WA_TEXT) + "..." else it }

        return Data(meta, headline, trace, full, short)
    }

    private fun chainOf(e: Throwable): List<Throwable> {
        val list = ArrayList<Throwable>()
        var c: Throwable? = e
        while (true) {
            val cur = c ?: break
            if (list.any { it === cur } || list.size >= 10) break
            list.add(cur)
            c = cur.cause
        }
        return list
    }

    private fun label(t: Throwable): String {
        val msg = t.message?.replace('\n', ' ')?.trim()?.take(200)
        return if (msg.isNullOrEmpty()) t.javaClass.name else "${t.javaClass.name}: $msg"
    }

    // ---------------------------------------------------------------------------------------------
    // Save / read
    // ---------------------------------------------------------------------------------------------
    private fun save(app: Application, d: Data) {
        val meta = JSONArray()
        d.meta.forEach { meta.put(JSONArray().put(it.first).put(it.second)) }
        val o = JSONObject()
            .put("meta", meta)
            .put("headline", d.headline)
            .put("trace", d.trace)
            .put("full", d.full)
            .put("short", d.short)
        File(app.filesDir, FILE).writeText(o.toString())
    }

    /** Latest saved report (null when there is none). Works in the ":crash" process too (plain file, no app services). */
    fun read(ctx: Context): Data? = try {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val m = o.getJSONArray("meta")
            val meta = (0 until m.length()).map { i -> m.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
            Data(meta, o.getString("headline"), o.getString("trace"), o.getString("full"), o.getString("short"))
        }
    } catch (_: Throwable) {
        null
    }

    private fun launch(app: Application): Boolean = try {
        val i = Intent(app, CrashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(i)
        true
    } catch (_: Throwable) {
        false
    }

    // ---------------------------------------------------------------------------------------------
    // WhatsApp
    // ---------------------------------------------------------------------------------------------
    /** Opens the chat with [WA_NUMBER] with [text] typed in. false when neither WhatsApp nor a browser could open it. */
    fun sendToWhatsApp(ctx: Context, text: String): Boolean {
        val t = Uri.encode(text)
        val links = listOf(
            "whatsapp://send?phone=$WA_NUMBER&text=$t",
            "https://wa.me/$WA_NUMBER?text=$t"
        )
        for (u in links) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Throwable) {
                // next
            }
        }
        return false
    }
}

/**
 * Startup steps: one line per step in filesDir (the file of the last launch + the launch before it).
 * If the app dies silently (no Java crash), the last line shows how far it got. Shown by LogActivity.
 */
object Crumb {
    private const val CUR = "startup_steps.txt"
    private const val PREV = "startup_steps_prev.txt"

    /** New launch: the old file becomes "previous", a fresh one is started. */
    fun start(ctx: Context) {
        try {
            val cur = File(ctx.filesDir, CUR)
            val prev = File(ctx.filesDir, PREV)
            if (cur.exists()) { prev.delete(); cur.renameTo(prev) }
            cur.writeText("launch " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "\n")
        } catch (_: Throwable) { }
    }

    fun mark(ctx: Context, step: String) {
        try {
            File(ctx.filesDir, CUR).appendText(SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()) + " " + step + "\n")
        } catch (_: Throwable) { }
    }

    /** (last launch, the launch before). */
    fun read(ctx: Context): Pair<String, String> = try {
        val c = File(ctx.filesDir, CUR).let { if (it.exists()) it.readText() else "" }
        val p = File(ctx.filesDir, PREV).let { if (it.exists()) it.readText() else "" }
        c to p
    } catch (_: Throwable) { "" to "" }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, CUR).delete() } catch (_: Throwable) { }
        try { File(ctx.filesDir, PREV).delete() } catch (_: Throwable) { }
    }
}

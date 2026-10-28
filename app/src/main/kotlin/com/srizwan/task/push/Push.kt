package com.srizwan.task.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.srizwan.task.MainActivity
import com.srizwan.task.R
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.Sb
import com.srizwan.task.core.Session
import com.srizwan.task.core.enc
import com.srizwan.task.core.objects
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Push notifications for new notices / payment results / review results / reward badges.
 *
 * Source of events: the existing `notices` table (rizwantask_full_schema.sql section 7):
 *   - `user_id` null  = a notice for everyone  -> every user gets a notification
 *   - `user_id` = me  = personal (kind `payment` / `review` / `reward` / `notice`) -> only that user gets it
 * The admin screens already insert these rows (sendNotice), and the SQL function `award_rewards()` inserts `reward` rows.
 *
 * Delivery without Firebase: a WorkManager periodic job ([PushWorker], every 15 minutes - the Android minimum - while online)
 * asks Supabase for notices newer than the last one notified and shows a system notification for each.
 * The app also syncs when it is opened (those notices are shown by the in-app notice dialog, so no duplicate notification).
 */
object Push {
    const val CHANNEL_ID = "rt_notices"
    private const val WORK_NAME = "rt_push_poll"
    private const val K_ON = "kp_push_on_v1"           // "0" = user turned it off
    private const val K_ASKED = "kp_push_asked_v1"     // how many times the permission rationale was shown
    private fun lastKey(uid: String) = "kp_push_last_v1_$uid"

    private lateinit var app: Application

    /** Called once from [com.srizwan.task.RizwanApp]. */
    fun init(a: Application) {
        app = a
        ensureChannel(a)
        schedule(a)
    }

    private val _askPermission = MutableStateFlow(false)
    /** True when the screen should show the "allow notifications" explanation (then the system permission dialog). */
    val askPermission: StateFlow<Boolean> = _askPermission.asStateFlow()

    /** Android 13+ needs the runtime permission POST_NOTIFICATIONS; older versions always allow. */
    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun enabled(): Boolean = Prefs.getString(K_ON) != "0"
    fun setEnabled(on: Boolean) = Prefs.putString(K_ON, if (on) null else "0")

    /** Notification channel (Android 8+). Safe to call many times. */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "নোটিশ ও পেমেন্ট", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "নতুন নোটিশ, পেমেন্ট অনুমোদন, রিভিউ ও রিওয়ার্ড ব্যাজের খবর"
        nm.createNotificationChannel(ch)
    }

    /** Repeating background check (once; KEEP means a second call changes nothing). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<PushWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Called after a successful sign-in check: channel, and ask for the permission once if it is still missing. */
    fun onSignedIn() {
        if (!::app.isInitialized) return
        ensureChannel(app)
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(app) && enabled() && (Prefs.getString(K_ASKED)?.toIntOrNull() ?: 0) < 2) {
            _askPermission.value = true
        }
        syncOpened()
    }

    /** The app is open: notices up to now are shown inside the app, so only move the marker (no system notification). */
    fun syncOpened() {
        if (!::app.isInitialized) return
        AppScope.scope.launch {
            try { poll(app, notify = false) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* offline */ }
        }
    }

    /** The rationale dialog was answered (either way); the system dialog is launched by the caller on "allow". */
    fun permissionAsked() {
        _askPermission.value = false
        Prefs.putString(K_ASKED, ((Prefs.getString(K_ASKED)?.toIntOrNull() ?: 0) + 1).toString())
    }

    /**
     * Looks for notices newer than the last one handled for the signed-in user.
     * [notify] = true shows a system notification for each (background job); false only moves the marker (app is open).
     * The first run only sets the marker, so old notices never flood the phone. Returns how many new notices were found.
     * A network error throws (the caller decides); nothing is lost, the marker moves only after a good answer.
     */
    suspend fun poll(ctx: Context, notify: Boolean): Int {
        val s = Session.get() ?: return 0
        if (!enabled()) return 0
        val key = lastKey(s.id)
        val last = Prefs.getString(key)?.toLongOrNull()
        val who = "or=" + enc("(user_id.is.null,user_id.eq.${s.id})")
        if (last == null) {
            val top = Sb.get("notices?select=id&$who&order=id.desc&limit=1").optJSONObject(0)
            Prefs.putString(key, (top?.optLong("id") ?: 0L).toString())
            return 0
        }
        val rows = Sb.get("notices?$who&id=gt.$last&order=id.asc&limit=20").objects()
        if (rows.isEmpty()) return 0
        val maxId = rows.maxOf { it.optLong("id") }
        if (notify && hasPermission(ctx)) {
            ensureChannel(ctx)
            rows.forEach { show(ctx, it) }
        }
        Prefs.putString(key, maxId.toString())
        return rows.size
    }

    private val KIND = mapOf("notice" to "নোটিশ", "review" to "রিভিউ", "payment" to "পেমেন্ট", "reward" to "রিওয়ার্ড")

    @SuppressLint("MissingPermission")   // hasPermission() is checked by the caller
    private fun show(ctx: Context, n: JSONObject) {
        val id = n.optLong("id").toInt()
        val kind = KIND[n.str("kind") ?: "notice"] ?: "নোটিশ"
        val title = n.str("title")?.takeIf { it.isNotBlank() } ?: kind
        val body = n.str("body").orEmpty()
        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notice)
            .setContentTitle(title)
            .setContentText(body.ifEmpty { kind })
            .setSubText(kind)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifEmpty { kind }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (e: SecurityException) {
            // permission revoked meanwhile — ignored
        }
    }
}

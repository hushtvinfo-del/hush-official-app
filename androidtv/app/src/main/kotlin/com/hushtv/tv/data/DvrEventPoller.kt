package com.hushtv.tv.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hushtv.tv.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Polls the DVR remote server every [POLL_INTERVAL_MS] for events
 * (recording started / completed / failed / scheduled-skipped) and
 * surfaces them as Android system notifications.
 *
 * Started from [MainActivity.onCreate] when the user has a playlist,
 * stopped on app exit. The single-tick `since` cursor lives in
 * SharedPreferences so we don't flood the user with stale events
 * after a relaunch.
 *
 * No FCM / push infrastructure — the user is already running our app
 * and the DVR server is hot in their network. A 30-second poll is
 * cheap and avoids the headache of registering a Firebase project.
 */
object DvrEventPoller {

    private const val POLL_INTERVAL_MS = 30_000L
    private const val PREFS = "hushdvr_event_cursor"
    private const val KEY_SINCE = "since"
    private const val NOTIF_CHANNEL_ID = "hushdvr_recordings"
    private const val NOTIF_CHANNEL_NAME = "Cloud DVR"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: Job? = null

    /** Idempotent — safe to call from every Activity onResume. */
    fun start(ctx: Context) {
        ensureNotificationChannel(ctx)
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = scope.launch {
            while (true) {
                try {
                    pollOnce(app)
                } catch (_: Throwable) {
                    // Network blip — ignore and try again next tick.
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun pollOnce(ctx: Context) {
        val playlist = PlaylistStore.getAll(ctx).firstOrNull() ?: return
        val userId = DvrApi.userIdFor(playlist)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var since = prefs.getLong(KEY_SINCE, System.currentTimeMillis() / 1000L - 60L)
        val (events, serverNow) = DvrApi.events(userId, since)
        for (ev in events) {
            postNotification(ctx, ev)
            if (ev.ts > since) since = ev.ts
        }
        // Even if we got 0 events, advance the cursor to serverNow so
        // we don't spam-replay stale events later.
        prefs.edit().putLong(KEY_SINCE, maxOf(since, serverNow)).apply()
    }

    private fun postNotification(ctx: Context, ev: DvrApi.Event) {
        val (title, text) = when (ev.kind) {
            "started" -> "Recording started" to
                "${ev.show_title.ifBlank { ev.channel_name }} is now being recorded."
            "scheduled_started" -> "Scheduled recording started" to
                "${ev.show_title.ifBlank { ev.channel_name }} is now being recorded."
            "completed" -> "Recording finished" to
                "${ev.show_title.ifBlank { ev.channel_name }} — tap to watch in My Recordings."
            "failed" -> "Recording failed" to
                (ev.fail_reason.ifBlank { "${ev.show_title.ifBlank { ev.channel_name }} couldn't be recorded." })
            "scheduled_skipped" -> "Scheduled recording skipped" to
                (ev.reason.ifBlank { "${ev.show_title} was skipped — another recording was in progress." })
            else -> return
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // safe built-in fallback
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Stable id per recording / schedule so consecutive events
        // (started → completed) replace the original notification.
        val id = (ev.rec_id.ifBlank { ev.sched_id.ifBlank { ev.show_title } })
            .hashCode()
        runCatching {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(id, n)
        }
    }

    private fun ensureNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(NOTIF_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            NOTIF_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Cloud DVR recording status — start, finish, skipped."
        }
        mgr.createNotificationChannel(channel)
    }
}

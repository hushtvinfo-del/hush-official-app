package com.hushtv.tv.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hushtv.tv.MainActivity
import com.hushtv.tv.R
import com.hushtv.tv.data.ReminderStore

/** Schedules / fires "Your show starts in 5 min" notifications. */
object EpgReminderScheduler {

    private const val CHANNEL_ID = "hushtv_reminders"
    private const val LEAD_MS = 5L * 60 * 1000  // 5 min before program

    fun schedule(ctx: Context, r: ReminderStore.Reminder) {
        runCatching {
            ensureChannel(ctx)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val fireAt = r.programStartMs - LEAD_MS
            if (fireAt <= System.currentTimeMillis()) return@runCatching  // past programs → skip

            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                action = "com.hushtv.tv.REMINDER"
                putExtra("channelName", r.channelName)
                putExtra("programTitle", r.programTitle)
                putExtra("streamId", r.streamId)
            }
            val pi = PendingIntent.getBroadcast(
                ctx,
                (r.streamId.toLong() + r.programStartMs).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // On Android 12+ calling setExactAndAllowWhileIdle without
            // SCHEDULE_EXACT_ALARM permission throws SecurityException.
            // On Android 14+ the permission can even be REVOKED at
            // runtime without notice. Fall back to inexact alarm on
            // any failure — the user still gets notified, just with
            // a small scheduling window, and the app stays alive.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                }
            } catch (_: SecurityException) {
                runCatching { am.set(AlarmManager.RTC_WAKEUP, fireAt, pi) }
            }
        }
    }

    @SuppressLint("NewApi")
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Program reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = "Notifies you 5 minutes before a scheduled program starts"
            nm.createNotificationChannel(ch)
        }
    }

    internal fun fireNotification(ctx: Context, channelName: String, programTitle: String, streamId: Int) {
        ensureChannel(ctx)
        val tap = PendingIntent.getActivity(
            ctx, streamId,
            Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$programTitle starts in 5 min")
            .setContentText("on $channelName — tap to open HushTV")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(streamId, notif)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val channelName = intent.getStringExtra("channelName") ?: "HushTV"
        val programTitle = intent.getStringExtra("programTitle") ?: "A program"
        val streamId = intent.getIntExtra("streamId", 0)
        EpgReminderScheduler.fireNotification(ctx, channelName, programTitle, streamId)
    }
}

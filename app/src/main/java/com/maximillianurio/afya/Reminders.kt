package com.maximillianurio.afya

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.ReminderTime
import com.maximillianurio.afya.data.LocalStore
import com.maximillianurio.afya.data.ReadingsDb

/**
 * The morning weigh-in reminder. It is off until the person picks a time; it never fires on a day
 * that already has a weigh-in on file; and it opens the app straight onto the platform. An inexact
 * alarm with a ten-minute window is enough for this and needs no exact-alarm permission.
 */
object Reminders {
    private const val CHANNEL = "weigh_in"
    private const val NOTIFICATION_ID = 7
    private const val WINDOW_MS = 10 * 60_000L
    const val ACTION_FIRE = "com.maximillianurio.afya.action.REMINDER"

    fun schedule(context: Context, minutes: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = firePending(context)
        alarms.cancel(pending)
        if (minutes < 0) return
        val at = ReminderTime.next(System.currentTimeMillis(), minutes)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_note) }
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.weighInIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mark)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the post; the next day asks again.
        }
    }

    private fun firePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Fires the reminder, and puts it back after anything that clears alarms: a reboot, an app update,
 * or the clock or time zone changing.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = LocalStore(app)
        val minutes = store.reminderMinutes
        if (intent.action != Reminders.ACTION_FIRE) {
            Reminders.schedule(app, minutes)
            return
        }
        val done = goAsync()
        Thread {
            try {
                val (dayStart, _) = DateFmt.dayBounds(System.currentTimeMillis())
                val weighed = ReadingsDb(app).use { db ->
                    db.since(dayStart).any { BodyComposition.bodyPlausible(it.grams) }
                }
                if (!weighed && minutes >= 0) Reminders.post(app)
                Reminders.schedule(app, minutes)
            } finally {
                done.finish()
            }
        }.start()
    }
}

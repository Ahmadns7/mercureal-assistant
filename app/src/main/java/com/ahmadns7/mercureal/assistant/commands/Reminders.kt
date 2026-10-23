package com.ahmadns7.mercureal.assistant.commands

import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat

/**
 * Local reminders: AlarmManager schedules a PendingIntent for the wall-clock time, and
 * [ReminderReceiver] posts a notification. Nothing is sent off the device.
 *
 * Exact timing uses setExactAndAllowWhileIdle when the user has allowed exact alarms for the app
 * (Android 12+ "Alarms & reminders"). Otherwise it falls back to setAndAllowWhileIdle, which can
 * fire a few minutes late. [canScheduleExact] tells the caller which one was used.
 */
object Reminders {

    private const val CHANNEL = "reminders"
    private const val EXTRA_TEXT = "text"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(context: Context, text: String, triggerAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TEXT, text)
        val requestCode = (triggerAtMs xor text.hashCode().toLong()).toInt()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    class ReminderReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra(EXTRA_TEXT) ?: return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
            if (!notificationsAllowed(context)) return
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("reminder")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(text.hashCode(), notification)
        }
    }
}

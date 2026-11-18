package com.example.yesmin.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.yesmin.security.PermissionHelper

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, System.currentTimeMillis())
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "YESMIN Reminder"
        val notes = intent.getStringExtra(EXTRA_NOTES).orEmpty()
        postReminderNotification(context, id.toInt(), title, notes)
    }

    companion object {
        const val CHANNEL_ID = "yesmin_reminders_channel"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_NOTES = "extra_reminder_notes"

        fun postReminderNotification(context: Context, notifId: Int, title: String, notes: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "YESMIN Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Scheduled reminders created via YESMIN AI"
                }
                nm?.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                return
            }

            val openIntent = PendingIntent.getActivity(
                context,
                notifId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("YESMIN Reminder: $title")
                .setContentText(notes.ifBlank { "Scheduled reminder is due now." })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}

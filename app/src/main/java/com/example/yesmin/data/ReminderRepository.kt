package com.example.yesmin.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.yesmin.database.ReminderDao
import com.example.yesmin.database.ReminderEntity
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.service.ReminderReceiver
import com.example.yesmin.service.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ReminderRepository(
    private val context: Context,
    private val reminderDao: ReminderDao
) {
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeReminders()

    suspend fun createReminder(title: String, notes: String, triggerAtMillis: Long): Long {
        val id = reminderDao.insertReminder(
            ReminderEntity(
                title = title.trim(),
                notes = notes.trim(),
                triggerAtMillis = triggerAtMillis
            )
        )
        scheduleReminder(id, title.trim(), notes.trim(), triggerAtMillis)
        return id
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        reminderDao.setReminderCompleted(id, completed)
        if (completed) {
            cancelScheduledReminder(id)
        }
    }

    suspend fun deleteReminder(id: Long) {
        cancelScheduledReminder(id)
        reminderDao.deleteReminder(id)
    }

    suspend fun clearAllReminders() {
        reminderDao.deleteAllReminders()
    }

    fun scheduleCloudAlarm(cloudReminderId: String, title: String, notes: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val key = (cloudReminderId.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L)
        scheduleReminder(key, title.trim(), notes.trim(), triggerAtMillis)
    }

    fun cancelCloudAlarm(cloudReminderId: String) {
        val key = (cloudReminderId.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L)
        cancelScheduledReminder(key)
    }

    private fun scheduleReminder(id: Long, title: String, notes: String, triggerAtMillis: Long) {
        val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_NOTES, notes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

        if (alarmManager != null && canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            val workData = Data.Builder()
                .putLong(ReminderReceiver.EXTRA_REMINDER_ID, id)
                .putString(ReminderReceiver.EXTRA_TITLE, title)
                .putString(ReminderReceiver.EXTRA_NOTES, notes)
                .build()
            val work = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "yesmin_reminder_$id",
                ExistingWorkPolicy.REPLACE,
                work
            )
        }
    }

    private fun cancelScheduledReminder(id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
        }
        WorkManager.getInstance(context).cancelUniqueWork("yesmin_reminder_$id")
    }

    suspend fun getUpcomingCalendarEvents(limit: Int = 15): List<CalendarEventSummary> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_CALENDAR)) {
            return@withContext emptyList()
        }
        val events = mutableListOf<CalendarEventSummary>()
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DTSTART} >= ?",
            arrayOf(now.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            while (cursor.moveToNext() && events.size < limit) {
                val title = cursor.getString(titleIdx) ?: continue
                val start = cursor.getLong(startIdx)
                val end = cursor.getLong(endIdx).let { if (it <= start) start + 3_600_000L else it }
                events.add(
                    CalendarEventSummary(
                        id = cursor.getLong(idIdx),
                        title = title,
                        startMillis = start,
                        endMillis = end,
                        location = cursor.getString(locIdx)
                    )
                )
            }
        }
        events
    }

    suspend fun insertCalendarEvent(
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long = startMillis + 3_600_000L
    ): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.WRITE_CALENDAR) ||
            !PermissionHelper.hasPermission(context, Manifest.permission.READ_CALENDAR)
        ) {
            return@withContext false
        }
        val calendarId = findPrimaryWritableCalendarId() ?: return@withContext false
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }

    private fun findPrimaryWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }
}

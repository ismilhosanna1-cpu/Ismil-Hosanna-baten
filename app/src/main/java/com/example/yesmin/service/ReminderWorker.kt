package com.example.yesmin.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(ReminderReceiver.EXTRA_REMINDER_ID, System.currentTimeMillis())
        val title = inputData.getString(ReminderReceiver.EXTRA_TITLE) ?: "YESMIN Reminder"
        val notes = inputData.getString(ReminderReceiver.EXTRA_NOTES).orEmpty()
        ReminderReceiver.postReminderNotification(applicationContext, id.toInt(), title, notes)
        return Result.success()
    }
}

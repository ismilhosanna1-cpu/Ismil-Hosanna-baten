package com.example.yesmin.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.yesmin.data.NotificationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YesminNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val entry = NotificationSummary(
            key = sbn.key,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        val updated = (listOf(entry) + _recentNotifications.value.filterNot { it.key == sbn.key })
            .take(25)
        _recentNotifications.value = updated
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        _recentNotifications.value = _recentNotifications.value.filterNot { it.key == sbn.key }
    }

    companion object {
        private val _recentNotifications = MutableStateFlow<List<NotificationSummary>>(emptyList())
        val recentNotifications: StateFlow<List<NotificationSummary>> = _recentNotifications.asStateFlow()

        fun clearCaptured() {
            _recentNotifications.value = emptyList()
        }
    }
}

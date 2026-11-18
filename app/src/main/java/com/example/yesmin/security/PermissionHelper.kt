package com.example.yesmin.security

import android.Manifest
import android.app.AlarmManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.yesmin.service.YesminAccessibilityService
import com.example.yesmin.service.YesminNotificationListener

data class PermissionStatusSnapshot(
    val recordAudio: Boolean,
    val readContacts: Boolean,
    val callPhone: Boolean,
    val readPhoneState: Boolean,
    val answerPhoneCalls: Boolean,
    val readCallLog: Boolean,
    val readCalendar: Boolean,
    val writeCalendar: Boolean,
    val postNotifications: Boolean,
    val exactAlarms: Boolean,
    val isDefaultDialer: Boolean,
    val notificationListenerEnabled: Boolean,
    val accessibilityEnabled: Boolean
)

object PermissionHelper {

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getSnapshot(context: Context): PermissionStatusSnapshot {
        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val answerCallsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
        } else {
            true
        }

        val exactAlarmsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

        return PermissionStatusSnapshot(
            recordAudio = hasPermission(context, Manifest.permission.RECORD_AUDIO),
            readContacts = hasPermission(context, Manifest.permission.READ_CONTACTS),
            callPhone = hasPermission(context, Manifest.permission.CALL_PHONE),
            readPhoneState = hasPermission(context, Manifest.permission.READ_PHONE_STATE),
            answerPhoneCalls = answerCallsGranted,
            readCallLog = hasPermission(context, Manifest.permission.READ_CALL_LOG),
            readCalendar = hasPermission(context, Manifest.permission.READ_CALENDAR),
            writeCalendar = hasPermission(context, Manifest.permission.WRITE_CALENDAR),
            postNotifications = postNotifGranted,
            exactAlarms = exactAlarmsGranted,
            isDefaultDialer = isDefaultDialer(context),
            notificationListenerEnabled = isNotificationListenerEnabled(context),
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
        )
    }

    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createDefaultDialerIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val component = ComponentName(context, YesminNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context, YesminAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun exactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            appDetailsSettingsIntent(context)
        }
    }

    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}

package com.example.yesmin.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.yesmin.core.YesminApplication
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.voice.WakeWordBus
import com.example.yesmin.voice.YesminVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YesminHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WAKE_WORD) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val running = WakeWordBus.serviceRunning.value
                    val app = context.applicationContext as? YesminApplication
                    if (running) {
                        app?.container?.settingsRepository?.updateSettings {
                            it.copy(wakeWordEnabled = false)
                        }
                        YesminVoiceService.stop(context)
                    } else if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                        app?.container?.settingsRepository?.updateSettings {
                            it.copy(wakeWordEnabled = true)
                        }
                        YesminVoiceService.start(context)
                    } else {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_VOICE)
                        }
                        context.startActivity(launchIntent)
                    }
                    updateAllWidgets(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_WAKE_WORD = "com.example.yesmin.widget.ACTION_TOGGLE_WAKE_WORD"
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"
        const val WIDGET_ACTION_VOICE = "voice"
        const val WIDGET_ACTION_CHAT = "chat"

        fun isPinSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
            return manager.isRequestPinAppWidgetSupported
        }

        fun requestPinToHomeScreen(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, YesminHomeWidgetProvider::class.java)
            return manager.requestPinAppWidget(provider, null, null)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, YesminHomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component) ?: return
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            runCatching {
                appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
            }
        }

        internal fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.yesmin_home_widget)
            val wakeRunning = WakeWordBus.serviceRunning.value

            views.setTextViewText(
                R.id.widget_status_text,
                if (wakeRunning) {
                    context.getString(R.string.widget_status_always_on)
                } else {
                    context.getString(R.string.widget_status_ready)
                }
            )
            views.setTextViewText(
                R.id.widget_wake_label,
                if (wakeRunning) {
                    context.getString(R.string.widget_wake_on)
                } else {
                    context.getString(R.string.widget_wake_off)
                }
            )

            val homePending = PendingIntent.getActivity(
                context,
                1001,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_info_container, homePending)
            views.setOnClickPendingIntent(R.id.widget_avatar, homePending)

            val voicePending = PendingIntent.getActivity(
                context,
                1002,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_VOICE)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_mic, voicePending)

            val chatPending = PendingIntent.getActivity(
                context,
                1003,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CHAT)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_chat, chatPending)

            val wakePending = PendingIntent.getBroadcast(
                context,
                1004,
                Intent(context, YesminHomeWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_WAKE_WORD
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_wake, wakePending)

            return views
        }
    }
}

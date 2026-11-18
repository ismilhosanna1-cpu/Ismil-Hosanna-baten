package com.example.yesmin.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.yesmin.core.YesminApplication
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.voice.YesminVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class YesminBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val app = context.applicationContext as? YesminApplication ?: return@launch
                val settings = app.container.settingsRepository.settingsFlow.first()
                if (settings.wakeWordEnabled &&
                    PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)
                ) {
                    YesminVoiceService.start(context)
                }
            } catch (e: Exception) {
                Log.w("YesminBootReceiver", "Could not auto-start voice service on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

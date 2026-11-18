package com.example.yesmin.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.yesmin.core.YesminApplication
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.widget.YesminHomeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WakeWordBus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _detectedCommands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val detectedCommands: SharedFlow<String> = _detectedCommands.asSharedFlow()

    private val _lastStatus = MutableStateFlow("Wake-word service stopped")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    internal fun setRunning(running: Boolean, status: String) {
        _serviceRunning.value = running
        _lastStatus.value = status
    }

    internal fun emitCommand(commandText: String) {
        _detectedCommands.tryEmit(commandText)
    }
}

class YesminVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!PermissionHelper.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
            WakeWordBus.setRunning(false, "Cannot start: Microphone permission missing")
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        val notification = buildNotification("Listening for \"Hey Yesmin\" / \"Yesmin\"")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        WakeWordBus.setRunning(true, "Active — Listening for wake phrase")
        YesminHomeWidgetProvider.updateAllWidgets(applicationContext)
        startRecognitionCycle()
        return START_STICKY
    }

    override fun onDestroy() {
        isStopping = true
        destroyRecognizer()
        WakeWordBus.setRunning(false, "Wake-word service stopped")
        YesminHomeWidgetProvider.updateAllWidgets(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        isStopping = true
        destroyRecognizer()
        WakeWordBus.setRunning(false, "Wake-word service stopped")
        YesminHomeWidgetProvider.updateAllWidgets(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRecognitionCycle() {
        if (isStopping || !SpeechRecognizer.isRecognitionAvailable(this)) {
            WakeWordBus.setRunning(false, "SpeechRecognizer unavailable on device")
            return
        }
        destroyRecognizer()

        serviceScope.launch {
            val container = (application as? YesminApplication)?.container
            val settings = container?.settingsRepository?.settingsFlow?.first()
            val langTag = settings?.language?.bcp47Tag ?: "en-US"
            val primaryPhrase = settings?.primaryWakePhrase?.lowercase()?.trim().orEmpty().ifBlank { "hey yesmin" }
            val secondaryPhrase = settings?.secondaryWakePhrase?.lowercase()?.trim().orEmpty().ifBlank { "yesmin" }

            val sr = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    if (!isStopping) {
                        serviceScope.launch {
                            delay(800L)
                            startRecognitionCycle()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (spoken.isNotBlank()) {
                        val lower = spoken.lowercase()
                        val hasWakeWord = lower.contains(primaryPhrase) ||
                            lower.contains(secondaryPhrase) ||
                            lower.contains("yesmin") ||
                            lower.contains("ইয়েসমিন") ||
                            lower.contains("येस्मिन")

                        if (hasWakeWord) {
                            WakeWordBus.setRunning(true, "Wake word heard: \"$spoken\"")
                            WakeWordBus.emitCommand(spoken)
                            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(EXTRA_VOICE_COMMAND, spoken)
                            }
                            startActivity(openIntent)
                        }
                    }
                    if (!isStopping) {
                        serviceScope.launch {
                            delay(500L)
                            startRecognitionCycle()
                        }
                    }
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            recognizer = null
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YESMIN Voice & Wake Word",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible microphone indicator while YESMIN wake-word listening is enabled."
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val speakPending = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(
                    YesminHomeWidgetProvider.EXTRA_WIDGET_ACTION,
                    YesminHomeWidgetProvider.WIDGET_ACTION_VOICE
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, YesminVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("YESMIN AI • Always Ready")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_btn_speak_now, "Speak Now", speakPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "yesmin_voice_channel"
        const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.example.yesmin.STOP_VOICE_SERVICE"
        const val EXTRA_VOICE_COMMAND = "extra_voice_command"

        fun start(context: Context) {
            val intent = Intent(context, YesminVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, YesminVoiceService::class.java))
        }
    }
}

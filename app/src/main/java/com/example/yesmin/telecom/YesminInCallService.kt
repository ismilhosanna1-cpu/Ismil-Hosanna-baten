package com.example.yesmin.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.yesmin.core.YesminApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class YesminInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoAnswerJob: Job? = null
    private var answeredByYesminFlag = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            syncCallState(call, state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallStateHolder.activeService = this
    }

    override fun onDestroy() {
        autoAnswerJob?.cancel()
        CallStateHolder.activeService = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallStateHolder.updateState {
                it.copy(
                    isMuted = audioState.isMuted,
                    isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER
                )
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallStateHolder.currentCall = call
        answeredByYesminFlag = false
        call.registerCallback(callCallback)

        val number = call.details?.handle?.schemeSpecificPart.orEmpty().ifBlank { "Private Number" }
        val callerDisplayName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }

        val initialState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
        syncCallState(call, initialState)

        serviceScope.launch {
            val container = (application as? YesminApplication)?.container ?: return@launch
            val resolvedName = callerDisplayName ?: container.contactsRepository.resolveCallerName(number)
            CallStateHolder.updateState {
                it.copy(
                    phoneNumber = number,
                    callerName = resolvedName
                )
            }
            val settings = container.settingsRepository.settingsFlow.first()
            if (initialState == Call.STATE_RINGING && settings.announceIncomingCalls) {
                val spokenWho = resolvedName ?: number
                val announcement = when (settings.language.code) {
                    "bn" -> "$spokenWho কল করছেন।"
                    "hi" -> "$spokenWho कॉल कर रहे हैं।"
                    else -> "Incoming call from $spokenWho."
                }
                container.ttsManager.speak(announcement, settings)
            }
            if (initialState == Call.STATE_RINGING && settings.autoAnswerEnabled) {
                autoAnswerJob?.cancel()
                autoAnswerJob = serviceScope.launch {
                    delay(settings.autoAnswerDelaySeconds.coerceIn(2, 15) * 1000L)
                    if (CallStateHolder.currentCall == call && getCompatCallState(call) == Call.STATE_RINGING) {
                        answeredByYesminFlag = true
                        CallStateHolder.answerIncomingCall()
                    }
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        autoAnswerJob?.cancel()
        call.unregisterCallback(callCallback)

        val previous = CallStateHolder.callState.value
        val durationSec = previous.connectedAtMillis?.let {
            ((System.currentTimeMillis() - it) / 1000L).coerceAtLeast(0L)
        } ?: 0L

        serviceScope.launch {
            val container = (application as? YesminApplication)?.container ?: return@launch
            val direction = when (previous.status) {
                LiveCallStatus.RINGING -> "MISSED"
                LiveCallStatus.DIALING -> "OUTGOING"
                else -> "INCOMING"
            }
            if (previous.phoneNumber.isNotBlank()) {
                container.contactsRepository.recordAssistantCallLog(
                    displayName = previous.callerName,
                    phoneNumber = previous.phoneNumber,
                    direction = direction,
                    durationSeconds = durationSec,
                    answeredByYesmin = answeredByYesminFlag
                )
            }
        }

        if (CallStateHolder.currentCall == call) {
            CallStateHolder.currentCall = null
            CallStateHolder.updateState {
                ActiveCallState(
                    status = LiveCallStatus.IDLE,
                    statusNote = "Call ended"
                )
            }
        }
    }

    private fun getCompatCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }

    private fun syncCallState(call: Call, state: Int) {
        val number = call.details?.handle?.schemeSpecificPart.orEmpty().ifBlank { "Unknown" }
        val mapped = when (state) {
            Call.STATE_RINGING -> LiveCallStatus.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> LiveCallStatus.DIALING
            Call.STATE_ACTIVE -> LiveCallStatus.ACTIVE
            Call.STATE_HOLDING -> LiveCallStatus.HOLDING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> LiveCallStatus.DISCONNECTED
            else -> LiveCallStatus.IDLE
        }
        val note = when (mapped) {
            LiveCallStatus.RINGING -> "Ringing"
            LiveCallStatus.DIALING -> "Dialing..."
            LiveCallStatus.ACTIVE -> "Connected"
            LiveCallStatus.HOLDING -> "On Hold"
            LiveCallStatus.DISCONNECTED -> "Disconnected"
            LiveCallStatus.IDLE -> "Idle"
        }
        CallStateHolder.updateState { prev ->
            prev.copy(
                status = mapped,
                phoneNumber = prev.phoneNumber.ifBlank { number },
                connectedAtMillis = if (mapped == LiveCallStatus.ACTIVE) {
                    prev.connectedAtMillis ?: System.currentTimeMillis()
                } else if (mapped == LiveCallStatus.IDLE || mapped == LiveCallStatus.DISCONNECTED) {
                    null
                } else {
                    prev.connectedAtMillis
                },
                statusNote = note
            )
        }
    }
}

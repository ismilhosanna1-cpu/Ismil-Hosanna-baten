package com.example.yesmin.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LiveCallStatus {
    IDLE,
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTED
}

data class ActiveCallState(
    val status: LiveCallStatus = LiveCallStatus.IDLE,
    val phoneNumber: String = "",
    val callerName: String? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val connectedAtMillis: Long? = null,
    val statusNote: String = "No active call"
)

object CallStateHolder {
    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    @Volatile
    var currentCall: Call? = null
        internal set

    @Volatile
    var activeService: YesminInCallService? = null
        internal set

    fun updateState(transform: (ActiveCallState) -> ActiveCallState) {
        _callState.value = transform(_callState.value)
    }

    fun answerIncomingCall(): Pair<Boolean, String> {
        val call = currentCall ?: return false to "No active incoming call is managed by YESMIN InCallService."
        return try {
            if (call.state == Call.STATE_RINGING) {
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                true to "Android Telecom accepted the answer request."
            } else {
                false to "Current call is not ringing."
            }
        } catch (e: Exception) {
            false to "Unable to answer call: ${e.localizedMessage ?: "Permission or Telecom error"}"
        }
    }

    fun rejectIncomingCall(): Pair<Boolean, String> {
        val call = currentCall ?: return false to "No active incoming call to reject."
        return try {
            if (call.state == Call.STATE_RINGING) {
                call.reject(false, null)
                true to "Android Telecom rejected the incoming call."
            } else {
                call.disconnect()
                true to "Android Telecom disconnected the call."
            }
        } catch (e: Exception) {
            false to "Unable to reject call: ${e.localizedMessage ?: "Telecom error"}"
        }
    }

    fun endActiveCall(): Pair<Boolean, String> {
        val call = currentCall ?: return false to "No active call in progress."
        return try {
            call.disconnect()
            true to "Android Telecom disconnected the call."
        } catch (e: Exception) {
            false to "Unable to end call: ${e.localizedMessage ?: "Telecom error"}"
        }
    }

    fun toggleMute(): Pair<Boolean, String> {
        val service = activeService ?: return false to "YESMIN InCallService is not currently bound (requires Default Dialer role during calls)."
        val target = !_callState.value.isMuted
        return try {
            service.setMuted(target)
            updateState { it.copy(isMuted = target) }
            true to if (target) "Microphone muted." else "Microphone unmuted."
        } catch (e: Exception) {
            false to "Mute toggle failed: ${e.localizedMessage}"
        }
    }

    fun toggleSpeakerphone(): Pair<Boolean, String> {
        val service = activeService ?: return false to "YESMIN InCallService is not currently bound (requires Default Dialer role during calls)."
        val targetSpeaker = !_callState.value.isSpeakerOn
        return try {
            service.setAudioRoute(
                if (targetSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            )
            updateState { it.copy(isSpeakerOn = targetSpeaker) }
            true to if (targetSpeaker) "Speakerphone enabled." else "Speakerphone disabled."
        } catch (e: Exception) {
            false to "Speakerphone toggle failed: ${e.localizedMessage}"
        }
    }
}

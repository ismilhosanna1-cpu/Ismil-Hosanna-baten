package com.example.yesmin.ui.screens

import android.Manifest
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.DeviceCallLogEntry
import com.example.yesmin.database.AssistantCallLogEntity
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.security.PermissionStatusSnapshot
import com.example.yesmin.telecom.ActiveCallState
import com.example.yesmin.telecom.LiveCallStatus
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.StatusBadgePill
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhoneCallScreen(
    settings: AssistantSettings,
    callState: ActiveCallState,
    permissions: PermissionStatusSnapshot,
    deviceCallLog: List<DeviceCallLogEntry>,
    assistantCallLogs: List<AssistantCallLogEntity>,
    onRefreshLogs: () -> Unit,
    onPlaceCall: (String, String) -> Unit,
    onAnswerCall: () -> Unit,
    onRejectCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onUpdateSettings: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var dialNumber by remember { mutableStateOf("") }
    var elapsedSec by remember { mutableLongStateOf(0L) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshLogs()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshLogs()
    }

    LaunchedEffect(Unit) {
        onRefreshLogs()
    }

    LaunchedEffect(callState.status, callState.connectedAtMillis) {
        val start = callState.connectedAtMillis
        if (callState.status == LiveCallStatus.ACTIVE && start != null) {
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedSec = 0L
        }
    }

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Phone & Telecom",
                    subtitle = if (permissions.isDefaultDialer) "Default Dialer Active" else "Role Setup Recommended",
                    onBack = onBack
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 620.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Default Dialer & Telecom Permissions Status
                    item {
                        NeonGlassCard(
                            borderColor = if (permissions.isDefaultDialer) EmeraldVerified.copy(alpha = 0.6f)
                            else AmberWarning.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Android Telecom Role", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                StatusBadgePill(
                                    label = if (permissions.isDefaultDialer) "Active" else "Not Default",
                                    accentColor = if (permissions.isDefaultDialer) EmeraldVerified else AmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android requires YESMIN AI to hold the Default Dialer role so InCallService can answer, reject, mute, and toggle speakerphone on live calls.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        PermissionHelper.createDefaultDialerIntent(context)?.let {
                                            roleLauncher.launch(it)
                                        }
                                    },
                                    modifier = Modifier.testTag("request_dialer_role_button")
                                ) {
                                    Text(if (permissions.isDefaultDialer) "Dialer Active" else "Set Default Dialer")
                                }
                                OutlinedButton(
                                    onClick = {
                                        permLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CALL_PHONE,
                                                Manifest.permission.READ_PHONE_STATE,
                                                Manifest.permission.ANSWER_PHONE_CALLS,
                                                Manifest.permission.READ_CALL_LOG
                                            )
                                        )
                                    }
                                ) {
                                    Text("Grant Call Perms")
                                }
                            }
                        }
                    }

                    // Live Telecom Call Panel
                    item {
                        NeonGlassCard(
                            borderColor = if (callState.status != LiveCallStatus.IDLE) ElectricCyan else MaterialTheme.colorScheme.outline
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Live InCallService State", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                StatusBadgePill(
                                    label = callState.status.name,
                                    accentColor = when (callState.status) {
                                        LiveCallStatus.ACTIVE -> EmeraldVerified
                                        LiveCallStatus.RINGING -> ElectricCyan
                                        else -> TextSecondary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = callState.callerName ?: callState.phoneNumber.ifBlank { "No active call" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            if (callState.phoneNumber.isNotBlank()) {
                                Text(callState.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                            }
                            if (callState.status == LiveCallStatus.ACTIVE) {
                                Text(
                                    text = String.format(Locale.US, "Duration: %02d:%02d", elapsedSec / 60, elapsedSec % 60),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldVerified
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onAnswerCall,
                                    enabled = callState.status == LiveCallStatus.RINGING,
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified, contentColor = ObsidianVoid),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PhoneInTalk, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Answer")
                                }
                                Button(
                                    onClick = onRejectCall,
                                    enabled = callState.status == LiveCallStatus.RINGING,
                                    colors = ButtonDefaults.buttonColors(containerColor = RosePulse),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.CallEnd, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject")
                                }
                                Button(
                                    onClick = onEndCall,
                                    enabled = callState.status == LiveCallStatus.ACTIVE || callState.status == LiveCallStatus.DIALING,
                                    colors = ButtonDefaults.buttonColors(containerColor = RosePulse),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("End")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onToggleMute, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.MicOff, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (callState.isMuted) "Unmute" else "Mute")
                                }
                                OutlinedButton(onClick = onToggleSpeaker, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.VolumeUp, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (callState.isSpeakerOn) "Speaker ON" else "Speaker")
                                }
                            }
                        }
                    }

                    // Quick Dialer
                    item {
                        NeonGlassCard {
                            Text("Make Outgoing Call", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = dialNumber,
                                    onValueChange = { dialNumber = it },
                                    label = { Text("Phone number") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dial_number_input")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (dialNumber.isNotBlank()) {
                                            onPlaceCall(dialNumber.trim(), dialNumber.trim())
                                        }
                                    },
                                    enabled = dialNumber.isNotBlank(),
                                    modifier = Modifier.testTag("dial_call_button")
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Call")
                                }
                            }
                        }
                    }

                    // Call Announcement & Auto-Answer Consent Settings
                    item {
                        NeonGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Announce Incoming Calls (TTS)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Speaks caller name/number when ringing.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Switch(
                                    checked = settings.announceIncomingCalls,
                                    onCheckedChange = { checked ->
                                        onUpdateSettings { it.copy(announceIncomingCalls = checked) }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Answer Calls (${settings.autoAnswerDelaySeconds}s)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Requires Default Dialer role & explicit consent. Keep OFF unless hands-free driving.", style = MaterialTheme.typography.bodyMedium, color = AmberWarning)
                                }
                                Switch(
                                    checked = settings.autoAnswerEnabled,
                                    onCheckedChange = { checked ->
                                        onUpdateSettings { it.copy(autoAnswerEnabled = checked) }
                                    }
                                )
                            }
                        }
                    }

                    // Recent Device Call Log
                    item {
                        Text("Recent Call History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    if (deviceCallLog.isEmpty() && assistantCallLogs.isEmpty()) {
                        item {
                            NeonGlassCard {
                                Text(
                                    text = if (permissions.readCallLog) "No recent calls found." else "Grant READ_CALL_LOG permission above to view device call history.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(deviceCallLog) { entry ->
                            val typeLabel = when (entry.type) {
                                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                                CallLog.Calls.MISSED_TYPE -> "Missed"
                                CallLog.Calls.REJECTED_TYPE -> "Rejected"
                                else -> "Call"
                            }
                            val fmt = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                            NeonGlassCard(
                                onClick = { onPlaceCall(entry.name ?: entry.number, entry.number) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.name ?: entry.number, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(
                                            "$typeLabel • ${fmt.format(Date(entry.timestamp))} • ${entry.durationSeconds}s",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    Icon(Icons.Filled.Call, contentDescription = "Redial", tint = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

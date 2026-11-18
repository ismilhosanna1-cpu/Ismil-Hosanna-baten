package com.example.yesmin.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.ui.components.AnimatedNeonWaveform
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.NeonMicButton
import com.example.yesmin.ui.components.YesminGlowingAvatar
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import com.example.yesmin.voice.VoiceListeningState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantScreen(
    settings: AssistantSettings,
    listeningState: VoiceListeningState,
    rmsLevel: Float,
    isSpeaking: Boolean,
    isGenerating: Boolean,
    lastTranscript: String,
    lastReply: String,
    onLanguageChange: (SupportedLanguage) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onLaunchSystemMic: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakReply: () -> Unit,
    onQuickVoiceCommand: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartListening()
        }
    }

    val isListening = listeningState is VoiceListeningState.Listening || listeningState is VoiceListeningState.Partial

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Voice Assistant",
                    subtitle = "${settings.language.displayName} • ${if (isSpeaking) "Speaking" else if (isListening) "Listening" else "Standby"}",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { if (isSpeaking) onStopSpeaking() else onSpeakReply() }) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Interrupt voice" else "Replay response",
                                tint = ElectricCyan
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .safeDrawingPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language selector chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SupportedLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text("${lang.displayName} (${lang.nativeLabel})") }
                            )
                        }
                    }

                    YesminGlowingAvatar(
                        size = 144.dp,
                        isActive = isListening || isSpeaking,
                        rmsLevel = if (isSpeaking) 0.55f else rmsLevel
                    )

                    AnimatedNeonWaveform(
                        isActive = isListening || isSpeaking || isGenerating,
                        rmsLevel = if (isSpeaking) 0.6f else rmsLevel
                    )

                    val statusText = when (listeningState) {
                        is VoiceListeningState.Listening -> "Listening in ${settings.language.displayName}..."
                        is VoiceListeningState.Partial -> "\"${listeningState.text}\""
                        is VoiceListeningState.Error -> listeningState.message
                        VoiceListeningState.Idle -> if (isGenerating) "YESMIN is thinking..." else "Tap microphone to speak"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (listeningState is VoiceListeningState.Error) RosePulse else ElectricCyan,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NeonMicButton(
                            isListening = isListening,
                            onClick = {
                                if (isListening) {
                                    onStopListening()
                                } else if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    onStartListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                        OutlinedButton(
                            onClick = {
                                if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    onLaunchSystemMic()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("system_mic_button")
                        ) {
                            Text("System Mic")
                        }
                    }

                    if (lastTranscript.isNotBlank()) {
                        NeonGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("You said", style = MaterialTheme.typography.labelSmall, color = NeonViolet)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(lastTranscript, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }

                    NeonGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("YESMIN Response", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(lastReply, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }

                    Text("Quick Voice Intents", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickPhrases = listOf(
                            "Yesmin, turn on flashlight",
                            "Yesmin, open YouTube",
                            "Yesmin, check battery",
                            "Yesmin, set an alarm for 6 AM",
                            "Yesmin, tell me today's weather",
                            "Yesmin, read my notifications"
                        )
                        quickPhrases.forEach { cmd ->
                            AssistChip(
                                onClick = { onQuickVoiceCommand(cmd) },
                                label = { Text(cmd) },
                                modifier = Modifier.testTag("quick_voice_cmd")
                            )
                        }
                    }
                }
            }
        }
    }
}

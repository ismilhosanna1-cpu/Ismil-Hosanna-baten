package com.example.yesmin.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.GeminiConnectionStatus
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.security.PermissionStatusSnapshot
import com.example.yesmin.telecom.BatteryTelemetry
import com.example.yesmin.telecom.PhoneActionExecutor
import com.example.yesmin.ui.components.AnimatedNeonWaveform
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.NeonMicButton
import com.example.yesmin.ui.components.StatusBadgePill
import com.example.yesmin.ui.components.YesminGlowingAvatar
import com.example.yesmin.ui.navigation.YesminRoute
import com.example.yesmin.voice.VoiceListeningState

private data class QuickToolAction(
    val label: String,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    settings: AssistantSettings,
    hasApiKey: Boolean,
    connectionStatus: GeminiConnectionStatus,
    battery: BatteryTelemetry,
    permissions: PermissionStatusSnapshot,
    wakeWordRunning: Boolean,
    listeningState: VoiceListeningState,
    rmsLevel: Float,
    phoneActionExecutor: PhoneActionExecutor,
    onNavigate: (YesminRoute) -> Unit,
    onMicClick: () -> Unit,
    onLaunchSystemMic: () -> Unit,
    onLanguageChange: (SupportedLanguage) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleKeepScreenAwake: (Boolean) -> Unit,
    onPinHomeWidget: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onBannerAction: (() -> String) -> Unit
) {
    val isListening = listeningState is VoiceListeningState.Listening ||
        listeningState is VoiceListeningState.Partial
    val quickTools = listOf(
        QuickToolAction("Flashlight", Icons.Filled.FlashlightOn, "quick_flashlight") {
            onBannerAction { phoneActionExecutor.toggleFlashlight().message }
        },
        QuickToolAction("Camera", Icons.Filled.CameraAlt, "quick_camera") {
            onBannerAction { phoneActionExecutor.openCamera().message }
        },
        QuickToolAction("YouTube", Icons.Filled.Public, "quick_youtube") {
            onBannerAction { phoneActionExecutor.launchInstalledAppByName("youtube").message }
        },
        QuickToolAction("WhatsApp", Icons.Filled.ChatBubble, "quick_whatsapp") {
            onBannerAction { phoneActionExecutor.launchInstalledAppByName("whatsapp").message }
        },
        QuickToolAction("Reminders", Icons.Filled.Alarm, "quick_reminders") {
            onNavigate(YesminRoute.RemindersCalendar)
        },
        QuickToolAction("Permissions", Icons.Filled.Security, "quick_permissions") {
            onNavigate(YesminRoute.PermissionsDashboard)
        }
    )

    val sampleCommands = listOf(
        "Tell me today's weather in Dhaka",
        "Check my battery status",
        "Explain Kotlin StateFlow simply",
        "Remind me tomorrow at 8 AM to drink water"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "YESMIN AI",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.language.displayName} (${settings.language.nativeLabel}) • ${settings.selectedModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onNavigate(YesminRoute.History) },
                            modifier = Modifier.testTag("dashboard_history_btn")
                        ) {
                            Icon(Icons.Filled.History, contentDescription = "Conversation History", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = { onNavigate(YesminRoute.SettingsDashboard) },
                            modifier = Modifier.testTag("dashboard_settings_btn")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings Dashboard", tint = TextPrimary)
                        }
                    }
                }
            }

            // Hero AI Core Card
            item {
                NeonGlassCard(
                    borderColor = ElectricCyan.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadgePill(
                                label = when (connectionStatus) {
                                    is GeminiConnectionStatus.Error -> "Gemini Error"
                                    GeminiConnectionStatus.Testing -> "Testing Gemini..."
                                    else -> if (hasApiKey) "Custom Key Active" else "Gemini AI Active"
                                },
                                accentColor = if (connectionStatus is GeminiConnectionStatus.Error) AmberWarning else EmeraldVerified
                            )
                            StatusBadgePill(
                                label = if (wakeWordRunning) "Wake Word ON" else "Wake Word OFF",
                                accentColor = if (wakeWordRunning) ElectricCyan else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        YesminGlowingAvatar(
                            size = 112.dp,
                            isActive = isListening,
                            rmsLevel = rmsLevel
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val heroStatusText = when (listeningState) {
                            is VoiceListeningState.Listening -> "Listening (${settings.language.nativeLabel})... Speak now"
                            is VoiceListeningState.Partial -> "\"${listeningState.text}\""
                            is VoiceListeningState.Error -> listeningState.message
                            VoiceListeningState.Idle -> "Hi, I'm YESMIN. How can I help?"
                        }
                        Text(
                            text = heroStatusText,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (listeningState is VoiceListeningState.Error) RosePulse else TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SupportedLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { onLanguageChange(lang) },
                                    label = { Text(lang.nativeLabel) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AnimatedNeonWaveform(isActive = isListening, rmsLevel = rmsLevel)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NeonMicButton(
                                    isListening = isListening,
                                    onClick = onMicClick
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(onClick = onLaunchSystemMic) {
                                    Text("System Mic")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onNavigate(YesminRoute.VoiceAssistant) },
                                    label = { Text("Voice Studio") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = ElectricCyan),
                                    modifier = Modifier.testTag("open_voice_studio_chip")
                                )
                                AssistChip(
                                    onClick = { onNavigate(YesminRoute.TextChat) },
                                    label = { Text("Gemini Text Chat") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = NeonViolet),
                                    modifier = Modifier.testTag("open_chat_chip")
                                )
                            }
                        }
                    }
                }
            }

            // Always-On Home Screen Card
            item {
                NeonGlassCard(
                    borderColor = if (wakeWordRunning) EmeraldVerified.copy(alpha = 0.6f) else NeonViolet.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.Widgets,
                                contentDescription = "Home Screen Always-On",
                                tint = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Always-On Home Screen",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "Pin YESMIN bar to phone Home Screen or run 24/7",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = wakeWordRunning,
                            onCheckedChange = onToggleWakeWord,
                            modifier = Modifier.testTag("dashboard_always_on_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPinHomeWidget,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pin_home_widget_btn")
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pin Home Widget")
                        }
                        FilterChip(
                            selected = settings.keepScreenAwake,
                            onClick = { onToggleKeepScreenAwake(!settings.keepScreenAwake) },
                            label = { Text("Screen Awake") },
                            modifier = Modifier.testTag("keep_screen_awake_chip")
                        )
                    }
                }
            }

            // Verified Device Telemetry Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeonGlassCard(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BatteryChargingFull, contentDescription = "Battery", tint = EmeraldVerified)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Battery", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    "${battery.percentage}% ${if (battery.isCharging) "Charging" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    NeonGlassCard(
                        onClick = { onNavigate(YesminRoute.PhoneCall) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Telecom Role",
                                tint = if (permissions.isDefaultDialer) EmeraldVerified else ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Telecom", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    if (permissions.isDefaultDialer) "Default Dialer" else "Setup Calls",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Phone Control Tools
            item {
                Text(
                    text = "Phone Control & Utilities",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quickTools) { tool ->
                        NeonGlassCard(
                            onClick = tool.onClick,
                            borderColor = MidnightBorder,
                            modifier = Modifier.testTag(tool.tag)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(82.dp)
                            ) {
                                Icon(tool.icon, contentDescription = tool.label, tint = ElectricCyan, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(tool.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Try Asking Suggestions
            item {
                Text(
                    text = "Try Asking YESMIN",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sampleCommands.forEach { prompt ->
                        NeonGlassCard(
                            onClick = {
                                onQuickPrompt(prompt)
                                onNavigate(YesminRoute.TextChat)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "\"$prompt\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Module Shortcuts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeonGlassCard(
                        onClick = { onNavigate(YesminRoute.Contacts) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Contacts, contentDescription = "Contacts", tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Contacts & SMS", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                    NeonGlassCard(
                        onClick = { onNavigate(YesminRoute.WakeWordConfig) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Wake Word", tint = NeonViolet)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wake Word", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

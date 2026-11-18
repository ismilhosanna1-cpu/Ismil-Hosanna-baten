package com.example.yesmin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import com.example.yesmin.ui.navigation.YesminRoute

@Composable
fun SettingsDashboardScreen(
    settings: AssistantSettings,
    hasApiKey: Boolean,
    userDisplayName: String,
    userEmail: String,
    onSignOut: () -> Unit,
    onNavigate: (YesminRoute) -> Unit,
    onUpdateSettings: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Settings Dashboard",
                    subtitle = "Configure AI, Cloud Sync, Voice & Privacy",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        NeonGlassCard(
                            borderColor = EmeraldVerified.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Google Account",
                                        tint = EmeraldVerified
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = userDisplayName.ifBlank { "Google Account" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = userEmail.ifBlank { "Cloud Firestore Sync Active" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onSignOut,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RosePulse),
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Gemini API Configuration",
                            subtitle = if (hasApiKey) "${settings.selectedModel} • Encrypted Key Saved" else "Add your Gemini API key & choose model",
                            tag = "nav_gemini_config",
                            onClick = { onNavigate(YesminRoute.GeminiConfig) }
                        )
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.Language,
                            title = "Language & Voice Settings",
                            subtitle = "${settings.language.displayName} (${settings.language.nativeLabel}) • Pitch ${String.format("%.2f", settings.ttsPitch)}",
                            tag = "nav_language_voice",
                            onClick = { onNavigate(YesminRoute.LanguageVoice) }
                        )
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.Hearing,
                            title = "Wake Word Configuration",
                            subtitle = "\"${settings.primaryWakePhrase}\" / \"${settings.secondaryWakePhrase}\"",
                            tag = "nav_wake_word",
                            onClick = { onNavigate(YesminRoute.WakeWordConfig) }
                        )
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.Security,
                            title = "Permissions Dashboard",
                            subtitle = "Mic, Contacts, Dialer Role, Notifications & Accessibility",
                            tag = "nav_permissions",
                            onClick = { onNavigate(YesminRoute.PermissionsDashboard) }
                        )
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.Alarm,
                            title = "Reminders, Alarms & Calendar",
                            subtitle = "Manage scheduled tasks & calendar sync",
                            tag = "nav_reminders",
                            onClick = { onNavigate(YesminRoute.RemindersCalendar) }
                        )
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.History,
                            title = "Conversation History",
                            subtitle = "Browse or clear stored chats",
                            tag = "nav_history",
                            onClick = { onNavigate(YesminRoute.History) }
                        )
                    }
                    item {
                        NeonGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Confirm Calls & SMS", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        "Require confirmation dialog before placing voice-initiated calls or opening SMS composer.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = settings.confirmBeforeCallOrSms,
                                    onCheckedChange = { checked ->
                                        onUpdateSettings { it.copy(confirmBeforeCallOrSms = checked) }
                                    }
                                )
                            }
                        }
                    }
                    item {
                        SettingsNavCard(
                            icon = Icons.Filled.Info,
                            title = "About & Privacy",
                            subtitle = "Security architecture, data wipe & device compatibility",
                            tag = "nav_about_privacy",
                            onClick = { onNavigate(YesminRoute.AboutPrivacy) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    NeonGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ElectricCyan)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open $title", tint = NeonViolet)
        }
    }
}

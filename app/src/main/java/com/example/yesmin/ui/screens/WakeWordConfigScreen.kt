package com.example.yesmin.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.StatusBadgePill
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar

@Composable
fun WakeWordConfigScreen(
    settings: AssistantSettings,
    serviceRunning: Boolean,
    wakeWordStatus: String,
    onToggleService: (Boolean) -> Unit,
    onPinHomeWidget: () -> Unit,
    onUpdateSettings: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var primaryPhrase by remember(settings.primaryWakePhrase) { mutableStateOf(settings.primaryWakePhrase) }
    var secondaryPhrase by remember(settings.secondaryWakePhrase) { mutableStateOf(settings.secondaryWakePhrase) }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleService(true)
        }
    }

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Wake Word Configuration",
                    subtitle = "Compliant Foreground Microphone Service",
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
                    item {
                        NeonGlassCard(
                            borderColor = if (serviceRunning) EmeraldVerified.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Foreground Wake-Word Service", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                StatusBadgePill(
                                    label = if (serviceRunning) "Listening" else "Stopped",
                                    accentColor = if (serviceRunning) EmeraldVerified else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(wakeWordStatus, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enable \"Hey Yesmin\" Listener", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                Switch(
                                    checked = serviceRunning,
                                    onCheckedChange = { enable ->
                                        if (enable && !PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            onToggleService(enable)
                                        }
                                    },
                                    modifier = Modifier.testTag("wake_word_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Displays a persistent microphone notification with a Stop Listening button. Never records silently or hides microphone usage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        NeonGlassCard {
                            Text("Wake Phrases", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = primaryPhrase,
                                onValueChange = { primaryPhrase = it },
                                label = { Text("Primary Wake Phrase") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = secondaryPhrase,
                                onValueChange = { secondaryPhrase = it },
                                label = { Text("Secondary Wake Phrase") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = {
                                onUpdateSettings {
                                    it.copy(
                                        primaryWakePhrase = primaryPhrase.trim().ifBlank { "Hey Yesmin" },
                                        secondaryWakePhrase = secondaryPhrase.trim().ifBlank { "Yesmin" }
                                    )
                                }
                            }) {
                                Text("Save Wake Phrases")
                            }
                        }
                    }

                    item {
                        NeonGlassCard {
                            Text("Home Screen & MIUI Always-On Setup", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Pin YESMIN AI directly onto your phone Home Screen, set it as your Default Assistant, and set MIUI Battery Saver to \"No restrictions\" + Autostart so Redmi Note 8 keeps it running.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onPinHomeWidget,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pin YESMIN Widget to Home Screen")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Set as Phone Default Assistant")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open App Info (MIUI Autostart & Battery)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

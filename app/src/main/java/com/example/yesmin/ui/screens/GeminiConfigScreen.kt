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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.GeminiConnectionStatus
import com.example.yesmin.data.PersonalityTone
import com.example.yesmin.data.ResponseLength
import com.example.yesmin.data.SupportedGeminiModels
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.StatusBadgePill
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar

@Composable
fun GeminiConfigScreen(
    settings: AssistantSettings,
    maskedApiKey: String,
    connectionStatus: GeminiConnectionStatus,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: () -> Unit,
    onUpdateSettings: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var keyInput by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf(settings.selectedModel) }
    var customHint by remember(settings.customSystemHint) { mutableStateOf(settings.customSystemHint) }

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Gemini API Configuration",
                    subtitle = "Encrypted via Android Keystore AES-256",
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
                    // Connection status & API Key entry
                    item {
                        NeonGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("API Key & Connection", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                val (badgeLabel, badgeColor) = when (connectionStatus) {
                                    GeminiConnectionStatus.NotConfigured -> "Built-in Gemini" to EmeraldVerified
                                    GeminiConnectionStatus.ReadyUnverified -> "Custom Key Saved" to ElectricCyan
                                    GeminiConnectionStatus.Testing -> "Testing..." to ElectricCyan
                                    is GeminiConnectionStatus.Connected -> "Gemini Connected" to EmeraldVerified
                                    is GeminiConnectionStatus.Error -> "Error" to RosePulse
                                }
                                StatusBadgePill(label = badgeLabel, accentColor = badgeColor)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (maskedApiKey == "Not configured") {
                                    "Mode: Built-in Gemini AI (Firebase AI Logic — no key needed)"
                                } else {
                                    "Mode: Custom Key Override ($maskedApiKey)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            if (connectionStatus is GeminiConnectionStatus.Error) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(connectionStatus.message, style = MaterialTheme.typography.bodyMedium, color = RosePulse)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                label = { Text("Enter new Gemini API Key") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    androidx.compose.material3.TextButton(onClick = { revealKey = !revealKey }) {
                                        Text(if (revealKey) "Hide" else "Show")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_api_key_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (keyInput.isNotBlank()) {
                                            onSaveApiKey(keyInput)
                                            keyInput = ""
                                        }
                                    },
                                    enabled = keyInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid),
                                    modifier = Modifier.testTag("save_api_key_button")
                                ) {
                                    Text("Save Encrypted")
                                }
                                OutlinedButton(
                                    onClick = onTestConnection,
                                    modifier = Modifier.testTag("test_connection_button")
                                ) {
                                    Text("Test API")
                                }
                                OutlinedButton(onClick = onClearApiKey) {
                                    Text("Remove", color = RosePulse)
                                }
                            }
                        }
                    }

                    // Model selection
                    item {
                        NeonGlassCard {
                            Text("Supported Gemini Models", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            SupportedGeminiModels.presets.forEach { model ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings.selectedModel == model.id,
                                        onClick = {
                                            customModelInput = model.id
                                            onUpdateSettings { it.copy(selectedModel = model.id) }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(model.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { customModelInput = it },
                                    label = { Text("Active Model ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    if (customModelInput.isNotBlank()) {
                                        onUpdateSettings { it.copy(selectedModel = customModelInput.trim()) }
                                    }
                                }) {
                                    Text("Apply")
                                }
                            }
                        }
                    }

                    // Personality & Response Length
                    item {
                        NeonGlassCard {
                            Text("Assistant Personality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            PersonalityTone.entries.forEach { tone ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = settings.personality == tone,
                                        onClick = { onUpdateSettings { it.copy(personality = tone) } }
                                    )
                                    Text(tone.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Response Length", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ResponseLength.entries.forEach { len ->
                                    FilterChip(
                                        selected = settings.responseLength == len,
                                        onClick = { onUpdateSettings { it.copy(responseLength = len) } },
                                        label = { Text(len.label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Creativity (Temperature): ${String.format("%.1f", settings.temperature)}", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                            Slider(
                                value = settings.temperature,
                                onValueChange = { value -> onUpdateSettings { it.copy(temperature = value) } },
                                valueRange = 0.0f..1.4f
                            )
                        }
                    }

                    // Memory, Streaming & Custom Behaviour
                    item {
                        NeonGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Conversation Memory", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Includes recent turns for context-aware replies.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Switch(
                                    checked = settings.memoryEnabled,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(memoryEnabled = checked) } }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SSE Streaming Responses", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Stream tokens live as Gemini generates them.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Switch(
                                    checked = settings.streamingEnabled,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(streamingEnabled = checked) } }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customHint,
                                onValueChange = { customHint = it },
                                label = { Text("Custom AI Behaviour Instruction (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onUpdateSettings { it.copy(customSystemHint = customHint.trim()) } }) {
                                    Text("Save Behaviour")
                                }
                                OutlinedButton(onClick = onClearHistory) {
                                    Text("Clear History", color = RosePulse)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

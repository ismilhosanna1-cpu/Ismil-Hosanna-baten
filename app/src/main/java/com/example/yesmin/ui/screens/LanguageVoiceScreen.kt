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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import com.example.yesmin.voice.TtsVoiceItem

@Composable
fun LanguageVoiceScreen(
    settings: AssistantSettings,
    availableVoices: List<TtsVoiceItem>,
    onUpdateSettings: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onTestVoice: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Language & Voice",
                    subtitle = "Bengali • Hindi • English & TTS Engine",
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
                        NeonGlassCard {
                            Text("Preferred Primary Language", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            SupportedLanguage.entries.forEach { lang ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = settings.language == lang,
                                        onClick = { onUpdateSettings { it.copy(language = lang) } }
                                    )
                                    Text("${lang.displayName} (${lang.nativeLabel})", color = TextPrimary)
                                }
                            }
                        }
                    }

                    item {
                        NeonGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Speak AI Replies", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Read answers aloud using female-preferred voice.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Switch(
                                    checked = settings.autoSpeakReplies,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(autoSpeakReplies = checked) } }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Voice Pitch: ${String.format("%.2f", settings.ttsPitch)}", color = ElectricCyan)
                            Slider(
                                value = settings.ttsPitch,
                                onValueChange = { v -> onUpdateSettings { it.copy(ttsPitch = v) } },
                                valueRange = 0.7f..1.4f
                            )
                            Text("Speech Speed: ${String.format("%.2f", settings.ttsSpeed)}", color = ElectricCyan)
                            Slider(
                                value = settings.ttsSpeed,
                                onValueChange = { v -> onUpdateSettings { it.copy(ttsSpeed = v) } },
                                valueRange = 0.7f..1.5f
                            )
                            Button(
                                onClick = {
                                    val sample = when (settings.language) {
                                        SupportedLanguage.BENGALI -> "হ্যালো! আমি ইয়েসমিন, আপনার স্মার্ট এআই সঙ্গী।"
                                        SupportedLanguage.HINDI -> "नमस्ते! मैं येस्मिन हूँ, आपकी स्मार्ट एआई साथी।"
                                        SupportedLanguage.ENGLISH -> "Hello! I am YESMIN, your intelligent Android companion."
                                    }
                                    onTestVoice(sample)
                                }
                            ) {
                                Text("Test YESMIN Voice")
                            }
                        }
                    }

                    item {
                        Text("Installed Device TTS Voices (EN / BN / HI)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    if (availableVoices.isEmpty()) {
                        item {
                            NeonGlassCard {
                                Text(
                                    "Default system TTS voice will be used automatically. Install Google Speech Services voice packs for Bengali/Hindi offline voices.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(availableVoices.take(20), key = { it.name }) { voice ->
                            NeonGlassCard(
                                onClick = { onUpdateSettings { it.copy(preferredVoiceName = voice.name) } }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings.preferredVoiceName == voice.name,
                                        onClick = { onUpdateSettings { it.copy(preferredVoiceName = voice.name) } }
                                    )
                                    Column {
                                        Text(voice.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                        Text(
                                            "${voice.localeTag} • ${if (voice.isNetwork) "Network Voice" else "Offline Voice"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

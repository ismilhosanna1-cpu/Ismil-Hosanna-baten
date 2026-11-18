package com.example.yesmin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar

@Composable
fun AboutPrivacyScreen(
    onWipeAllData: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showWipeConfirm by remember { mutableStateOf(false) }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe All Local Data?") },
            text = { Text("This permanently deletes your encrypted Gemini API key, Room conversations, reminders, call logs, and preferences from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onWipeAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RosePulse)
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") }
            }
        )
    }

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "About & Privacy",
                    subtitle = "YESMIN AI v1.0 • Package com.yesmin.ai",
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
                            Text("Privacy & Security Guarantees", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Encrypted API Storage: Your Gemini API key is encrypted with Android Keystore AES-256-GCM and never stored in plaintext.\n" +
                                "• HTTPS Only: AI and live weather requests travel strictly over TLS/HTTPS.\n" +
                                "• No Hidden Surveillance: Microphone listening runs only on explicit tap or via a visible Foreground Service notification with a Stop button.\n" +
                                "• No Silent Messaging: SMS commands always open your system SMS composer after confirmation.\n" +
                                "• Cloud Sync & Account Isolation: Conversations and reminders sync securely to your Google account in Cloud Firestore, protected by zero-trust per-user security rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    item {
                        NeonGlassCard {
                            Text("Android Platform Limitations", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Answering, rejecting, muting, and speakerphone switching require YESMIN AI to be selected as Default Dialer via Android RoleManager.\n" +
                                "• Android 10+ prevents third-party apps from silently toggling Wi-Fi, Bluetooth, or Mobile Data; YESMIN opens the official Settings panel instead.\n" +
                                "• Wake-word listening uses Android SpeechRecognizer in a foreground service rather than a proprietary low-power DSP chip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        NeonGlassCard(borderColor = RosePulse.copy(alpha = 0.5f)) {
                            Text("Local Data Deletion", style = MaterialTheme.typography.titleMedium, color = RosePulse)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Remove all locally stored chat history, reminders, encrypted API keys, and settings in one tap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showWipeConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RosePulse),
                                modifier = Modifier.testTag("wipe_all_data_button")
                            ) {
                                Text("Delete All Local Data")
                            }
                        }
                    }
                }
            }
        }
    }
}

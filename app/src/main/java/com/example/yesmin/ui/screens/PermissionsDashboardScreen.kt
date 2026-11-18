package com.example.yesmin.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.security.PermissionStatusSnapshot
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.StatusBadgePill
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar

@Composable
fun PermissionsDashboardScreen(
    permissions: PermissionStatusSnapshot,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefresh()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onRefresh()
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Permissions Dashboard",
                    subtitle = "Transparent user-controlled access",
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
                        PermissionCard(
                            title = "Microphone (Voice & Wake Word)",
                            description = "Used only when you tap the mic or enable the visible Foreground Wake-Word Service.",
                            granted = permissions.recordAudio,
                            actionLabel = "Grant Mic",
                            onAction = { runtimePermLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Contacts & Phone Calls",
                            description = "Enables spoken contact lookup, caller ID announcement, and direct calling.",
                            granted = permissions.readContacts && permissions.callPhone && permissions.readPhoneState,
                            actionLabel = "Grant Phone & Contacts",
                            onAction = {
                                val list = mutableListOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    list.add(Manifest.permission.ANSWER_PHONE_CALLS)
                                }
                                runtimePermLauncher.launch(list.toTypedArray())
                            }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Calendar & Notifications",
                            description = "Create/list calendar events and display scheduled reminder alerts.",
                            granted = permissions.readCalendar && permissions.writeCalendar && permissions.postNotifications,
                            actionLabel = "Grant Calendar & Alerts",
                            onAction = {
                                val list = mutableListOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                runtimePermLauncher.launch(list.toTypedArray())
                            }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Default Dialer Role (InCallService)",
                            description = "Required by Android 10+ so YESMIN can answer, reject, mute, and manage live calls.",
                            granted = permissions.isDefaultDialer,
                            actionLabel = "Set Dialer Role",
                            onAction = {
                                PermissionHelper.createDefaultDialerIntent(context)?.let {
                                    settingsLauncher.launch(it)
                                }
                            }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Notification Listener Access",
                            description = "Optional: allows YESMIN to summarize recent notifications only when you ask.",
                            granted = permissions.notificationListenerEnabled,
                            actionLabel = "Open Notification Access",
                            onAction = { settingsLauncher.launch(PermissionHelper.notificationListenerSettingsIntent()) }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Accessibility Helper Service",
                            description = "Optional: allows voice-triggered Home, Back, Recents, and Notification shade actions. No screen content scraping.",
                            granted = permissions.accessibilityEnabled,
                            actionLabel = "Open Accessibility Settings",
                            onAction = { settingsLauncher.launch(PermissionHelper.accessibilitySettingsIntent()) }
                        )
                    }
                    item {
                        PermissionCard(
                            title = "Exact Alarms (Android 12+)",
                            description = "Ensures reminders fire at the exact scheduled minute (falls back to WorkManager otherwise).",
                            granted = permissions.exactAlarms,
                            actionLabel = "Configure Exact Alarms",
                            onAction = { settingsLauncher.launch(PermissionHelper.exactAlarmSettingsIntent(context)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    NeonGlassCard(
        borderColor = if (granted) EmeraldVerified.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            StatusBadgePill(
                label = if (granted) "Granted" else "Needed",
                accentColor = if (granted) EmeraldVerified else AmberWarning
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        if (granted) {
            OutlinedButton(onClick = onAction) {
                Text("Review")
            }
        } else {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

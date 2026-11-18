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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.cloud.CloudReminder
import com.example.yesmin.cloud.UiState
import com.example.yesmin.data.CalendarEventSummary
import com.example.yesmin.telecom.PhoneActionExecutor
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersCalendarScreen(
    remindersState: UiState<List<CloudReminder>>,
    calendarEvents: List<CalendarEventSummary>,
    phoneActionExecutor: PhoneActionExecutor,
    onCreateReminder: (title: String, notes: String, triggerAtMillis: Long, syncCalendar: Boolean) -> Unit,
    onToggleReminder: (String, Boolean) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onRefreshCalendar: () -> Unit,
    onBannerMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var delayMinutes by remember { mutableIntStateOf(15) }
    var syncCalendar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshCalendar()
    }

    val fmt = SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.getDefault())
    val reminders = (remindersState as? UiState.Success)?.data.orEmpty()

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Reminders & Calendar",
                    subtitle = "Cloud Firestore • AlarmManager • Calendar",
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
                            Text("Create Scheduled Reminder", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Reminder title") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reminder_title_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes (optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 15, 60, 1440).forEach { mins ->
                                    val label = when (mins) {
                                        1 -> "1m"
                                        15 -> "15m"
                                        60 -> "1h"
                                        else -> "24h"
                                    }
                                    FilterChip(
                                        selected = delayMinutes == mins,
                                        onClick = { delayMinutes = mins },
                                        label = { Text("In $label") }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = syncCalendar, onCheckedChange = { syncCalendar = it })
                                Text("Also add to Android Calendar", color = TextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (title.isNotBlank()) {
                                            val trigger = System.currentTimeMillis() + delayMinutes * 60_000L
                                            onCreateReminder(title, notes, trigger, syncCalendar)
                                            title = ""
                                            notes = ""
                                        }
                                    },
                                    enabled = title.isNotBlank(),
                                    modifier = Modifier.testTag("create_reminder_button")
                                ) {
                                    Text("Schedule Reminder")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val fb = phoneActionExecutor.setSystemAlarm(7, 0, "YESMIN Morning Alarm")
                                        onBannerMessage(fb.message)
                                    }
                                ) {
                                    Icon(Icons.Filled.AlarmAdd, contentDescription = null)
                                    Text(" 7:00 AM Alarm")
                                }
                            }
                        }
                    }

                    item {
                        Text("Synced Reminders (${reminders.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    when (remindersState) {
                        UiState.Loading -> {
                            item {
                                NeonGlassCard {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(color = ElectricCyan)
                                        Text("Syncing reminders from Cloud Firestore...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            item {
                                NeonGlassCard(borderColor = RosePulse.copy(alpha = 0.6f)) {
                                    Text("Failed to sync reminders: ${remindersState.message}", style = MaterialTheme.typography.bodyMedium, color = RosePulse)
                                }
                            }
                        }
                        is UiState.Success -> {
                            if (reminders.isEmpty()) {
                                item {
                                    NeonGlassCard {
                                        Text("No reminders scheduled yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            } else {
                                items(reminders, key = { it.id }) { rem ->
                                    NeonGlassCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { onToggleReminder(rem.id, !rem.completed) }) {
                                                Icon(
                                                    imageVector = if (rem.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                                    contentDescription = "Toggle completed",
                                                    tint = if (rem.completed) EmeraldVerified else ElectricCyan
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rem.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text(fmt.format(Date(rem.triggerAtMillis)), style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                                if (rem.notes.isNotBlank()) {
                                                    Text(rem.notes, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                                }
                                            }
                                            IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = RosePulse)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Upcoming Device Calendar Events", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    if (calendarEvents.isEmpty()) {
                        item {
                            NeonGlassCard {
                                Text(
                                    "No upcoming calendar events found (or Calendar permission not yet granted).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(calendarEvents, key = { it.id }) { ev ->
                            NeonGlassCard {
                                Text(ev.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(fmt.format(Date(ev.startMillis)), style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                if (!ev.location.isNullOrBlank()) {
                                    Text(ev.location, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

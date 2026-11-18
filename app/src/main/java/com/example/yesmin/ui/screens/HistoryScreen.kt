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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RosePulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.cloud.CloudConversation
import com.example.yesmin.cloud.UiState
import com.example.yesmin.ui.components.NeonGlassCard
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.components.YesminTopBar
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(
    conversationsState: UiState<List<CloudConversation>>,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val formatter = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())
    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()

    YesminScreenBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                YesminTopBar(
                    title = "Conversation History",
                    subtitle = "${conversations.size} synced in Cloud Firestore",
                    onBack = onBack,
                    actions = {
                        if (conversations.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAll,
                                modifier = Modifier.testTag("clear_all_history_btn")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all conversations", tint = RosePulse)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                when (conversationsState) {
                    UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }
                    is UiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Unable to load cloud history", style = MaterialTheme.typography.titleLarge, color = RosePulse)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(conversationsState.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    is UiState.Success -> {
                        if (conversations.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No conversation history yet", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Your chats with YESMIN sync privately to your Google account in Cloud Firestore.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 620.dp),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(conversations, key = { it.id }) { conv ->
                                    val displayTimestamp = conv.updatedAt ?: conv.createdAt ?: Timestamp.now()
                                    NeonGlassCard(
                                        onClick = { onOpenConversation(conv.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(conv.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text(
                                                    "${conv.languageCode.uppercase()} • ${formatter.format(displayTimestamp.toDate())}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan
                                                )
                                            }
                                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete conversation", tint = RosePulse)
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
    }
}

package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ui.theme.YesminTheme
import com.example.yesmin.ai.configureAppCheck
import com.example.yesmin.cloud.CloudSyncRepository
import com.example.yesmin.cloud.authStateFlow
import com.example.yesmin.cloud.signOutFromGoogle
import com.example.yesmin.core.YesminApplication
import com.example.yesmin.ui.navigation.YesminAppShell
import com.example.yesmin.ui.screens.AuthScreen
import com.example.yesmin.ui.screens.SplashScreen
import com.example.yesmin.ui.viewmodel.YesminViewModel
import com.example.yesmin.voice.YesminVoiceService
import com.example.yesmin.widget.YesminHomeWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow

private sealed interface PendingLaunchPayload {
    data class VoiceCommand(val text: String) : PendingLaunchPayload
    data class WidgetAction(val action: String) : PendingLaunchPayload
}

class MainActivity : ComponentActivity() {

    private val pendingLaunchPayload = MutableStateFlow<PendingLaunchPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before any UI, ViewModel, or AI operations
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        captureIncomingIntent(intent)

        val app = application as YesminApplication
        setContent {
            YesminTheme {
                YesminRootGate(
                    app = app,
                    pendingLaunchFlow = pendingLaunchPayload,
                    onConsumePendingLaunch = { pendingLaunchPayload.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureIncomingIntent(intent)
    }

    private fun captureIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val voiceCmd = intent.getStringExtra(YesminVoiceService.EXTRA_VOICE_COMMAND)
        if (!voiceCmd.isNullOrBlank()) {
            pendingLaunchPayload.value = PendingLaunchPayload.VoiceCommand(voiceCmd)
            return
        }
        val widgetAction = intent.getStringExtra(YesminHomeWidgetProvider.EXTRA_WIDGET_ACTION)
        if (!widgetAction.isNullOrBlank()) {
            pendingLaunchPayload.value = PendingLaunchPayload.WidgetAction(widgetAction)
            return
        }
        if (intent.action == Intent.ACTION_ASSIST) {
            pendingLaunchPayload.value = PendingLaunchPayload.WidgetAction(YesminHomeWidgetProvider.WIDGET_ACTION_VOICE)
        }
    }
}

@Composable
private fun YesminRootGate(
    app: YesminApplication,
    pendingLaunchFlow: MutableStateFlow<PendingLaunchPayload?>,
    onConsumePendingLaunch: () -> Unit
) {
    var splashFinished by remember { mutableStateOf(false) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    if (!splashFinished) {
        SplashScreen(onFinished = { splashFinished = true })
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* Reactive authStateFlow automatically switches to authenticated shell */ })
    } else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        key(user.uid) {
            val viewModel: YesminViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val appInstance = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = appInstance.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        YesminViewModel(
                            appContext = appInstance.applicationContext,
                            container = app.container,
                            cloudSyncRepository = CloudSyncRepository(db),
                            currentUserId = user.uid
                        )
                    }
                }
            )

            LifecycleResumeEffect(viewModel) {
                viewModel.refreshSystemState()
                onPauseOrDispose { }
            }

            val pendingPayload by pendingLaunchFlow.collectAsStateWithLifecycle()
            LaunchedEffect(pendingPayload, user.uid) {
                when (val payload = pendingPayload) {
                    is PendingLaunchPayload.VoiceCommand -> {
                        onConsumePendingLaunch()
                        viewModel.submitPrompt(payload.text, speakResponse = true)
                    }
                    is PendingLaunchPayload.WidgetAction -> {
                        onConsumePendingLaunch()
                        viewModel.queueLaunchAction(payload.action)
                    }
                    null -> Unit
                }
            }

            YesminAppShell(
                viewModel = viewModel,
                container = app.container,
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty(),
                onSignOut = {
                    viewModel.stopVoiceListening()
                    viewModel.stopSpeaking()
                    signOutFromGoogle(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

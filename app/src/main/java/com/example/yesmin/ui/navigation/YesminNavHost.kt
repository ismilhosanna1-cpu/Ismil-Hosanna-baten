package com.example.yesmin.ui.navigation

import android.Manifest
import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.yesmin.core.AppContainer
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.ui.components.SensitiveActionConfirmationDialog
import com.example.yesmin.ui.components.YesminScreenBackdrop
import com.example.yesmin.ui.screens.AboutPrivacyScreen
import com.example.yesmin.ui.screens.ContactsScreen
import com.example.yesmin.ui.screens.DashboardScreen
import com.example.yesmin.ui.screens.GeminiConfigScreen
import com.example.yesmin.ui.screens.HistoryScreen
import com.example.yesmin.ui.screens.LanguageVoiceScreen
import com.example.yesmin.ui.screens.PermissionsDashboardScreen
import com.example.yesmin.ui.screens.PhoneCallScreen
import com.example.yesmin.ui.screens.RemindersCalendarScreen
import com.example.yesmin.ui.screens.SettingsDashboardScreen
import com.example.yesmin.ui.screens.TextChatScreen
import com.example.yesmin.ui.screens.VoiceAssistantScreen
import com.example.yesmin.ui.screens.WakeWordConfigScreen
import com.example.yesmin.ui.viewmodel.YesminViewModel
import com.example.yesmin.voice.VoiceListeningState
import com.example.yesmin.widget.YesminHomeWidgetProvider

private data class TopNavItem(
    val route: YesminRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun YesminAppShell(
    viewModel: YesminViewModel,
    container: AppContainer,
    userDisplayName: String,
    userEmail: String,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val wakeWordRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
    val wakeWordStatus by viewModel.wakeWordStatus.collectAsStateWithLifecycle()
    val listeningState by viewModel.listeningState.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingPreview by viewModel.streamingPreview.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val lastTranscript by viewModel.lastTranscript.collectAsStateWithLifecycle()
    val lastVoiceReply by viewModel.lastVoiceReply.collectAsStateWithLifecycle()
    val liveCallState by viewModel.liveCallState.collectAsStateWithLifecycle()
    val deviceCallLog by viewModel.deviceCallLog.collectAsStateWithLifecycle()
    val assistantCallLogs by viewModel.assistantCallLogs.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableTtsVoices.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val bannerFeedback by viewModel.bannerFeedback.collectAsStateWithLifecycle()
    val pendingLaunchAction by viewModel.pendingLaunchAction.collectAsStateWithLifecycle()

    val rootView = LocalView.current
    SideEffect {
        rootView.keepScreenOn = settings.keepScreenAwake
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemState()
        if (granted) viewModel.startVoiceListening()
    }

    val systemMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                viewModel.handleVoiceResult(spoken)
            }
        }
    }

    val launchSystemMicDialog = {
        try {
            systemMicLauncher.launch(viewModel.createSystemSpeechIntent())
        } catch (_: Exception) {
            viewModel.executeQuickPhoneAction {
                "No system voice input app found. Please install or enable Google Speech Services."
            }
        }
    }

    LaunchedEffect(bannerFeedback) {
        val msg = bannerFeedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    LaunchedEffect(pendingLaunchAction) {
        when (pendingLaunchAction) {
            YesminHomeWidgetProvider.WIDGET_ACTION_VOICE -> {
                viewModel.consumeLaunchAction()
                navController.navigate(YesminRoute.Dashboard) {
                    popUpTo(YesminRoute.Dashboard) { inclusive = false }
                    launchSingleTop = true
                }
                if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                    viewModel.startVoiceListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            YesminHomeWidgetProvider.WIDGET_ACTION_CHAT -> {
                viewModel.consumeLaunchAction()
                navController.navigate(YesminRoute.Dashboard) {
                    popUpTo(YesminRoute.Dashboard) { inclusive = false }
                    launchSingleTop = true
                }
                navController.navigate(YesminRoute.TextChat) { launchSingleTop = true }
            }
        }
    }

    SensitiveActionConfirmationDialog(
        pending = pendingConfirmation,
        onConfirm = { viewModel.confirmPendingAction() },
        onDismiss = { viewModel.dismissConfirmation() }
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevelHub = currentDest?.hasRoute<YesminRoute.Dashboard>() == true

    val topNavItems = listOf(
        TopNavItem(YesminRoute.Dashboard, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        TopNavItem(YesminRoute.VoiceAssistant, "Voice", Icons.Filled.Mic, Icons.Outlined.Mic, "nav_voice"),
        TopNavItem(YesminRoute.TextChat, "Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_chat"),
        TopNavItem(YesminRoute.PhoneCall, "Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
        TopNavItem(YesminRoute.SettingsDashboard, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        YesminScreenBackdrop {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && isTopLevelHub) {
                    NavigationRail(containerColor = MidnightCard) {
                        topNavItems.forEach { item ->
                            val selected = item.route == YesminRoute.Dashboard
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(item.route) { launchSingleTop = true }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        if (!useRail && isTopLevelHub) {
                            NavigationBar(
                                containerColor = MidnightCard.copy(alpha = 0.96f),
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                topNavItems.forEach { item ->
                                    val selected = item.route == YesminRoute.Dashboard
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                navController.navigate(item.route) { launchSingleTop = true }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ElectricCyan,
                                            selectedTextColor = ElectricCyan,
                                            indicatorColor = MidnightElevated,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag(item.tag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = YesminRoute.Dashboard,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {

                        composable<YesminRoute.Dashboard> {
                            DashboardScreen(
                                settings = settings,
                                hasApiKey = hasApiKey,
                                connectionStatus = connectionStatus,
                                battery = battery,
                                permissions = permissions,
                                wakeWordRunning = wakeWordRunning,
                                listeningState = listeningState,
                                rmsLevel = rmsLevel,
                                phoneActionExecutor = container.phoneActionExecutor,
                                onNavigate = { route ->
                                    navController.navigate(route) { launchSingleTop = true }
                                },
                                onMicClick = {
                                    val isListeningNow = listeningState is VoiceListeningState.Listening ||
                                        listeningState is VoiceListeningState.Partial
                                    if (isListeningNow) {
                                        viewModel.stopVoiceListening()
                                    } else if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                        viewModel.startVoiceListening()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onLaunchSystemMic = {
                                    if (PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                        launchSystemMicDialog()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onLanguageChange = { viewModel.setLanguage(it) },
                                onToggleWakeWord = { enable ->
                                    if (enable && !PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleWakeWordService(enable)
                                    }
                                },
                                onToggleKeepScreenAwake = { viewModel.setKeepScreenAwake(it) },
                                onPinHomeWidget = { viewModel.pinHomeWidget() },
                                onQuickPrompt = { prompt ->
                                    viewModel.submitPrompt(prompt, speakResponse = false)
                                },
                                onBannerAction = { block ->
                                    viewModel.executeQuickPhoneAction(block)
                                }
                            )
                        }

                        composable<YesminRoute.VoiceAssistant> {
                            VoiceAssistantScreen(
                                settings = settings,
                                listeningState = listeningState,
                                rmsLevel = rmsLevel,
                                isSpeaking = isSpeaking,
                                isGenerating = isGenerating,
                                lastTranscript = lastTranscript,
                                lastReply = lastVoiceReply,
                                onLanguageChange = { viewModel.setLanguage(it) },
                                onStartListening = { viewModel.startVoiceListening() },
                                onStopListening = { viewModel.stopVoiceListening() },
                                onLaunchSystemMic = { launchSystemMicDialog() },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onSpeakReply = { viewModel.speakText(lastVoiceReply) },
                                onQuickVoiceCommand = { cmd -> viewModel.submitPrompt(cmd, speakResponse = true) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.TextChat> {
                            TextChatScreen(
                                settings = settings,
                                messagesState = activeMessagesState,
                                streamingPreview = streamingPreview,
                                isGenerating = isGenerating,
                                onSendMessage = { text -> viewModel.submitPrompt(text, speakResponse = false) },
                                onNewConversation = { viewModel.startNewConversation() },
                                onSpeakMessage = { msg -> viewModel.speakText(msg) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.PhoneCall> {
                            PhoneCallScreen(
                                settings = settings,
                                callState = liveCallState,
                                permissions = permissions,
                                deviceCallLog = deviceCallLog,
                                assistantCallLogs = assistantCallLogs,
                                onRefreshLogs = {
                                    viewModel.refreshSystemState()
                                    viewModel.refreshDeviceCallLog()
                                },
                                onPlaceCall = { label, num -> viewModel.requestCallWithSafetyCheck(label, num) },
                                onAnswerCall = { viewModel.answerCall() },
                                onRejectCall = { viewModel.rejectCall() },
                                onEndCall = { viewModel.endCall() },
                                onToggleMute = { viewModel.toggleMute() },
                                onToggleSpeaker = { viewModel.toggleSpeakerphone() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.Contacts> {
                            ContactsScreen(
                                contacts = contacts,
                                permissions = permissions,
                                onSearchContacts = { viewModel.refreshContacts(it) },
                                onCallContact = { c -> viewModel.requestCallWithSafetyCheck(c.name, c.phoneNumber) },
                                onSmsContact = { c -> viewModel.requestSmsWithSafetyCheck(c.name, c.phoneNumber) },
                                onRefreshPermissions = { viewModel.refreshSystemState() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.History> {
                            HistoryScreen(
                                conversationsState = conversationsState,
                                onOpenConversation = { id ->
                                    viewModel.openConversation(id)
                                    navController.navigate(YesminRoute.TextChat) { launchSingleTop = true }
                                },
                                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                                onClearAll = { viewModel.clearAllConversations() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.SettingsDashboard> {
                            SettingsDashboardScreen(
                                settings = settings,
                                hasApiKey = hasApiKey,
                                userDisplayName = userDisplayName,
                                userEmail = userEmail,
                                onSignOut = onSignOut,
                                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.GeminiConfig> {
                            GeminiConfigScreen(
                                settings = settings,
                                maskedApiKey = viewModel.getMaskedApiKey(),
                                connectionStatus = connectionStatus,
                                onSaveApiKey = { viewModel.saveGeminiApiKey(it) },
                                onClearApiKey = { viewModel.saveGeminiApiKey("") },
                                onTestConnection = { viewModel.testGeminiConnection() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onClearHistory = { viewModel.clearAllConversations() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.LanguageVoice> {
                            LanguageVoiceScreen(
                                settings = settings,
                                availableVoices = availableVoices,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onTestVoice = { sample -> viewModel.speakText(sample) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.PermissionsDashboard> {
                            PermissionsDashboardScreen(
                                permissions = permissions,
                                onRefresh = { viewModel.refreshSystemState() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.WakeWordConfig> {
                            WakeWordConfigScreen(
                                settings = settings,
                                serviceRunning = wakeWordRunning,
                                wakeWordStatus = wakeWordStatus,
                                onToggleService = { viewModel.toggleWakeWordService(it) },
                                onPinHomeWidget = { viewModel.pinHomeWidget() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.AboutPrivacy> {
                            AboutPrivacyScreen(
                                onWipeAllData = { viewModel.wipeAllLocalData() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YesminRoute.RemindersCalendar> {
                            RemindersCalendarScreen(
                                remindersState = remindersState,
                                calendarEvents = calendarEvents,
                                phoneActionExecutor = container.phoneActionExecutor,
                                onCreateReminder = { title, notes, time, cal ->
                                    viewModel.createReminder(title, notes, time, cal)
                                },
                                onToggleReminder = { id, done -> viewModel.toggleReminderDone(id, done) },
                                onDeleteReminder = { id -> viewModel.deleteReminder(id) },
                                onRefreshCalendar = { viewModel.refreshCalendarEvents() },
                                onBannerMessage = { msg -> viewModel.executeQuickPhoneAction { msg } },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

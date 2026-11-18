package com.example.yesmin.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yesmin.ai.GeminiStreamEvent
import com.example.yesmin.ai.IntentOutcome
import com.example.yesmin.cloud.CloudConversation
import com.example.yesmin.cloud.CloudMessage
import com.example.yesmin.cloud.CloudReminder
import com.example.yesmin.cloud.CloudSyncRepository
import com.example.yesmin.cloud.UiState
import com.example.yesmin.core.AppContainer
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.CalendarEventSummary
import com.example.yesmin.data.ContactInfo
import com.example.yesmin.data.DeviceCallLogEntry
import com.example.yesmin.data.GeminiConnectionStatus
import com.example.yesmin.data.NotificationSummary
import com.example.yesmin.data.PendingConfirmation
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.database.AssistantCallLogEntity
import com.example.yesmin.database.MessageEntity
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.security.PermissionStatusSnapshot
import com.example.yesmin.service.YesminNotificationListener
import com.example.yesmin.telecom.ActiveCallState
import com.example.yesmin.telecom.BatteryTelemetry
import com.example.yesmin.telecom.CallStateHolder
import com.example.yesmin.voice.TtsVoiceItem
import com.example.yesmin.voice.VoiceListeningState
import com.example.yesmin.voice.WakeWordBus
import com.example.yesmin.voice.YesminVoiceService
import com.example.yesmin.widget.YesminHomeWidgetProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class YesminViewModel(
    private val appContext: Context,
    private val container: AppContainer,
    private val cloudSyncRepository: CloudSyncRepository,
    private val currentUserId: String
) : ViewModel() {

    val settings: StateFlow<AssistantSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AssistantSettings())

    val hasApiKey: StateFlow<Boolean> = container.settingsRepository.hasApiKeyFlow

    val conversationsState: StateFlow<UiState<List<CloudConversation>>> =
        cloudSyncRepository.observeConversations(currentUserId)
            .map<List<CloudConversation>, UiState<List<CloudConversation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud conversations", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to sync conversations"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessagesState: StateFlow<UiState<List<CloudMessage>>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf<UiState<List<CloudMessage>>>(UiState.Success(emptyList()))
            } else {
                cloudSyncRepository.observeMessages(currentUserId, convId)
                    .map<List<CloudMessage>, UiState<List<CloudMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing cloud messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to sync messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    val remindersState: StateFlow<UiState<List<CloudReminder>>> =
        cloudSyncRepository.observeReminders(currentUserId)
            .map<List<CloudReminder>, UiState<List<CloudReminder>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud reminders", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to sync reminders"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val assistantCallLogs: StateFlow<List<AssistantCallLogEntity>> = container.contactsRepository.assistantCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val liveCallState: StateFlow<ActiveCallState> = CallStateHolder.callState
    val listeningState: StateFlow<VoiceListeningState> = container.speechManager.listeningState
    val rmsLevel: StateFlow<Float> = container.speechManager.rmsLevel
    val isSpeaking: StateFlow<Boolean> = container.ttsManager.isSpeaking
    val availableTtsVoices: StateFlow<List<TtsVoiceItem>> = container.ttsManager.availableVoices
    val wakeWordServiceRunning: StateFlow<Boolean> = WakeWordBus.serviceRunning
    val wakeWordStatus: StateFlow<String> = WakeWordBus.lastStatus
    val recentNotifications: StateFlow<List<NotificationSummary>> = YesminNotificationListener.recentNotifications

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingPreview = MutableStateFlow("")
    val streamingPreview: StateFlow<String> = _streamingPreview.asStateFlow()

    private val _lastVoiceReply = MutableStateFlow("Hello! I'm YESMIN. Tap the mic or ask me anything in English, বাংলা, or हिन्दी.")
    val lastVoiceReply: StateFlow<String> = _lastVoiceReply.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _connectionStatus = MutableStateFlow<GeminiConnectionStatus>(
        GeminiConnectionStatus.Connected(3, "gemini-3.8-flash")
    )
    val connectionStatus: StateFlow<GeminiConnectionStatus> = _connectionStatus.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    private val _deviceCallLog = MutableStateFlow<List<DeviceCallLogEntry>>(emptyList())
    val deviceCallLog: StateFlow<List<DeviceCallLogEntry>> = _deviceCallLog.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventSummary>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventSummary>> = _calendarEvents.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionHelper.getSnapshot(appContext))
    val permissions: StateFlow<PermissionStatusSnapshot> = _permissions.asStateFlow()

    private val _battery = MutableStateFlow(container.phoneActionExecutor.getBatteryTelemetry())
    val battery: StateFlow<BatteryTelemetry> = _battery.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _bannerFeedback = MutableStateFlow<String?>(null)
    val bannerFeedback: StateFlow<String?> = _bannerFeedback.asStateFlow()

    private val _pendingLaunchAction = MutableStateFlow<String?>(null)
    val pendingLaunchAction: StateFlow<String?> = _pendingLaunchAction.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            WakeWordBus.detectedCommands.collect { command ->
                _lastTranscript.value = command
                submitPrompt(command, speakResponse = true)
            }
        }
    }

    fun refreshSystemState() {
        _permissions.value = PermissionHelper.getSnapshot(appContext)
        _battery.value = container.phoneActionExecutor.getBatteryTelemetry()
        container.ttsManager.refreshAvailableVoices()
    }

    fun refreshContacts(query: String = "") {
        viewModelScope.launch {
            _contacts.value = container.contactsRepository.getContacts(query)
        }
    }

    fun refreshDeviceCallLog() {
        viewModelScope.launch {
            _deviceCallLog.value = container.contactsRepository.getDeviceCallLog()
        }
    }

    fun refreshCalendarEvents() {
        viewModelScope.launch {
            _calendarEvents.value = container.reminderRepository.getUpcomingCalendarEvents()
        }
    }

    fun dismissBanner() {
        _bannerFeedback.value = null
    }

    fun queueLaunchAction(action: String) {
        _pendingLaunchAction.value = action
    }

    fun consumeLaunchAction() {
        _pendingLaunchAction.value = null
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        pending.onConfirm()
    }

    fun startNewConversation() {
        _activeConversationId.value = null
    }

    fun openConversation(conversationId: String) {
        _activeConversationId.value = conversationId
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            cloudSyncRepository.deleteConversation(conversationId)
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Failed to delete conversation."
                }
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            cloudSyncRepository.clearAllConversations()
                .onSuccess {
                    _activeConversationId.value = null
                    _bannerFeedback.value = "All cloud conversation history cleared."
                }
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Failed to clear cloud history."
                }
        }
    }

    fun submitPrompt(prompt: String, speakResponse: Boolean = false) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingPreview.value = ""
            _lastTranscript.value = trimmed
            val currentSettings = settings.value

            try {
                val convId = cloudSyncRepository.ensureConversation(
                    existingConversationId = _activeConversationId.value,
                    title = trimmed.take(42).ifBlank { "YESMIN Session" },
                    languageCode = currentSettings.language.code
                ).getOrNull()

                if (convId != null) {
                    _activeConversationId.value = convId
                    cloudSyncRepository.addMessage(
                        conversationId = convId,
                        role = "user",
                        content = trimmed,
                        updateTitleIfDefault = true
                    ).onFailure { err ->
                        _bannerFeedback.value = err.localizedMessage ?: "Cloud message save failed."
                    }
                } else {
                    _bannerFeedback.value = "Cloud sync unavailable; running assistant in session mode."
                }

                when (val intentResult = container.intentRouter.tryHandleCommand(trimmed, currentSettings)) {
                    is IntentOutcome.Handled -> {
                        if (convId != null) {
                            cloudSyncRepository.addMessage(
                                conversationId = convId,
                                role = "model",
                                content = intentResult.responseText,
                                modelUsed = "Device Intent Router",
                                verifiedDeviceData = intentResult.isVerifiedDeviceData,
                                actionSummary = intentResult.actionSummary
                            )
                        }
                        _lastVoiceReply.value = intentResult.responseText
                        if (intentResult.pendingConfirmation != null) {
                            _pendingConfirmation.value = intentResult.pendingConfirmation
                        }
                        if (speakResponse || currentSettings.autoSpeakReplies) {
                            container.ttsManager.speak(intentResult.responseText, currentSettings)
                        }
                    }

                    IntentOutcome.DelegateToGemini -> {
                        val apiKey = container.settingsRepository.getApiKey()
                        val history = if (currentSettings.memoryEnabled) {
                            val currentList = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
                            currentList
                                .filter { it.role == "user" || it.role == "model" }
                                .takeLast(12)
                                .dropLastWhile { it.role == "user" && it.content == trimmed }
                                .map { cloudMsg ->
                                    MessageEntity(
                                        conversationId = 0L,
                                        role = cloudMsg.role,
                                        content = cloudMsg.content,
                                        modelUsed = cloudMsg.modelUsed,
                                        isVerifiedDeviceData = cloudMsg.verifiedDeviceData,
                                        actionSummary = cloudMsg.actionSummary
                                    )
                                }
                        } else {
                            emptyList()
                        }

                        var finalReply = ""
                        var errorReply: String? = null

                        val streamFlow = if (apiKey.isNotBlank()) {
                            container.geminiApiClient.streamGenerateContent(
                                apiKey = apiKey,
                                settings = currentSettings,
                                history = history,
                                userPrompt = trimmed
                            )
                        } else {
                            container.firebaseAiService.streamChat(
                                settings = currentSettings,
                                history = history,
                                userPrompt = trimmed
                            )
                        }

                        withTimeout(65_000L) {
                            streamFlow.collect { event ->
                                when (event) {
                                    is GeminiStreamEvent.Delta -> {
                                        _streamingPreview.value += event.textChunk
                                    }
                                    is GeminiStreamEvent.Completed -> {
                                        finalReply = event.fullText
                                    }
                                    is GeminiStreamEvent.Failure -> {
                                        errorReply = event.message
                                    }
                                }
                            }
                        }

                        val replyText = finalReply.ifBlank {
                            errorReply ?: "No response received from Gemini."
                        }
                        val isError = finalReply.isBlank()
                        if (convId != null) {
                            cloudSyncRepository.addMessage(
                                conversationId = convId,
                                role = "model",
                                content = replyText,
                                modelUsed = if (isError) "Error" else currentSettings.selectedModel,
                                verifiedDeviceData = false
                            )
                        }
                        _lastVoiceReply.value = replyText
                        if (!isError && (speakResponse || currentSettings.autoSpeakReplies)) {
                            container.ttsManager.speak(replyText, currentSettings)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Assistant error: ${e.localizedMessage ?: "Request timed out or failed."}"
                _lastVoiceReply.value = msg
                _bannerFeedback.value = msg
            } finally {
                _streamingPreview.value = ""
                _isGenerating.value = false
            }
        }
    }

    fun startVoiceListening() {
        container.ttsManager.stop()
        if (WakeWordBus.serviceRunning.value) {
            YesminVoiceService.stop(appContext)
        }
        container.speechManager.startListening(settings.value.language) { recognized ->
            handleVoiceResult(recognized)
        }
    }

    fun handleVoiceResult(recognized: String) {
        val cleaned = recognized.trim()
        if (cleaned.isNotEmpty()) {
            _lastTranscript.value = cleaned
            submitPrompt(cleaned, speakResponse = true)
        }
    }

    fun createSystemSpeechIntent(): Intent =
        container.speechManager.createRecognizerIntent(settings.value.language)

    fun stopVoiceListening() {
        container.speechManager.stopListening()
    }

    fun stopSpeaking() {
        container.ttsManager.stop()
    }

    fun speakText(text: String) {
        container.ttsManager.speak(text, settings.value)
    }

    fun getMaskedApiKey(): String = container.settingsRepository.getMaskedApiKey()

    fun saveGeminiApiKey(key: String) {
        container.settingsRepository.saveApiKey(key)
        _connectionStatus.value = if (key.isNotBlank()) {
            GeminiConnectionStatus.ReadyUnverified
        } else {
            GeminiConnectionStatus.Connected(3, settings.value.selectedModel)
        }
        _bannerFeedback.value = if (key.isNotBlank()) {
            "Custom Gemini API key encrypted and saved."
        } else {
            "Switched back to built-in Gemini AI (Firebase AI Logic)."
        }
    }

    fun testGeminiConnection() {
        viewModelScope.launch {
            _connectionStatus.value = GeminiConnectionStatus.Testing
            val key = container.settingsRepository.getApiKey()
            _connectionStatus.value = if (key.isNotBlank()) {
                container.geminiApiClient.testConnection(
                    apiKey = key,
                    selectedModel = settings.value.selectedModel
                )
            } else {
                container.firebaseAiService.testBuiltInConnection(
                    selectedModel = settings.value.selectedModel
                )
            }
        }
    }

    fun updateSettings(transform: (AssistantSettings) -> AssistantSettings) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings(transform)
        }
    }

    fun setLanguage(language: SupportedLanguage) {
        updateSettings { it.copy(language = language) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        updateSettings { it.copy(keepScreenAwake = enabled) }
        _bannerFeedback.value = if (enabled) {
            "Screen will stay awake while YESMIN AI is open."
        } else {
            "Screen auto-sleep restored."
        }
    }

    fun pinHomeWidget() {
        val requested = YesminHomeWidgetProvider.requestPinToHomeScreen(appContext)
        _bannerFeedback.value = if (requested) {
            "Confirm on your Home Screen to pin YESMIN AI Widget!"
        } else {
            "Long-press an empty spot on your Home Screen -> Widgets -> drag YESMIN AI."
        }
    }

    fun toggleWakeWordService(enable: Boolean) {
        updateSettings { it.copy(wakeWordEnabled = enable) }
        if (enable) {
            YesminVoiceService.start(appContext)
        } else {
            YesminVoiceService.stop(appContext)
        }
        YesminHomeWidgetProvider.updateAllWidgets(appContext)
    }

    fun requestCallWithSafetyCheck(nameOrLabel: String, number: String) {
        val current = settings.value
        if (current.confirmBeforeCallOrSms) {
            _pendingConfirmation.value = PendingConfirmation(
                title = "Confirm Outgoing Call",
                description = "Call $nameOrLabel ($number)?",
                actionLabel = "Place Call",
                onConfirm = {
                    val res = container.phoneActionExecutor.placePhoneCall(number)
                    _bannerFeedback.value = res.message
                }
            )
        } else {
            val res = container.phoneActionExecutor.placePhoneCall(number)
            _bannerFeedback.value = res.message
        }
    }

    fun requestSmsWithSafetyCheck(nameOrLabel: String, number: String, body: String = "") {
        _pendingConfirmation.value = PendingConfirmation(
            title = "Open SMS Composer",
            description = "Compose message to $nameOrLabel ($number)? YESMIN opens your SMS app so you stay in control.",
            actionLabel = "Open Composer",
            onConfirm = {
                val res = container.phoneActionExecutor.openSmsComposer(number, body)
                _bannerFeedback.value = res.message
            }
        )
    }

    fun executeQuickPhoneAction(action: () -> String) {
        _bannerFeedback.value = action()
    }

    fun answerCall() {
        _bannerFeedback.value = CallStateHolder.answerIncomingCall().second
    }

    fun rejectCall() {
        _bannerFeedback.value = CallStateHolder.rejectIncomingCall().second
    }

    fun endCall() {
        _bannerFeedback.value = CallStateHolder.endActiveCall().second
    }

    fun toggleMute() {
        _bannerFeedback.value = CallStateHolder.toggleMute().second
    }

    fun toggleSpeakerphone() {
        _bannerFeedback.value = CallStateHolder.toggleSpeakerphone().second
    }

    fun createReminder(title: String, notes: String, triggerAtMillis: Long, alsoAddToCalendar: Boolean) {
        viewModelScope.launch {
            val cloudResult = cloudSyncRepository.createReminder(title, notes, triggerAtMillis)
            cloudResult.onSuccess { remId ->
                container.reminderRepository.scheduleCloudAlarm(remId, title, notes, triggerAtMillis)
                val calNote = if (alsoAddToCalendar) {
                    val ok = container.reminderRepository.insertCalendarEvent(title, notes, triggerAtMillis)
                    if (ok) " and added to Calendar" else " (Calendar permission required for Calendar sync)"
                } else ""
                _bannerFeedback.value = "Reminder \"$title\" synced & scheduled$calNote."
                refreshCalendarEvents()
            }.onFailure { err ->
                _bannerFeedback.value = err.localizedMessage ?: "Failed to sync reminder."
            }
        }
    }

    fun toggleReminderDone(id: String, completed: Boolean) {
        viewModelScope.launch {
            cloudSyncRepository.updateReminderCompleted(id, completed)
                .onSuccess {
                    if (completed) {
                        container.reminderRepository.cancelCloudAlarm(id)
                    }
                }
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Failed to update reminder."
                }
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            container.reminderRepository.cancelCloudAlarm(id)
            cloudSyncRepository.deleteReminder(id)
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Failed to delete reminder."
                }
        }
    }

    fun wipeAllLocalData() {
        viewModelScope.launch {
            YesminVoiceService.stop(appContext)
            container.conversationRepository.clearAllConversations()
            container.reminderRepository.clearAllReminders()
            container.contactsRepository.clearAssistantCallLogs()
            container.settingsRepository.resetAllPreferences()
            cloudSyncRepository.clearAllConversations()
            cloudSyncRepository.clearAllReminders()
            YesminNotificationListener.clearCaptured()
            _activeConversationId.value = null
            _connectionStatus.value = GeminiConnectionStatus.Connected(3, "gemini-3.8-flash")
            _bannerFeedback.value = "All local and cloud conversations, reminders, logs, and preferences cleared."
        }
    }

    private companion object {
        const val TAG = "YesminViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

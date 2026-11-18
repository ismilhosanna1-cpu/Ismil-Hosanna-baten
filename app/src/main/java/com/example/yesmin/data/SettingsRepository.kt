package com.example.yesmin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.yesmin.security.SecureVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.yesminDataStore: DataStore<Preferences> by preferencesDataStore(name = "yesmin_preferences")

class SettingsRepository(
    context: Context,
    private val secureVault: SecureVault
) {
    private val dataStore = context.applicationContext.yesminDataStore

    private val _apiKeyState = MutableStateFlow(secureVault.hasApiKey())
    val hasApiKeyFlow: StateFlow<Boolean> = _apiKeyState.asStateFlow()

    val settingsFlow: Flow<AssistantSettings> = dataStore.data.map { prefs ->
        AssistantSettings(
            selectedModel = prefs[KEY_MODEL] ?: "gemini-3.8-flash",
            language = SupportedLanguage.fromCode(prefs[KEY_LANGUAGE] ?: "en"),
            responseLength = ResponseLength.fromId(prefs[KEY_RESPONSE_LENGTH] ?: "balanced"),
            personality = PersonalityTone.fromId(prefs[KEY_PERSONALITY] ?: "caring_playful"),
            memoryEnabled = prefs[KEY_MEMORY_ENABLED] ?: true,
            streamingEnabled = prefs[KEY_STREAMING_ENABLED] ?: true,
            autoSpeakReplies = prefs[KEY_AUTO_SPEAK] ?: true,
            preferredVoiceName = prefs[KEY_VOICE_NAME] ?: "",
            ttsPitch = prefs[KEY_TTS_PITCH] ?: 1.08f,
            ttsSpeed = prefs[KEY_TTS_SPEED] ?: 1.0f,
            wakeWordEnabled = prefs[KEY_WAKE_WORD_ENABLED] ?: false,
            keepScreenAwake = prefs[KEY_KEEP_SCREEN_AWAKE] ?: false,
            primaryWakePhrase = prefs[KEY_WAKE_PRIMARY] ?: "Hey Yesmin",
            secondaryWakePhrase = prefs[KEY_WAKE_SECONDARY] ?: "Yesmin",
            announceIncomingCalls = prefs[KEY_ANNOUNCE_CALLS] ?: true,
            autoAnswerEnabled = prefs[KEY_AUTO_ANSWER] ?: false,
            autoAnswerDelaySeconds = prefs[KEY_AUTO_ANSWER_DELAY] ?: 4,
            confirmBeforeCallOrSms = prefs[KEY_CONFIRM_SENSITIVE] ?: true,
            customSystemHint = prefs[KEY_CUSTOM_HINT] ?: "",
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f
        )
    }

    fun getApiKey(): String = secureVault.getGeminiApiKey()

    fun getMaskedApiKey(): String = secureVault.getMaskedApiKey()

    fun saveApiKey(apiKey: String) {
        secureVault.saveGeminiApiKey(apiKey)
        _apiKeyState.value = secureVault.hasApiKey()
    }

    fun clearApiKey() {
        secureVault.clearGeminiApiKey()
        _apiKeyState.value = false
    }

    suspend fun updateSettings(transform: (AssistantSettings) -> AssistantSettings) {
        dataStore.edit { prefs ->
            val current = AssistantSettings(
                selectedModel = prefs[KEY_MODEL] ?: "gemini-3.8-flash",
                language = SupportedLanguage.fromCode(prefs[KEY_LANGUAGE] ?: "en"),
                responseLength = ResponseLength.fromId(prefs[KEY_RESPONSE_LENGTH] ?: "balanced"),
                personality = PersonalityTone.fromId(prefs[KEY_PERSONALITY] ?: "caring_playful"),
                memoryEnabled = prefs[KEY_MEMORY_ENABLED] ?: true,
                streamingEnabled = prefs[KEY_STREAMING_ENABLED] ?: true,
                autoSpeakReplies = prefs[KEY_AUTO_SPEAK] ?: true,
                preferredVoiceName = prefs[KEY_VOICE_NAME] ?: "",
                ttsPitch = prefs[KEY_TTS_PITCH] ?: 1.08f,
                ttsSpeed = prefs[KEY_TTS_SPEED] ?: 1.0f,
                wakeWordEnabled = prefs[KEY_WAKE_WORD_ENABLED] ?: false,
                keepScreenAwake = prefs[KEY_KEEP_SCREEN_AWAKE] ?: false,
                primaryWakePhrase = prefs[KEY_WAKE_PRIMARY] ?: "Hey Yesmin",
                secondaryWakePhrase = prefs[KEY_WAKE_SECONDARY] ?: "Yesmin",
                announceIncomingCalls = prefs[KEY_ANNOUNCE_CALLS] ?: true,
                autoAnswerEnabled = prefs[KEY_AUTO_ANSWER] ?: false,
                autoAnswerDelaySeconds = prefs[KEY_AUTO_ANSWER_DELAY] ?: 4,
                confirmBeforeCallOrSms = prefs[KEY_CONFIRM_SENSITIVE] ?: true,
                customSystemHint = prefs[KEY_CUSTOM_HINT] ?: "",
                temperature = prefs[KEY_TEMPERATURE] ?: 0.7f
            )
            val updated = transform(current)
            prefs[KEY_MODEL] = updated.selectedModel
            prefs[KEY_LANGUAGE] = updated.language.code
            prefs[KEY_RESPONSE_LENGTH] = updated.responseLength.id
            prefs[KEY_PERSONALITY] = updated.personality.id
            prefs[KEY_MEMORY_ENABLED] = updated.memoryEnabled
            prefs[KEY_STREAMING_ENABLED] = updated.streamingEnabled
            prefs[KEY_AUTO_SPEAK] = updated.autoSpeakReplies
            prefs[KEY_VOICE_NAME] = updated.preferredVoiceName
            prefs[KEY_TTS_PITCH] = updated.ttsPitch
            prefs[KEY_TTS_SPEED] = updated.ttsSpeed
            prefs[KEY_WAKE_WORD_ENABLED] = updated.wakeWordEnabled
            prefs[KEY_KEEP_SCREEN_AWAKE] = updated.keepScreenAwake
            prefs[KEY_WAKE_PRIMARY] = updated.primaryWakePhrase
            prefs[KEY_WAKE_SECONDARY] = updated.secondaryWakePhrase
            prefs[KEY_ANNOUNCE_CALLS] = updated.announceIncomingCalls
            prefs[KEY_AUTO_ANSWER] = updated.autoAnswerEnabled
            prefs[KEY_AUTO_ANSWER_DELAY] = updated.autoAnswerDelaySeconds
            prefs[KEY_CONFIRM_SENSITIVE] = updated.confirmBeforeCallOrSms
            prefs[KEY_CUSTOM_HINT] = updated.customSystemHint
            prefs[KEY_TEMPERATURE] = updated.temperature
        }
    }

    suspend fun resetAllPreferences() {
        dataStore.edit { it.clear() }
        clearApiKey()
    }

    companion object {
        private val KEY_MODEL = stringPreferencesKey("selected_model")
        private val KEY_LANGUAGE = stringPreferencesKey("language_code")
        private val KEY_RESPONSE_LENGTH = stringPreferencesKey("response_length")
        private val KEY_PERSONALITY = stringPreferencesKey("personality_tone")
        private val KEY_MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        private val KEY_STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        private val KEY_AUTO_SPEAK = booleanPreferencesKey("auto_speak_replies")
        private val KEY_VOICE_NAME = stringPreferencesKey("preferred_voice_name")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_WAKE_PRIMARY = stringPreferencesKey("wake_primary_phrase")
        private val KEY_WAKE_SECONDARY = stringPreferencesKey("wake_secondary_phrase")
        private val KEY_ANNOUNCE_CALLS = booleanPreferencesKey("announce_incoming_calls")
        private val KEY_AUTO_ANSWER = booleanPreferencesKey("auto_answer_enabled")
        private val KEY_AUTO_ANSWER_DELAY = intPreferencesKey("auto_answer_delay_sec")
        private val KEY_CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_before_call_or_sms")
        private val KEY_CUSTOM_HINT = stringPreferencesKey("custom_system_hint")
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
    }
}

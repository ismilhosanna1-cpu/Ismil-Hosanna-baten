package com.example.yesmin.data

enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val bcp47Tag: String
) {
    ENGLISH("en", "English", "English", "en-US"),
    BENGALI("bn", "Bengali", "বাংলা", "bn-BD"),
    HINDI("hi", "Hindi", "हिन्दी", "hi-IN");

    companion object {
        fun fromCode(code: String): SupportedLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class ResponseLength(val id: String, val label: String, val instruction: String) {
    CONCISE("concise", "Concise", "Keep replies brief, crisp, and direct (1-3 sentences unless code is requested)."),
    BALANCED("balanced", "Balanced", "Give clear, helpful, well-structured answers with natural warmth."),
    DETAILED("detailed", "Detailed", "Provide thorough, step-by-step, comprehensive explanations.");

    companion object {
        fun fromId(id: String): ResponseLength =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

enum class PersonalityTone(val id: String, val label: String, val instruction: String) {
    CARING_PLAYFUL("caring_playful", "Caring & Playful (Default)", "Be warm, attentive, intelligent, subtly playful, and supportive like a trusted companion."),
    PROFESSIONAL("professional", "Focused & Professional", "Be sharp, efficient, respectful, and executive-assistant crisp."),
    FRIENDLY_COMPANION("friendly", "Warm Companion", "Be empathetic, conversational, encouraging, and gentle.");

    companion object {
        fun fromId(id: String): PersonalityTone =
            entries.firstOrNull { it.id == id } ?: CARING_PLAYFUL
    }
}

data class GeminiModelOption(
    val id: String,
    val title: String,
    val subtitle: String
)

object SupportedGeminiModels {
    val presets = listOf(
        GeminiModelOption(
            id = "gemini-3.8-flash",
            title = "Gemini 3.8 Flash",
            subtitle = "Default — Fast, smart & multilingual (EN / BN / HI)"
        ),
        GeminiModelOption(
            id = "gemini-3.5-flash-lite",
            title = "Gemini 3.5 Flash-Lite",
            subtitle = "Ultra-low latency for quick voice commands"
        ),
        GeminiModelOption(
            id = "gemini-4-argon",
            title = "Gemini 4 Argon",
            subtitle = "Deep reasoning, coding & complex analysis"
        )
    )
}

data class AssistantSettings(
    val selectedModel: String = "gemini-3.8-flash",
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val responseLength: ResponseLength = ResponseLength.BALANCED,
    val personality: PersonalityTone = PersonalityTone.CARING_PLAYFUL,
    val memoryEnabled: Boolean = true,
    val streamingEnabled: Boolean = true,
    val autoSpeakReplies: Boolean = true,
    val preferredVoiceName: String = "",
    val ttsPitch: Float = 1.08f,
    val ttsSpeed: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val primaryWakePhrase: String = "Hey Yesmin",
    val secondaryWakePhrase: String = "Yesmin",
    val announceIncomingCalls: Boolean = true,
    val autoAnswerEnabled: Boolean = false,
    val autoAnswerDelaySeconds: Int = 4,
    val confirmBeforeCallOrSms: Boolean = true,
    val customSystemHint: String = "",
    val temperature: Float = 0.7f
)

sealed interface GeminiConnectionStatus {
    data object NotConfigured : GeminiConnectionStatus
    data object ReadyUnverified : GeminiConnectionStatus
    data object Testing : GeminiConnectionStatus
    data class Connected(val modelCount: Int, val activeModel: String) : GeminiConnectionStatus
    data class Error(val message: String, val code: Int? = null) : GeminiConnectionStatus
}

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val label: String = "Mobile"
)

data class DeviceCallLogEntry(
    val id: String,
    val name: String?,
    val number: String,
    val type: Int,
    val timestamp: Long,
    val durationSeconds: Long
)

data class NotificationSummary(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

data class CalendarEventSummary(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?
)

data class PendingConfirmation(
    val title: String,
    val description: String,
    val actionLabel: String,
    val onConfirm: () -> Unit
)

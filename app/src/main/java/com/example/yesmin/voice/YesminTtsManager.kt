package com.example.yesmin.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsVoiceItem(
    val name: String,
    val localeTag: String,
    val isNetwork: Boolean
)

class YesminTtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceItem>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceItem>> = _availableVoices.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                refreshAvailableVoices()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    fun refreshAvailableVoices() {
        val voices = tts?.voices.orEmpty()
            .filter { voice ->
                val lang = voice.locale.language
                lang == "en" || lang == "bn" || lang == "hi"
            }
            .sortedBy { it.locale.toLanguageTag() }
            .map {
                TtsVoiceItem(
                    name = it.name,
                    localeTag = it.locale.toLanguageTag(),
                    isNetwork = it.isNetworkConnectionRequired
                )
            }
        _availableVoices.value = voices
    }

    fun speak(text: String, settings: AssistantSettings) {
        val engine = tts ?: return
        if (!isReady || text.isBlank()) return

        val cleanedText = text
            .replace(Regex("\\*\\*|__|`|#"), "")
            .replace(Regex("\\[Verified [^\\]]+\\]"), "")
            .trim()
            .take(1500)
        if (cleanedText.isBlank()) return

        val targetLocale = detectPreferredLocale(cleanedText, settings.language)
        engine.language = targetLocale
        engine.setPitch(settings.ttsPitch.coerceIn(0.6f, 1.6f))
        engine.setSpeechRate(settings.ttsSpeed.coerceIn(0.6f, 1.8f))

        val selectedVoice = chooseBestFemaleOrPreferredVoice(
            voices = engine.voices.orEmpty(),
            targetLocale = targetLocale,
            preferredVoiceName = settings.preferredVoiceName
        )
        if (selectedVoice != null) {
            engine.voice = selectedVoice
        }

        engine.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun detectPreferredLocale(text: String, fallback: SupportedLanguage): Locale {
        val hasBengali = text.any { it in '\u0980'..'\u09FF' }
        if (hasBengali) return Locale("bn", "BD")
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return Locale("hi", "IN")
        return Locale.forLanguageTag(fallback.bcp47Tag)
    }

    private fun chooseBestFemaleOrPreferredVoice(
        voices: Set<Voice>,
        targetLocale: Locale,
        preferredVoiceName: String
    ): Voice? {
        if (preferredVoiceName.isNotBlank()) {
            voices.firstOrNull { it.name == preferredVoiceName }?.let { return it }
        }
        val localeMatches = voices.filter { it.locale.language == targetLocale.language }
        if (localeMatches.isEmpty()) return null

        val femaleHints = listOf("female", "sfg", "sfb", "bn-in-x-bnf", "bn-bd-x-ban", "hi-in-x-hid", "en-us-x-sfg", "en-gb-x-gba")
        return localeMatches.firstOrNull { v ->
            val nameLower = v.name.lowercase()
            femaleHints.any { hint -> nameLower.contains(hint) }
        } ?: localeMatches.firstOrNull()
    }
}

package com.example.yesmin.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.security.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceListeningState {
    data object Idle : VoiceListeningState
    data object Listening : VoiceListeningState
    data class Partial(val text: String) : VoiceListeningState
    data class Error(val message: String, val suggestSystemDialog: Boolean = false) : VoiceListeningState
}

class YesminSpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastPartialText: String = ""
    private var pendingStartRunnable: Runnable? = null

    private val _listeningState = MutableStateFlow<VoiceListeningState>(VoiceListeningState.Idle)
    val listeningState: StateFlow<VoiceListeningState> = _listeningState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun createRecognizerIntent(language: SupportedLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.bcp47Tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.bcp47Tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "YESMIN is listening (${language.nativeLabel})...")
        }
    }

    fun startListening(
        language: SupportedLanguage,
        onFinalResult: (String) -> Unit
    ) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            _listeningState.value = VoiceListeningState.Error(
                "Microphone permission is required. Please allow Microphone access."
            )
            return
        }
        if (!isRecognitionAvailable()) {
            _listeningState.value = VoiceListeningState.Error(
                "Inline speech service unavailable on this ROM. Tap 'System Mic' to speak.",
                suggestSystemDialog = true
            )
            return
        }

        mainHandler.post {
            cancelPendingStart()
            internalDestroyRecognizer()
            lastPartialText = ""

            // Small delay after destroy avoids ERROR_RECOGNIZER_BUSY on MIUI / Android 11
            val startRunnable = Runnable {
                pendingStartRunnable = null
                try {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                    speechRecognizer = recognizer

                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _listeningState.value = VoiceListeningState.Listening
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                            _rmsLevel.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _rmsLevel.value = 0f
                        }

                        override fun onError(error: Int) {
                            _rmsLevel.value = 0f
                            Log.w(TAG, "SpeechRecognizer onError code=$error, lastPartial=$lastPartialText")
                            // If we already captured partial speech on NO_MATCH/TIMEOUT, use it!
                            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                                lastPartialText.isNotBlank()
                            ) {
                                val recovered = lastPartialText
                                lastPartialText = ""
                                _listeningState.value = VoiceListeningState.Idle
                                onFinalResult(recovered)
                                return
                            }

                            val (msg, suggestFallback) = when (error) {
                                SpeechRecognizer.ERROR_AUDIO ->
                                    "Microphone audio error. Close other mic apps or try System Mic." to true
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    "Microphone permission denied in Android settings." to false
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                    "Network error during speech recognition. Check internet." to false
                                SpeechRecognizer.ERROR_NO_MATCH ->
                                    "Didn't catch your voice. Speak clearly near mic or tap System Mic." to true
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                    "Speech service was busy. Please tap Mic again or use System Mic." to true
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    "No speech heard. Tap Mic and speak right after the beep." to true
                                SpeechRecognizer.ERROR_CLIENT ->
                                    "Device speech client blocked. Tap 'System Mic' to speak." to true
                                else ->
                                    "Speech recognition error ($error). Try 'System Mic'." to true
                            }
                            _listeningState.value = VoiceListeningState.Error(msg, suggestFallback)
                        }

                        override fun onResults(results: Bundle?) {
                            _rmsLevel.value = 0f
                            _listeningState.value = VoiceListeningState.Idle
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val top = matches?.firstOrNull()?.trim().orEmpty().ifBlank { lastPartialText }
                            lastPartialText = ""
                            if (top.isNotBlank()) {
                                onFinalResult(top)
                            } else {
                                _listeningState.value = VoiceListeningState.Error(
                                    "No words recognized. Try speaking again or use System Mic.",
                                    suggestSystemDialog = true
                                )
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                lastPartialText = partial
                                _listeningState.value = VoiceListeningState.Partial(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    _listeningState.value = VoiceListeningState.Listening
                    recognizer.startListening(createRecognizerIntent(language))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start SpeechRecognizer", e)
                    _listeningState.value = VoiceListeningState.Error(
                        "Unable to start mic recognizer (${e.localizedMessage ?: "unknown"}). Use System Mic.",
                        suggestSystemDialog = true
                    )
                }
            }
            pendingStartRunnable = startRunnable
            mainHandler.postDelayed(startRunnable, 120L)
        }
    }

    fun stopListening() {
        mainHandler.post {
            cancelPendingStart()
            _rmsLevel.value = 0f
            internalDestroyRecognizer()
            if (_listeningState.value is VoiceListeningState.Listening ||
                _listeningState.value is VoiceListeningState.Partial
            ) {
                _listeningState.value = VoiceListeningState.Idle
            }
        }
    }

    private fun cancelPendingStart() {
        pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingStartRunnable = null
    }

    private fun internalDestroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    companion object {
        private const val TAG = "YesminSpeechManager"
    }
}

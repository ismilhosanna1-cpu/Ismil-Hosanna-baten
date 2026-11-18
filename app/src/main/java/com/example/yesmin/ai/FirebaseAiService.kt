package com.example.yesmin.ai

import android.util.Log
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.GeminiConnectionStatus
import com.example.yesmin.database.MessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class FirebaseAiService {

    private fun buildModel(settings: AssistantSettings): GenerativeModel {
        val systemText = buildString {
            appendLine("You are YESMIN, an intelligent, caring, warm, and slightly playful female AI phone companion.")
            appendLine("Language rule: Understand Bengali, Hindi, and English seamlessly. Always respond in the language the user wrote in; if ambiguous, prefer ${settings.language.displayName}.")
            appendLine("Personality: ${settings.personality.instruction}")
            appendLine("Response style: ${settings.responseLength.instruction}")
            appendLine("Honesty rule: Never claim you performed a real device action (call answered, SMS sent, flashlight toggled, alarm set) unless the Android system result confirms it. Clearly distinguish AI-generated knowledge from verified device telemetry.")
            if (settings.customSystemHint.isNotBlank()) {
                appendLine("User instruction: ${settings.customSystemHint.trim()}")
            }
        }

        val maxTokens = when (settings.responseLength.id) {
            "concise" -> 350
            "detailed" -> 2048
            else -> 1024
        }

        val config = generationConfig {
            temperature = settings.temperature.coerceIn(0f, 1.5f)
            maxOutputTokens = maxTokens
        }

        val modelName = settings.selectedModel.trim().ifBlank { "gemini-3.8-flash" }
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = config,
            systemInstruction = content { text(systemText) }
        )
    }

    suspend fun testBuiltInConnection(selectedModel: String): GeminiConnectionStatus {
        return try {
            val modelName = selectedModel.trim().ifBlank { "gemini-3.8-flash" }
            val model by lazy {
                Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName)
            }
            val response = withTimeout(30_000L) {
                model.generateContent("Reply with OK.")
            }
            if (!response.text.isNullOrBlank()) {
                GeminiConnectionStatus.Connected(modelCount = 3, activeModel = modelName)
            } else {
                GeminiConnectionStatus.Error("Gemini returned an empty test response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI connection test timed out", e)
            GeminiConnectionStatus.Error("Gemini connection test timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI connection test failed", e)
            GeminiConnectionStatus.Error(e.localizedMessage ?: "Firebase AI Logic connection failed.")
        }
    }

    fun streamChat(
        settings: AssistantSettings,
        history: List<MessageEntity>,
        userPrompt: String
    ): Flow<GeminiStreamEvent> = flow {
        try {
            val model by lazy { buildModel(settings) }
            val chatHistory = if (settings.memoryEnabled) {
                history.filter { it.content.isNotBlank() }.map { msg ->
                    val role = if (msg.role == "user") "user" else "model"
                    content(role = role) { text(msg.content) }
                }
            } else {
                emptyList()
            }

            val chat = model.startChat(history = chatHistory)
            val accumulated = StringBuilder()

            withTimeout(60_000L) {
                if (settings.streamingEnabled) {
                    chat.sendMessageStream(userPrompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                            emit(GeminiStreamEvent.Delta(part))
                        }
                    }
                } else {
                    val response = chat.sendMessage(userPrompt)
                    val full = response.text.orEmpty()
                    if (full.isNotEmpty()) {
                        accumulated.append(full)
                    }
                }
            }

            if (accumulated.isNotBlank()) {
                emit(GeminiStreamEvent.Completed(accumulated.toString()))
            } else {
                emit(GeminiStreamEvent.Failure("Gemini returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out", e)
            emit(GeminiStreamEvent.Failure("Generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            emit(GeminiStreamEvent.Failure(e.localizedMessage ?: "Gemini generation failed."))
        }
    }

    companion object {
        private const val TAG = "FirebaseAiService"
    }
}

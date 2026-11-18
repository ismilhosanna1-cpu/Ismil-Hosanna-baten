package com.example.yesmin.ai

import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.GeminiConnectionStatus
import com.example.yesmin.database.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed interface GeminiStreamEvent {
    data class Delta(val textChunk: String) : GeminiStreamEvent
    data class Completed(val fullText: String) : GeminiStreamEvent
    data class Failure(val message: String, val httpCode: Int? = null) : GeminiStreamEvent
}

class GeminiApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(apiKey: String, selectedModel: String): GeminiConnectionStatus =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext GeminiConnectionStatus.NotConfigured
            }
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("x-goog-api-key", apiKey.trim())
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val parsedErr = extractApiError(body, response.code)
                        return@withContext GeminiConnectionStatus.Error(parsedErr, response.code)
                    }
                    val root = JSONObject(body)
                    val models = root.optJSONArray("models") ?: JSONArray()
                    GeminiConnectionStatus.Connected(
                        modelCount = models.length(),
                        activeModel = selectedModel
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GeminiConnectionStatus.Error(
                    message = e.localizedMessage ?: "Network error while contacting Gemini API."
                )
            }
        }

    fun streamGenerateContent(
        apiKey: String,
        settings: AssistantSettings,
        history: List<MessageEntity>,
        userPrompt: String
    ): Flow<GeminiStreamEvent> = callbackFlow {
        if (apiKey.isBlank()) {
            trySend(
                GeminiStreamEvent.Failure(
                    "Gemini API key is not configured. Open Settings -> Gemini API Configuration to add your key."
                )
            )
            close()
            return@callbackFlow
        }

        val payload = buildGeneratePayload(settings, history, userPrompt)
        val modelPath = normalizeModelName(settings.selectedModel)

        val request = if (settings.streamingEnabled) {
            Request.Builder()
                .url("$BASE_URL/$modelPath:streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey.trim())
                .header("Accept", "text/event-stream")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        } else {
            Request.Builder()
                .url("$BASE_URL/$modelPath:generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    trySend(
                        GeminiStreamEvent.Failure(
                            "Network failure: ${e.localizedMessage ?: "Unable to reach Gemini API."}"
                        )
                    )
                }
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val errBody = res.body?.string().orEmpty()
                        trySend(
                            GeminiStreamEvent.Failure(
                                message = extractApiError(errBody, res.code),
                                httpCode = res.code
                            )
                        )
                        close()
                        return
                    }

                    val body = res.body
                    if (body == null) {
                        trySend(GeminiStreamEvent.Failure("Empty response body from Gemini."))
                        close()
                        return
                    }

                    try {
                        if (settings.streamingEnabled) {
                            val accumulated = StringBuilder()
                            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val current = line ?: continue
                                    if (current.startsWith("data:")) {
                                        val jsonStr = current.removePrefix("data:").trim()
                                        if (jsonStr == "[DONE]" || jsonStr.isEmpty()) continue
                                        val chunkText = extractCandidateText(jsonStr)
                                        if (chunkText.isNotEmpty()) {
                                            accumulated.append(chunkText)
                                            trySend(GeminiStreamEvent.Delta(chunkText))
                                        }
                                    }
                                }
                            }
                            if (accumulated.isNotEmpty()) {
                                trySend(GeminiStreamEvent.Completed(accumulated.toString()))
                            } else {
                                trySend(GeminiStreamEvent.Failure("Gemini returned an empty stream response."))
                            }
                        } else {
                            val fullJson = body.string()
                            val text = extractCandidateText(fullJson)
                            if (text.isNotEmpty()) {
                                trySend(GeminiStreamEvent.Completed(text))
                            } else {
                                trySend(GeminiStreamEvent.Failure("Gemini returned an empty response."))
                            }
                        }
                    } catch (e: Exception) {
                        trySend(
                            GeminiStreamEvent.Failure(
                                "Failed to parse Gemini response: ${e.localizedMessage ?: "Unknown error"}"
                            )
                        )
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }

    private fun buildGeneratePayload(
        settings: AssistantSettings,
        history: List<MessageEntity>,
        userPrompt: String
    ): JSONObject {
        val systemInstructionText = buildString {
            appendLine("You are YESMIN, an intelligent, caring, warm, and slightly playful female AI phone companion.")
            appendLine("Language rule: Understand Bengali, Hindi, and English seamlessly. Always respond in the language the user wrote in; if ambiguous, prefer ${settings.language.displayName}.")
            appendLine("Personality: ${settings.personality.instruction}")
            appendLine("Response style: ${settings.responseLength.instruction}")
            appendLine("Honesty rule: Never claim you performed a real device action (call answered, SMS sent, flashlight toggled, alarm set) unless the Android system result confirms it. Clearly distinguish AI-generated knowledge from verified device telemetry.")
            if (settings.customSystemHint.isNotBlank()) {
                appendLine("User instruction: ${settings.customSystemHint.trim()}")
            }
        }

        val contents = JSONArray()
        if (settings.memoryEnabled) {
            history.forEach { msg ->
                val role = if (msg.role == "user") "user" else "model"
                if (msg.content.isNotBlank()) {
                    contents.put(
                        JSONObject().apply {
                            put("role", role)
                            put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", msg.content))
                            )
                        }
                    )
                }
            }
        }

        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }
        )

        val maxTokens = when (settings.responseLength.id) {
            "concise" -> 350
            "detailed" -> 2048
            else -> 1024
        }

        return JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstructionText))
                )
            )
            put("contents", contents)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", settings.temperature.toDouble())
                    put("maxOutputTokens", maxTokens)
                }
            )
        }
    }

    private fun normalizeModelName(model: String): String {
        val trimmed = model.trim().ifEmpty { "gemini-3.8-flash" }
        return if (trimmed.startsWith("models/")) trimmed else "models/$trimmed"
    }

    private fun extractCandidateText(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                sb.append(part.optString("text", ""))
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractApiError(body: String, code: Int): String {
        val baseMsg = try {
            val errObj = JSONObject(body).optJSONObject("error")
            errObj?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        return when (code) {
            400 -> baseMsg.ifBlank { "Invalid request or unsupported model selected (HTTP 400)." }
            401, 403 -> baseMsg.ifBlank { "Authentication failed (HTTP $code). Verify your Gemini API key." }
            404 -> baseMsg.ifBlank { "Selected Gemini model was not found (HTTP 404). Try switching models in Settings." }
            429 -> baseMsg.ifBlank { "Gemini API rate limit reached (HTTP 429). Please wait a moment and try again." }
            in 500..599 -> "Gemini service is temporarily unavailable (HTTP $code). Please retry shortly."
            else -> baseMsg.ifBlank { "Gemini API error (HTTP $code)." }
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

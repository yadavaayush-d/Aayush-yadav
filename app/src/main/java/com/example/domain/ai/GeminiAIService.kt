package com.example.domain.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Concrete implementation of AIService interacting with Google Gemini API.
 *
 * Security & Reliability:
 * - Credentials injected via BuildConfig.GEMINI_API_KEY from environment secrets
 * - Never logged or surfaced in raw form to the client
 * - Automatic prioritized model fallback (gemini-3.1-flash-lite, gemini-3.1-flash-lite-preview, gemini-3.8-flash, gemini-3.5-flash)
 *   to guard against model demand spikes / 503 errors
 * - Supports structured application/json mode for MCQs and quizzes
 * - Emits structured exceptions for precise UI error mapping
 */
class GeminiAIService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val explicitApiKey: String? = null
) : AIService {

    companion object {
        private const val TAG = "GeminiAIService"
        private const val BASE_HOST = "https://generativelanguage.googleapis.com/v1beta/models"

        // Prioritized list of compatible Gemini models with graceful fallback on 503/429
        private val CANDIDATE_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-flash",
            "gemini-3.5-flash"
        )
    }

    private fun getResolvedApiKey(): String {
        val configured = explicitApiKey ?: try {
            val bcKey = BuildConfig.GEMINI_API_KEY
            if (bcKey.isNotBlank() && bcKey != "MY_GEMINI_API_KEY") {
                bcKey
            } else {
                System.getenv("GEMINI_API_KEY") ?: ""
            }
        } catch (_: Throwable) {
            System.getenv("GEMINI_API_KEY") ?: ""
        }
        return configured.trim()
    }

    override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getResolvedApiKey()

        // Check if external provider key is configured
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val setupError = if (request.isAdmin) {
                "AI provider is not configured. Please set GEMINI_API_KEY in the AI Studio Secrets panel."
            } else {
                "AI provider is not configured. Please contact the administrator."
            }
            Log.w(TAG, "API key missing or placeholder")
            return@withContext Result.failure(AIProviderNotConfiguredException(setupError))
        }

        try {
            // Build Gemini REST JSON payload with context history and system instruction
            val rootJson = JSONObject()

            // 1. System instruction
            if (request.systemInstruction.isNotBlank()) {
                val systemParts = JSONArray().put(JSONObject().put("text", request.systemInstruction))
                rootJson.put("systemInstruction", JSONObject().put("parts", systemParts))
            }

            // 2. Contents (history + current prompt)
            val contentsArray = JSONArray()

            val filteredTurns = mutableListOf<Pair<String, String>>()
            for (msg in request.contextMessages) {
                val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
                val text = msg.content.trim()
                if (text.isNotBlank()) {
                    filteredTurns.add(role to text)
                }
            }

            while (filteredTurns.isNotEmpty() && filteredTurns.last().first == "user" && filteredTurns.last().second == request.prompt.trim()) {
                filteredTurns.removeAt(filteredTurns.lastIndex)
            }

            val alternatingTurns = mutableListOf<Pair<String, String>>()
            for (turn in filteredTurns) {
                if (alternatingTurns.isEmpty()) {
                    alternatingTurns.add(turn)
                } else if (alternatingTurns.last().first == turn.first) {
                    val prev = alternatingTurns.removeAt(alternatingTurns.lastIndex)
                    alternatingTurns.add(prev.first to "${prev.second}\n\n${turn.second}")
                } else {
                    alternatingTurns.add(turn)
                }
            }

            for (turn in alternatingTurns) {
                val partObj = JSONObject().put("text", turn.second)
                val turnObj = JSONObject()
                    .put("role", turn.first)
                    .put("parts", JSONArray().put(partObj))
                contentsArray.put(turnObj)
            }

            val lastWasUser = alternatingTurns.isNotEmpty() && alternatingTurns.last().first == "user"
            if (!lastWasUser) {
                val currentTurn = JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", request.prompt.trim())))
                contentsArray.put(currentTurn)
            }

            rootJson.put("contents", contentsArray)

            // 3. Generation configuration
            val tokenLimit = if (request.responseJson) 4096.coerceAtLeast(request.maxTokens) else request.maxTokens.coerceAtLeast(1024)
            val genConfig = JSONObject()
                .put("temperature", request.temperature.toDouble())
                .put("maxOutputTokens", tokenLimit)

            if (request.responseJson) {
                genConfig.put("responseMimeType", "application/json")
            }
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            // Try candidate models in priority order with fallback on 503 / 404 / 429
            var lastErrorSummary = "No response from provider"

            for (model in CANDIDATE_MODELS) {
                val urlWithKey = "$BASE_HOST/$model:generateContent?key=$apiKey"
                val httpRequest = Request.Builder()
                    .url(urlWithKey)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                try {
                    val response = client.newCall(httpRequest).execute()
                    val latency = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: ""

                    if (response.code == 401 || response.code == 403) {
                        Log.e(TAG, "Authentication failed with HTTP ${response.code}")
                        val authMsg = if (request.isAdmin) {
                            "AI provider authentication failure. Please check GEMINI_API_KEY in the AI Studio Secrets panel."
                        } else {
                            "AI service is temporarily unavailable. Please try again."
                        }
                        return@withContext Result.failure(AIProviderNotConfiguredException(authMsg))
                    }

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Model $model returned HTTP ${response.code}: ${responseBody.take(150)}")
                        lastErrorSummary = "Model $model returned HTTP ${response.code}"
                        // Attempt fallback model
                        continue
                    }

                    if (responseBody.isBlank()) {
                        Log.w(TAG, "Model $model returned empty body")
                        lastErrorSummary = "Model $model returned empty body"
                        continue
                    }

                    val parsedJson = JSONObject(responseBody)
                    val candidates = parsedJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        Log.w(TAG, "Model $model returned no candidates: ${responseBody.take(150)}")
                        lastErrorSummary = "Model $model returned no candidates"
                        continue
                    }

                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    val textBuilder = StringBuilder()

                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            if (p.has("text") && !p.optBoolean("thought", false)) {
                                textBuilder.append(p.getString("text"))
                            }
                        }
                        if (textBuilder.isEmpty()) {
                            for (i in 0 until parts.length()) {
                                val p = parts.getJSONObject(i)
                                if (p.has("text")) {
                                    textBuilder.append(p.getString("text"))
                                }
                            }
                        }
                    }

                    val responseText = textBuilder.toString().trim()
                    if (responseText.isEmpty()) {
                        Log.w(TAG, "Model $model candidate had empty text")
                        lastErrorSummary = "Model $model returned empty text in candidate"
                        continue
                    }

                    val usageMetadata = parsedJson.optJSONObject("usageMetadata")
                    val totalTokens = usageMetadata?.optInt("totalTokenCount", 0)
                        ?: (responseText.length / 4 + request.prompt.length / 4)

                    Log.i(TAG, "Success with model $model in ${latency}ms, tokens: $totalTokens")

                    return@withContext Result.success(
                        AIServiceResponse(
                            content = responseText,
                            tokensUsed = totalTokens,
                            modelName = model,
                            latencyMs = latency,
                            isConfigurationNotice = false
                        )
                    )
                } catch (e: SocketTimeoutException) {
                    Log.w(TAG, "Timeout with model $model: ${e.message}")
                    lastErrorSummary = "Timeout with model $model"
                } catch (e: IOException) {
                    Log.w(TAG, "IOException with model $model: ${e.message}")
                    lastErrorSummary = "IOException with model $model"
                }
            }

            Log.e(TAG, "All candidate models failed. Last status: $lastErrorSummary")
            Result.failure(
                AIProviderUnavailableException(
                    userFriendlyMessage = "AI service is temporarily unavailable. Please try again.",
                    technicalMessage = "All candidate models exhausted: $lastErrorSummary"
                )
            )
        } catch (e: AIServiceException) {
            Log.e(TAG, "AIServiceException: ${e.errorCode} - ${e.technicalMessage}")
            Result.failure(e)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "SocketTimeoutException calling AI provider: ${e.message}")
            Result.failure(AITimeoutException("The AI request took too long. Please try again.", e.message ?: "Timeout"))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "UnknownHostException calling AI provider: ${e.message}")
            Result.failure(AINetworkException("Network connection error. Please check your connection.", e.message ?: "Network error"))
        } catch (e: IOException) {
            Log.e(TAG, "IOException calling AI provider: ${e.message}")
            Result.failure(AIProviderUnavailableException("AI service is temporarily unavailable. Please try again.", e.message ?: "IO error"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling AI provider: ${e.message}")
            Result.failure(AIProviderUnavailableException("AI service is temporarily unavailable. Please try again.", e.message ?: "Unexpected error"))
        }
    }
}

package com.safeqr.scanner.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.safeqr.scanner.data.ApiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Fast LLM7.io AI Inference Client for ThreatLens.
 *
 * Provides:
 * 1. Real-time natural language threat explanations.
 * 2. Deep zero-day website classification when heuristics are ambiguous.
 * 3. Deterministic offline fallback with full parity when offline or key is unset.
 */
object Llm7Client {

    private const val TAG = "Llm7Client"
    private const val API_URL = "https://api.llm7.io/v1/chat/completions"
    private const val DEFAULT_MODEL = "default"

    val isEnabled: Boolean
        get() = ApiKeys.LLM7.isNotBlank()

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    data class WebsiteClassificationResult(
        val category: String,
        val confidence: Float,
        val reason: String
    )

    /**
     * Generates a concise, 2-sentence threat explanation for a scanned URL or payload.
     * If the API call fails or the client is offline, falls back to the deterministic neural insight.
     */
    suspend fun generateThreatExplanation(
        url: String,
        score: Float,
        flags: List<String> = emptyList(),
        threatType: String = "UNKNOWN",
        category: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            return@withContext generateDeterministicInsight(url, score, flags, threatType, category)
        }

        val systemPrompt = "You are ThreatLens AI Security Engine. Explain in 2 concise sentences why this site is dangerous or safe to a regular web user. Be objective, crisp, and direct."
        val prompt = "URL: $url\nThreat Type: $threatType\nCategory: $category\nTrust Score: ${score.toInt()}/100\nFlags: ${flags.joinToString("; ")}"

        val aiResult = generateResponse(
            prompt = prompt,
            systemPrompt = systemPrompt,
            maxTokens = 200,
            temperature = 0.3
        )

        if (!aiResult.isNullOrBlank()) {
            aiResult.trim()
        } else {
            generateDeterministicInsight(url, score, flags, threatType, category)
        }
    }

    /**
     * Classifies a website using LLM7 when heuristic signals are ambiguous or low-confidence.
     */
    suspend fun classifyWebsite(
        url: String,
        title: String = "",
        description: String = "",
        textSnippet: String = ""
    ): WebsiteClassificationResult? = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext null

        val systemPrompt = """
You are ThreatLens AI Website Classifier. Classify the website into exactly one category based on its metadata.
Allowed categories:
MALWARE, PHISHING, FINANCIAL_FRAUD, TECH_SUPPORT_SCAMS, RANSOMWARE, IMPERSONATION_SITES,
PORNOGRAPHY, GAMBLING, PIRACY, SEARCH_ENGINE, AI_ML_PLATFORMS, DEVELOPER_TOOLS,
CLOUD_SERVICES, CYBERSECURITY, BANKING, ECOMMERCE, SOCIAL_MEDIA, MESSAGING,
NEWS_MEDIA, MOVIE_STREAMING, MUSIC_STREAMING, GAMING, EDUCATION, GOVERNMENT,
HEALTHCARE, BUSINESS, GENERAL_SAFE.

Respond ONLY with valid JSON in this exact structure:
{"category": "<CATEGORY_NAME>", "confidence": <0.0 to 1.0>, "reason": "<brief 1-sentence reason>"}
""".trimIndent()

        val prompt = buildString {
            append("URL: $url\n")
            if (title.isNotBlank()) append("Title: $title\n")
            if (description.isNotBlank()) append("Description: $description\n")
            if (textSnippet.isNotBlank()) append("Content Snippet: ${textSnippet.take(350)}\n")
        }

        val response = generateResponse(
            prompt = prompt,
            systemPrompt = systemPrompt,
            maxTokens = 120,
            temperature = 0.2
        ) ?: return@withContext null

        try {
            // Find JSON within response (in case model wraps with markdown code fences)
            val cleanJson = response.substringAfter("{").substringBeforeLast("}")
            val jsonObject = gson.fromJson("{$cleanJson}", JsonObject::class.java)

            val cat = jsonObject.get("category")?.asString?.trim()?.uppercase() ?: return@withContext null
            val conf = jsonObject.get("confidence")?.asFloat ?: 0.85f
            val reason = jsonObject.get("reason")?.asString?.trim() ?: "Classified by LLM7 AI Engine"

            return@withContext WebsiteClassificationResult(
                category = cat,
                confidence = conf.coerceIn(0f, 1f),
                reason = reason
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM7 classification JSON: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Dispatches an OpenAI-compatible completion request to LLM7.io.
     */
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 300,
        temperature: Double = 0.3
    ): String? = suspendCancellableCoroutine { continuation ->
        val apiKey = ApiKeys.LLM7
        if (apiKey.isBlank()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val messages = mutableListOf<Map<String, String>>()
        if (systemPrompt.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to prompt))

        val payload = mapOf(
            "model" to DEFAULT_MODEL,
            "messages" to messages,
            "max_tokens" to maxTokens,
            "temperature" to temperature
        )

        val jsonBody = gson.toJson(payload)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    Log.w(TAG, "LLM7 call failed: ${e.message}")
                    continuation.resume(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "LLM7 returned HTTP ${resp.code}: ${resp.message}")
                        if (!continuation.isCancelled) continuation.resume(null)
                        return
                    }

                    val bodyString = resp.body?.string()
                    if (bodyString.isNullOrBlank()) {
                        if (!continuation.isCancelled) continuation.resume(null)
                        return
                    }

                    try {
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val firstChoice = choices[0].asJsonObject
                            val message = firstChoice.getAsJsonObject("message")
                            val content = message?.get("content")?.asString
                            if (!content.isNullOrBlank() && !continuation.isCancelled) {
                                continuation.resume(content.trim())
                                return
                            }
                        }
                    } catch (err: Exception) {
                        Log.e(TAG, "Error parsing LLM7 response", err)
                    }

                    if (!continuation.isCancelled) continuation.resume(null)
                }
            }
        })
    }

    /**
     * Deterministic Neural Heuristic Explainer (full parity with ThreatLens extension and offline engine).
     */
    fun generateDeterministicInsight(
        url: String,
        score: Float,
        flags: List<String> = emptyList(),
        threatType: String = "UNKNOWN",
        category: String = ""
    ): String {
        var domain = "this link"
        try {
            domain = URI(url).host ?: url
        } catch (e: Exception) {
            domain = url
        }

        val flagCount = flags.size
        val flagListStr = flags.take(2).joinToString(" and ")

        return when (threatType.uppercase()) {
            "MALWARE" -> "ThreatLens detected dangerous executable or ransomware signatures on $domain. ${if (flagListStr.isNotBlank()) flagListStr else "Direct download of unauthorized binaries or malicious scripts detected"}. Accessing this resource may compromise your device."
            "PHISHING" -> "ThreatLens flagged $domain for suspected credential theft and brand spoofing. ${if (flagListStr.isNotBlank()) flagListStr else "Lookalike domain mimicking trusted login portals"}. Do NOT enter your passwords, PINs, or financial details."
            "SCAM", "FINANCIAL_FRAUD" -> "This destination exhibits characteristics of an online fraud or social engineering campaign. ${if (flagListStr.isNotBlank()) flagListStr else "Promised rewards, prize claims, or fraudulent urgency detected"}."
            "HIGH-RISK", "GAMBLING", "PIRACY" -> "This destination ($domain) is classified under ${if (category.isNotBlank()) category else threatType}. It carries elevated risk of drive-by downloads, aggressive ad injection, and unverified third-party content."
            else -> {
                if (score <= 40f || flagCount > 0) {
                    "Multiple suspicious indicators ($flagCount flags) were identified for $domain: ${if (flagListStr.isNotBlank()) flagListStr else "Abnormal structure or unverified origin"}. ThreatLens recommends returning to safety."
                } else {
                    "$domain matches standard security criteria with no malicious signatures detected across verified threat databases."
                }
            }
        }
    }
}

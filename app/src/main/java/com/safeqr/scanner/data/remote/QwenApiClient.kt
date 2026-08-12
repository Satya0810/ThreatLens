package com.safeqr.scanner.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

data class QwenMessage(
    val role: String,
    val content: String
)

data class QwenChatRequest(
    val model: String,
    val messages: List<QwenMessage>,
    val temperature: Float = 0.3f
)

data class QwenChatChoice(
    val message: QwenMessage
)

data class QwenChatResponse(
    val choices: List<QwenChatChoice>
)

interface QwenApiClient {
    @Headers("ngrok-skip-browser-warning: 69420")
    @POST("chat/completions")
    suspend fun generateClassification(
        @Header("Authorization") authHeader: String,
        @Body request: QwenChatRequest
    ): QwenChatResponse
}

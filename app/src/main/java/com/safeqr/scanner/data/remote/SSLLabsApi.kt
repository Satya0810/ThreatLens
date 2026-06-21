package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class SSLLabsResponse(
    val host: String? = null,
    val port: Int = 443,
    val status: String? = null,
    val endpoints: List<SSLEndpoint>? = null
)
data class SSLEndpoint(
    val ipAddress: String? = null,
    val grade: String? = null,
    val gradeTrustIgnored: String? = null,
    val hasWarnings: Boolean = false,
    val isExceptional: Boolean = false,
    val statusMessage: String? = null
)

interface SSLLabsApi {
    @GET("api/v3/analyze")
    suspend fun analyze(
        @Query("host") host: String,
        @Query("fromCache") fromCache: String = "on",
        @Query("maxAge") maxAge: Int = 24
    ): SSLLabsResponse
}

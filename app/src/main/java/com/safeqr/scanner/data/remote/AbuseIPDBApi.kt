package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class AbuseIPDBResponse(val data: AbuseIPDBData? = null)
data class AbuseIPDBData(
    val ipAddress: String? = null,
    val isPublic: Boolean = true,
    val abuseConfidenceScore: Int = 0,
    val countryCode: String? = null,
    val isp: String? = null,
    val domain: String? = null,
    val totalReports: Int = 0,
    val lastReportedAt: String? = null,
    val isWhitelisted: Boolean = false
)

interface AbuseIPDBApi {
    @GET("api/v2/check")
    suspend fun checkIP(
        @Query("ipAddress") ipAddress: String,
        @Query("maxAgeInDays") maxAge: Int = 90,
        @Header("Key") apiKey: String,
        @Header("Accept") accept: String = "application/json"
    ): AbuseIPDBResponse
}

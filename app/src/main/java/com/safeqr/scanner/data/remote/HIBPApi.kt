package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class HIBPBreach(
    val Name: String? = null,
    val Title: String? = null,
    val Domain: String? = null,
    val BreachDate: String? = null,
    val PwnCount: Int = 0,
    val Description: String? = null,
    val DataClasses: List<String>? = null,
    val IsVerified: Boolean = false
)

interface HIBPApi {
    @GET("api/v3/breaches")
    suspend fun getBreachesByDomain(
        @Query("domain") domain: String,
        @Header("hibp-api-key") apiKey: String,
        @Header("user-agent") userAgent: String = "SafeQRScanner"
    ): List<HIBPBreach>
}

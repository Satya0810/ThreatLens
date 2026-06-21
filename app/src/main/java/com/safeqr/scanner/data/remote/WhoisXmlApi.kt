package com.safeqr.scanner.data.remote

import retrofit2.http.*

// ── WhoisXML API Response Models ──────────────────────────────────────────────

data class WhoisXmlResponse(
    val WhoisRecord: WhoisRecord? = null
)

data class WhoisRecord(
    val domainName: String? = null,
    val registrarName: String? = null,
    val createdDateNormalized: String? = null,
    val updatedDateNormalized: String? = null,
    val expiresDateNormalized: String? = null,
    val estimatedDomainAge: Int? = null,   // age in days
    val registrant: WhoisContact? = null,
    val domainAvailability: String? = null  // "AVAILABLE" or "UNAVAILABLE"
)

data class WhoisContact(
    val organization: String? = null,
    val country: String? = null,
    val state: String? = null
)

// ── WhoisXML Reputation API ──────────────────────────────────────────────────

data class WhoisReputationResponse(
    val reputationScore: Float? = null,    // 0 (bad) to 100 (good)
    val testResults: List<WhoisTestResult>? = null
)

data class WhoisTestResult(
    val test: String? = null,
    val testCode: Int? = null,
    val warnings: List<String>? = null
)

// ── Retrofit Interface ───────────────────────────────────────────────────────

interface WhoisXmlApi {

    /**
     * WHOIS domain lookup — returns registration data, domain age, registrar, etc.
     * Docs: https://whois.whoisxmlapi.com/documentation/making-requests
     */
    @GET("whoisserver/WhoisService")
    suspend fun lookup(
        @Query("domainName") domain: String,
        @Query("apiKey") apiKey: String,
        @Query("outputFormat") format: String = "JSON"
    ): WhoisXmlResponse

    /**
     * Domain Reputation API — returns a 0–100 reputation score + test results.
     * Docs: https://domain-reputation.whoisxmlapi.com/api/documentation
     */
    @GET("api/v1")
    suspend fun reputation(
        @Query("domainName") domain: String,
        @Query("apiKey") apiKey: String,
        @Query("outputFormat") format: String = "JSON"
    ): WhoisReputationResponse
}

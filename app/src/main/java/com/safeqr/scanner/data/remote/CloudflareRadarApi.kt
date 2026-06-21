package com.safeqr.scanner.data.remote

import retrofit2.http.*

// ── Cloudflare Radar URL Scanner Response Models ─────────────────────────────

data class CloudflareRadarSubmitRequest(
    val url: String,
    val visibility: String = "unlisted"
)

data class CloudflareRadarSubmitResponse(
    val success: Boolean = false,
    val result: CloudflareSubmitResult? = null,
    val errors: List<CloudflareError>? = null
)

data class CloudflareSubmitResult(
    val uuid: String? = null,
    val url: String? = null,
    val visibility: String? = null
)

data class CloudflareError(
    val code: Int? = null,
    val message: String? = null
)

data class CloudflareRadarScanResponse(
    val success: Boolean = false,
    val result: CloudflareScanResult? = null
)

data class CloudflareScanResult(
    val scan: CloudflareScan? = null
)

data class CloudflareScan(
    val task: CloudflareTask? = null,
    val verdicts: CloudflareVerdicts? = null,
    val categories: CloudflareCategories? = null
)

data class CloudflareTask(
    val uuid: String? = null,
    val url: String? = null,
    val status: String? = null  // "Finished", "Queued", etc.
)

data class CloudflareVerdicts(
    val overall: CloudflareVerdict? = null
)

data class CloudflareVerdict(
    val malicious: Boolean = false,
    val phishing: List<String>? = null,
    val categories: List<CloudflareCategory>? = null
)

data class CloudflareCategory(
    val id: Int? = null,
    val superCategoryId: Int? = null,
    val name: String? = null
)

data class CloudflareCategories(
    val content: List<CloudflareCategory>? = null,
    val risks: List<CloudflareCategory>? = null
)

// ── Retrofit Interface ───────────────────────────────────────────────────────

interface CloudflareRadarApi {

    /**
     * Search existing scans for a URL without submitting a new scan.
     * Docs: https://developers.cloudflare.com/radar/investigate/url-scanner/
     */
    @GET("api/v1/url_scanner/scan")
    suspend fun searchScans(
        @Query("url") url: String,
        @Header("Authorization") authToken: String
    ): CloudflareRadarScanResponse

    /**
     * Submit a new URL for scanning.
     * Docs: https://developers.cloudflare.com/api/resources/url_scanner/methods/create_scan/
     */
    @POST("api/v1/url_scanner/scan")
    suspend fun submitScan(
        @Body request: CloudflareRadarSubmitRequest,
        @Header("Authorization") authToken: String
    ): CloudflareRadarSubmitResponse

    /**
     * Get scan result by UUID.
     */
    @GET("api/v1/url_scanner/scan/{uuid}")
    suspend fun getScanResult(
        @Path("uuid") uuid: String,
        @Header("Authorization") authToken: String
    ): CloudflareRadarScanResponse
}

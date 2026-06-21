package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class UrlScanRequest(val url: String, val visibility: String = "unlisted")
data class UrlScanSubmitResponse(val uuid: String? = null, val result: String? = null, val message: String? = null)
data class UrlScanSearchResponse(val results: List<UrlScanSearchResult> = emptyList())
data class UrlScanSearchResult(
    val task: UrlScanTask? = null,
    val verdicts: UrlScanVerdicts? = null,
    val page: UrlScanPage? = null
)
data class UrlScanTask(val uuid: String? = null, val url: String? = null)
data class UrlScanVerdicts(
    val overall: UrlScanVerdict? = null
)
data class UrlScanVerdict(
    val score: Int = 0,
    val malicious: Boolean = false,
    val hasVerdicts: Boolean = false
)
data class UrlScanPage(val domain: String? = null, val ip: String? = null, val country: String? = null)

interface UrlScanApi {
    @GET("api/v1/search/")
    suspend fun search(
        @Query("q") query: String,
        @Header("API-Key") apiKey: String
    ): UrlScanSearchResponse
}

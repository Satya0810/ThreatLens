package com.safeqr.scanner.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// ── Response data class ───────────────────────────────────────────────────────

data class VTResponse(
    @SerializedName("response_code")
    val responseCode: Int,
    val positives: Int,
    val total: Int,
    @SerializedName("scan_date")
    val scanDate: String?,
    val permalink: String?
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface VirusTotalApi {

    @GET("vtapi/v2/url/report")
    suspend fun getUrlReport(
        @Query("apikey") apiKey: String,
        @Query("resource") resource: String
    ): VTResponse
}

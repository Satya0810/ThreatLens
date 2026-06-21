package com.safeqr.scanner.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request data classes ──────────────────────────────────────────────────────

data class ThreatMatchRequest(
    val client: ClientInfo,
    val threatInfo: ThreatInfo
)

data class ClientInfo(
    val clientId: String = "safeqr-scanner",
    val clientVersion: String = "1.0.0"
)

data class ThreatInfo(
    val threatTypes: List<String>,
    val platformTypes: List<String>,
    val threatEntryTypes: List<String>,
    val threatEntries: List<ThreatEntry>
)

data class ThreatEntry(
    val url: String
)

// ── Response data classes ─────────────────────────────────────────────────────

data class ThreatMatchResponse(
    val matches: List<ThreatMatch>?
)

data class ThreatMatch(
    val threatType: String?,
    val platformType: String?,
    val threatEntryType: String?,
    val threat: ThreatEntry?,
    @SerializedName("cacheDuration")
    val cacheDuration: String?
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface SafeBrowsingApi {

    @POST("v4/threatMatches:find")
    suspend fun findThreatMatches(
        @Query("key") apiKey: String,
        @Body request: ThreatMatchRequest
    ): ThreatMatchResponse
}

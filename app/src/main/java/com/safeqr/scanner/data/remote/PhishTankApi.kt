package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class PhishTankResponse(
    val results: PhishTankResult? = null,
    val meta: PhishTankMeta? = null
)
data class PhishTankResult(
    val in_database: Boolean = false,
    val phish_id: String? = null,
    val phish_detail_page: String? = null,
    val verified: Boolean = false,
    val valid: Boolean = false
)
data class PhishTankMeta(
    val status: String? = null
)

interface PhishTankApi {
    @FormUrlEncoded
    @POST("checkurl/")
    suspend fun checkUrl(
        @Field("url") url: String,
        @Field("format") format: String = "json",
        @Field("app_key") appKey: String
    ): PhishTankResponse
}

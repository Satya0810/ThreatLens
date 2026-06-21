package com.safeqr.scanner.data.remote

import retrofit2.http.*

data class UrlHausResponse(
    val query_status: String? = null,
    val id: String? = null,
    val url_status: String? = null,
    val threat: String? = null,
    val tags: List<String>? = null,
    val reference: String? = null,
    val date_added: String? = null
)

interface UrlHausApi {
    @FormUrlEncoded
    @POST("v1/url/")
    suspend fun checkUrl(
        @Field("url") url: String
    ): UrlHausResponse
}

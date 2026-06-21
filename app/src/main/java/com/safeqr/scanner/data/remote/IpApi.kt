package com.safeqr.scanner.data.remote

import androidx.annotation.Keep
import retrofit2.http.GET
import retrofit2.http.Path

@Keep
data class IpApiResponse(
    val status: String,
    val country: String?,
    val countryCode: String?,
    val regionName: String?,
    val city: String?,
    val isp: String?,
    val org: String?,
    val asname: String?,
    val query: String?
)

interface IpApi {
    @GET("json/{domain}")
    suspend fun lookup(@Path("domain") domain: String): IpApiResponse
}

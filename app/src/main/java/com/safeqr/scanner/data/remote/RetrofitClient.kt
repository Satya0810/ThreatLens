package com.safeqr.scanner.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val SAFE_BROWSING_BASE_URL = "https://safebrowsing.googleapis.com/"
    private const val VIRUS_TOTAL_BASE_URL = "https://www.virustotal.com/"
    private const val PHISH_TANK_BASE_URL = "https://checkurl.phishtank.com/"
    private const val URL_SCAN_BASE_URL = "https://urlscan.io/"
    private const val ABUSE_IPDB_BASE_URL = "https://api.abuseipdb.com/"
    private const val URL_HAUS_BASE_URL = "https://urlhaus-api.abuse.ch/"
    private const val HIBP_BASE_URL = "https://haveibeenpwned.com/"
    private const val SSL_LABS_BASE_URL = "https://api.ssllabs.com/"
    private const val WHOIS_XML_BASE_URL = "https://www.whoisxmlapi.com/"
    private const val WHOIS_REPUTATION_BASE_URL = "https://domain-reputation.whoisxmlapi.com/"
    private const val CLOUDFLARE_RADAR_BASE_URL = "https://api.cloudflare.com/"
    private const val IP_API_BASE_URL = "http://ip-api.com/"
    private const val TIMEOUT_SECONDS = 15L

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Existing APIs
    val safeBrowsingApi: SafeBrowsingApi by lazy {
        createRetrofit(SAFE_BROWSING_BASE_URL).create(SafeBrowsingApi::class.java)
    }

    val virusTotalApi: VirusTotalApi by lazy {
        createRetrofit(VIRUS_TOTAL_BASE_URL).create(VirusTotalApi::class.java)
    }

    // New APIs
    val phishTankApi: PhishTankApi by lazy {
        createRetrofit(PHISH_TANK_BASE_URL).create(PhishTankApi::class.java)
    }

    val urlScanApi: UrlScanApi by lazy {
        createRetrofit(URL_SCAN_BASE_URL).create(UrlScanApi::class.java)
    }

    val abuseIPDBApi: AbuseIPDBApi by lazy {
        createRetrofit(ABUSE_IPDB_BASE_URL).create(AbuseIPDBApi::class.java)
    }

    val urlHausApi: UrlHausApi by lazy {
        createRetrofit(URL_HAUS_BASE_URL).create(UrlHausApi::class.java)
    }

    val hibpApi: HIBPApi by lazy {
        createRetrofit(HIBP_BASE_URL).create(HIBPApi::class.java)
    }

    val sslLabsApi: SSLLabsApi by lazy {
        createRetrofit(SSL_LABS_BASE_URL).create(SSLLabsApi::class.java)
    }

    val whoisXmlApi: WhoisXmlApi by lazy {
        createRetrofit(WHOIS_XML_BASE_URL).create(WhoisXmlApi::class.java)
    }

    val whoisReputationApi: WhoisXmlApi by lazy {
        createRetrofit(WHOIS_REPUTATION_BASE_URL).create(WhoisXmlApi::class.java)
    }

    val cloudflareRadarApi: CloudflareRadarApi by lazy {
        createRetrofit(CLOUDFLARE_RADAR_BASE_URL).create(CloudflareRadarApi::class.java)
    }

    val ipApi: IpApi by lazy {
        createRetrofit(IP_API_BASE_URL).create(IpApi::class.java)
    }
}

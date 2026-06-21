package com.safeqr.scanner.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * Expands shortened URLs by following redirect chains up to a configurable hop limit.
 */
object UrlExpander {

    private const val MAX_REDIRECTS = 10
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /** Known URL shortener domains (lowercase). */
    private val SHORT_URL_DOMAINS = setOf(
        "bit.ly",
        "t.co",
        "tinyurl.com",
        "goo.gl",
        "ow.ly",
        "is.gd",
        "buff.ly",
        "adf.ly",
        "shorte.st"
    )

    /**
     * Expands a URL by following HTTP redirects (HEAD/GET requests) up to [MAX_REDIRECTS] hops.
     * Returns a List of URLs representing the entire redirect chain. The first element is the original URL,
     * and the last element is the final destination.
     */
    suspend fun unroll(url: String): List<String> = withContext(Dispatchers.IO) {
        val chain = mutableListOf<String>()
        var currentUrl = url
        chain.add(currentUrl)
        var redirectCount = 0

        try {
            while (redirectCount < MAX_REDIRECTS) {
                val connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "HEAD"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ThreatLens/1.0")
                }

                try {
                    connection.connect()
                    val responseCode = connection.responseCode

                    if (responseCode in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) break

                        // Handle relative redirects
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            val base = URI(currentUrl)
                            base.resolve(location).toString()
                        }
                        chain.add(currentUrl)
                        redirectCount++
                    } else {
                        // No more redirects
                        break
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            // On any network error, return the chain built so far
        }

        return@withContext chain
    }
}

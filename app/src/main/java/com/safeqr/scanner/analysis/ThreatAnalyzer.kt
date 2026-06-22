package com.safeqr.scanner.analysis

import android.util.Log
import com.safeqr.scanner.data.ApiKeys
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.data.remote.ClientInfo
import com.safeqr.scanner.data.remote.CloudDatasetManager
import com.safeqr.scanner.data.remote.CloudSyncManager
import com.safeqr.scanner.data.remote.RetrofitClient
import com.safeqr.scanner.data.remote.ThreatEntry
import com.safeqr.scanner.data.remote.ThreatInfo
import com.safeqr.scanner.data.remote.ThreatMatchRequest
import java.net.InetAddress
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Orchestrates the full threat-analysis pipeline for scanned QR content.
 */
object ThreatAnalyzerDefaults {
    val TRANSACTION_SCHEMES = listOf("upi", "paytm", "gpay", "phonepe", "bitcoin", "ethereum")

    val TRANSACTION_DOMAINS = listOf(
        "paypal.com", "venmo.com", "cash.app", "stripe.com",
        "razorpay.com", "payu.in", "billdesk.com", "checkout.com"
    )
    val TRANSACTION_PATH_KEYWORDS = listOf("pay", "checkout", "billing", "invoice", "transfer")

    val ADULT_TLDS = listOf(".xxx", ".adult", ".porn", ".sex", ".cam")
    val ADULT_DOMAINS = listOf(
        "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "redtube.com",
        "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "chaturbate.com",
        "bongacams.com", "stripchat.com", "livejasmin.com", "onlyfans.com", "fansly.com"
    )
    val ADULT_DOMAIN_KEYWORDS = listOf("porn", "xxx", "sex", "nude", "cam", "fuck", "milf", "hentai")
    val ADULT_PATH_KEYWORDS = listOf("/porn", "/video/sex", "/nsfw", "/mature", "/18+")
    val TRUSTED_DOMAINS = listOf("google.com", "youtube.com", "facebook.com", "instagram.com", "twitter.com", "wikipedia.org", "amazon.com", "microsoft.com", "apple.com", "linkedin.com", "github.com", "reddit.com", "byjus.com", "meesho.com", "flipkart.com", "myntra.com", "swiggy.com", "zomato.com", "unstop.com", "internshala.com", "hackerrank.com", "leetcode.com", "geeksforgeeks.org")

    // ── Piracy Domain Detection ──────────────────────────────────────────
    val PIRACY_DOMAINS = listOf(
        "vegamovies.gripe", "vegamovies.nl", "vegamovies.to", "vegamovies.ist",
        "tamilrockers.ws", "tamilrockers.wc", "tamilrockers.com",
        "1337x.to", "1337x.st", "1337x.gd",
        "rarbg.to", "rarbg.me",
        "yts.mx", "yts.am", "yts.lt",
        "piratebay.org", "thepiratebay.org", "thepiratebay.se",
        "kickass.to", "kickasstorrents.to", "katcr.to",
        "limetorrents.cc", "limetorrents.info",
        "torrentz2.eu", "torrentz2.me",
        "nyaa.si", "nyaa.net",
        "fmovies.to", "fmovies.wtf", "fmovies.ps",
        "123movies.to", "123movies.net",
        "putlocker.to", "putlockers.fm",
        "gomovies.to", "gostream.is",
        "soap2day.to", "soap2day.ac",
        "hdmovie2.me", "hdmovie2.ws",
        "movierulz.com", "movierulz.pe", "movierulz.gs",
        "filmyzilla.com", "filmyzilla.in",
        "mp4moviez.com", "mp4moviez.in",
        "bolly4u.org", "bolly4u.cc",
        "worldfree4u.com", "worldfree4u.lol",
        "9xmovies.in", "9xmovies.com",
        "kuttymovies.com", "isaimini.com",
        "tamilyogi.com", "tamilyogi.cc",
        "ssrmovies.club", "extramovies.com",
        "downloadhub.in", "downloadhub.ws",
        "skymovieshd.com", "skymovieshd.in",
        "afilmywap.com", "filmywap.com",
        "pagalmovies.com", "cinevood.com",
        "katmoviehd.com", "katmoviehd.se"
    )
    val PIRACY_DOMAIN_KEYWORDS = listOf("torrent", "movie", "pirate", "warez", "crack", "nulled", "fmovie", "putlocker", "123movie", "gomovie", "soap2day", "movierulz", "filmyzilla", "mp4moviez", "hdmovie", "bolly4u", "9xmovie", "kuttymovie", "tamilyogi", "isaimini", "downloadhub", "skymovieshd", "filmywap", "pagalmovie", "cinevood", "katmovie", "vegamovie", "tamilrocker", "yts", "rarbg", "kickass", "limetorrent")
    val PIRACY_TLDS = listOf(".gripe", ".ist", ".wc", ".gd", ".wtf", ".ps", ".ac", ".lol")

    // Topic Heuristics
    val PIRACY_KEYWORDS = listOf("torrent", "crack", "keygen", "free download", "pirate", "nulled", "seeders", "leechers", "repack", "dubbed", "hdcam", "dvdscr", "hdts", "camrip", "webrip", "brrip", "480p", "720p", "1080p", "dual audio", "hindi dubbed", "movie download", "full movie")
    val BAITING_KEYWORDS = listOf("claim your prize", "winner", "giveaway", "free iphone", "spin the wheel", "lottery")
    val ADULT_KEYWORDS = listOf("porn", "sex", "xxx", "nude", "mature", "escort", "nsfw", "onlyfans", "cam")
    val SHOPPING_KEYWORDS = listOf("add to cart", "checkout", "buy now", "discount", "promo code", "shipping")
    val SOCIAL_KEYWORDS = listOf("login", "sign up", "password", "forgot password", "verify account")
    val NEWS_KEYWORDS = listOf("breaking news", "article", "journal", "reporter")
    val EDUCATION_KEYWORDS = listOf("university", "course", "syllabus", "student")
    val ENTERTAINMENT_KEYWORDS = listOf("stream", "watch", "movie", "episode", "season", "hd")
}

class ThreatAnalyzer {

    companion object {
        private const val TAG = "ThreatAnalyzer"

        private val URL_REGEX = Regex(
            "^(https?://)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)$",
            RegexOption.IGNORE_CASE
        )

        private val IP_ADDRESS_REGEX = Regex(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
        )

        private val cloudData get() = CloudDatasetManager.getThreatAnalyzerData()

        private val TRANSACTION_SCHEMES get() = cloudData.TRANSACTION_SCHEMES.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.TRANSACTION_SCHEMES
        private val TRANSACTION_DOMAINS get() = cloudData.TRANSACTION_DOMAINS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.TRANSACTION_DOMAINS
        private val TRANSACTION_PATH_KEYWORDS get() = cloudData.TRANSACTION_PATH_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.TRANSACTION_PATH_KEYWORDS
        private val ADULT_TLDS get() = cloudData.ADULT_TLDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ADULT_TLDS
        private val ADULT_DOMAINS get() = cloudData.ADULT_DOMAINS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ADULT_DOMAINS
        private val ADULT_DOMAIN_KEYWORDS get() = cloudData.ADULT_DOMAIN_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ADULT_DOMAIN_KEYWORDS
        private val ADULT_PATH_KEYWORDS get() = cloudData.ADULT_PATH_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ADULT_PATH_KEYWORDS
        private val TRUSTED_DOMAINS get() = cloudData.TRUSTED_DOMAINS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.TRUSTED_DOMAINS
        private val PIRACY_DOMAINS get() = cloudData.PIRACY_DOMAINS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.PIRACY_DOMAINS
        private val PIRACY_DOMAIN_KEYWORDS get() = cloudData.PIRACY_DOMAIN_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.PIRACY_DOMAIN_KEYWORDS
        private val PIRACY_TLDS get() = cloudData.PIRACY_TLDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.PIRACY_TLDS
        private val PIRACY_KEYWORDS get() = cloudData.PIRACY_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.PIRACY_KEYWORDS
        private val BAITING_KEYWORDS get() = cloudData.BAITING_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.BAITING_KEYWORDS
        private val ADULT_KEYWORDS get() = cloudData.ADULT_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ADULT_KEYWORDS
        private val SHOPPING_KEYWORDS get() = cloudData.SHOPPING_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.SHOPPING_KEYWORDS
        private val SOCIAL_KEYWORDS get() = cloudData.SOCIAL_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.SOCIAL_KEYWORDS
        private val NEWS_KEYWORDS get() = cloudData.NEWS_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.NEWS_KEYWORDS
        private val EDUCATION_KEYWORDS get() = cloudData.EDUCATION_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.EDUCATION_KEYWORDS
        private val ENTERTAINMENT_KEYWORDS get() = cloudData.ENTERTAINMENT_KEYWORDS.takeIf { it.isNotEmpty() } ?: ThreatAnalyzerDefaults.ENTERTAINMENT_KEYWORDS

        private val compiledRegexCache = ConcurrentHashMap<String, Regex>()
        private fun getCachedRegex(keyword: String): Regex {
            return compiledRegexCache.getOrPut(keyword) { "\\b${Pattern.quote(keyword)}\\b".toRegex(RegexOption.IGNORE_CASE) }
        }

        /**
         * Entry point to analyze a URL/Text/QR Code.
         * Dynamically applies new community reports to an existing ScanResult.
         * This undoes previous community score impacts and applies the new ones,
         * updating the score, status, and summary text properly.
         */
        fun applyDynamicCommunityReports(
            scanResult: ScanResult, 
            newCommunityReasons: List<String>, 
            visitCount: Long = 1L
        ): ScanResult {
            // First, strip out old community effects from the overallScore.
            var baseScore = scanResult.overallScore
            
            val oldPositive = scanResult.communityReportReasons.filter { it.startsWith("👍") }
            val oldNegative = scanResult.communityReportReasons.filter { !it.startsWith("👍") }
            val totalVisits = maxOf(1L, visitCount, scanResult.visitCount.toLong())
            
            if (oldNegative.isNotEmpty()) {
                val ratio = (oldNegative.size.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
                val oldPenalty = (oldNegative.size * 10f * ratio).toFloat().coerceIn(1f, 40f)
                baseScore += oldPenalty // undo penalty
            }
            if (oldPositive.isNotEmpty()) {
                val ratio = (oldPositive.size.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
                val oldBoost = (oldPositive.size * 5f * ratio).toFloat().coerceIn(1f, 25f)
                baseScore -= oldBoost // undo boost
            }
            
            // Now apply new community effects
            val newPositive = newCommunityReasons.filter { it.startsWith("👍") }
            val newNegative = newCommunityReasons.filter { !it.startsWith("👍") }
            var newScore = baseScore
            
            if (newNegative.isNotEmpty()) {
                val ratio = (newNegative.size.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
                val penalty = (newNegative.size * 10f * ratio).toFloat().coerceIn(1f, 40f)
                newScore -= penalty
            }
            
            if (newPositive.isNotEmpty()) {
                val ratio = (newPositive.size.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
                val boost = (newPositive.size * 5f * ratio).toFloat().coerceIn(1f, 25f)
                newScore += boost
            }
            
            newScore = newScore.coerceIn(0f, 100f)
            
            // Safety status recalculation: Never let community feedback override a hard deterministic threat block!
            val newStatus = when {
                scanResult.safetyStatus == SafetyStatus.MALICIOUS -> SafetyStatus.MALICIOUS
                newScore <= 40f -> SafetyStatus.MALICIOUS
                scanResult.safetyStatus == SafetyStatus.CAUTION -> SafetyStatus.CAUTION
                newScore <= 75f -> SafetyStatus.CAUTION
                else -> SafetyStatus.SAFE
            }
            
            // Update Site Summary
            var updatedSummary = scanResult.siteSummary ?: ""
            val communitySectionStart = updatedSummary.indexOf("🤖 Community Intelligence:\n")
            val apiSectionStart = updatedSummary.indexOf("🌐 APIs Report:\n")
            if (communitySectionStart != -1 && apiSectionStart != -1) {
                val pre = updatedSummary.substring(0, communitySectionStart)
                val post = updatedSummary.substring(apiSectionStart)
                val newCommunityText = buildString {
                    append("🤖 Community Intelligence:\n")
                    if (newNegative.isNotEmpty()) {
                        append("• 🔴 Flagged by ${newNegative.size} user(s): ${newNegative.joinToString()}\n")
                    }
                    if (newPositive.isNotEmpty()) {
                        append("• 🟢 Appreciated by ${newPositive.size} user(s): ${newPositive.map { it.removePrefix("👍").trim() }.joinToString()}\n")
                    }
                    if (newNegative.isEmpty() && newPositive.isEmpty()) {
                        append("• No community reports yet.\n")
                    }
                    if (scanResult.isBrandImpersonation) append("• ⚠️ Brand impersonation suspected.\n")
                    append("\n")
                }
                updatedSummary = pre + newCommunityText + post
            }
            
            // Update Trust Score line
            val scoreRegex = Regex("• Trust Score: [0-9.]+/100\n")
            updatedSummary = updatedSummary.replace(scoreRegex, "• Trust Score: ${String.format("%.1f", newScore)}/100\n")
            
            return scanResult.copy(
                overallScore = newScore,
                safetyStatus = newStatus,
                communityReportsCount = newCommunityReasons.size,
                communityReportReasons = newCommunityReasons,
                threatDetails = scanResult.threatDetails,
                positiveDetails = scanResult.positiveDetails,
                siteSummary = updatedSummary
            )
        }
    }

    suspend fun analyze(
        rawContent: String,
        communityReportsCount: Int = 0,
        communityReportReasons: List<String> = emptyList(),
        webshrinkerApiKey: String = ""
    ): ScanResult {
        // PERMANENT SOLUTION: We completely remove the overarching timeout wrapper.
        // Instead, we rely entirely on the strict individual micro-timeouts of each API (e.g., 3s, 4s, 6s).
        // Since the APIs run concurrently via async{}, the maximum time the scan can take 
        // is exactly equal to the longest single API timeout, effectively eliminating endless loops.
        return analyzeInternal(rawContent, communityReportsCount, communityReportReasons, webshrinkerApiKey)
    }

    private suspend fun analyzeInternal(
        rawContent: String,
        communityReportsCount: Int = 0,
        communityReportReasons: List<String> = emptyList(),
        webshrinkerApiKey: String = ""
    ): ScanResult {
        val trimmed = rawContent.trim()
        val lowercaseContent = trimmed.lowercase()

        // ── Step 1: Broad URL & Scheme Detection ──────────────────────────────
        val isWebUrl = QrDataParser.parse(rawContent).type == QrDataType.URL
        val scheme = try {
            URI(trimmed).scheme?.lowercase()
        } catch (e: Exception) {
            null
        }

        val isTransactionScheme = scheme != null && TRANSACTION_SCHEMES.contains(scheme)
        val isRawEmail = lowercaseContent.startsWith("mailto:") || (trimmed.contains("@") && !trimmed.contains("/") && !trimmed.contains(" "))
        val isRawPhone = lowercaseContent.startsWith("tel:") || lowercaseContent.startsWith("smsto:") || (trimmed.matches(Regex("^[+]?[0-9]{7,15}$")))
        val isWifi = lowercaseContent.startsWith("wifi:")

        val isInteractiveUri = isWebUrl || isTransactionScheme || isRawEmail || isRawPhone || isWifi || scheme != null
        
        // Fetch community reports from Cloud (with strict 3-second timeout)
        val (cloudReportsCount, cloudReportReasons, visitCount) = if (isWebUrl) {
            withTimeoutOrNull(3000) {
                CloudSyncManager.logVisit(rawContent)
                CloudSyncManager.getCommunityReports(rawContent)
            } ?: Triple(0, emptyList<String>(), 0L)
        } else {
            Triple(0, emptyList<String>(), 0L)
        }
        
        // Merge passed parameters (which include local reports) with newly fetched cloud reports
        val mergedCount = communityReportsCount + cloudReportsCount
        val mergedReasons = (communityReportReasons + cloudReportReasons)


        if (!isInteractiveUri) {
            return ScanResult(
                rawContent = rawContent,
                isUrl = false,
                safetyStatus = SafetyStatus.SAFE,
                overallScore = 100f,
                isTransaction = isTransactionScheme
            )
        }

        if (!isWebUrl) {
            val inferredUrl = when {
                isRawEmail && !lowercaseContent.startsWith("mailto:") -> "mailto:$trimmed"
                isRawPhone && !lowercaseContent.startsWith("tel:") -> "tel:${trimmed.replace(Regex("[^0-9+]"), "")}"
                else -> trimmed
            }
            return ScanResult(
                rawContent = rawContent,
                isUrl = false,
                originalUrl = inferredUrl,
                expandedUrl = inferredUrl,
                domain = null,
                safetyStatus = SafetyStatus.SAFE,
                overallScore = 100f,
                isAdultContent = false,
                isTransaction = isTransactionScheme
            )
        }

        // ── Step 1.5: Obfuscation Sanitizer (Homoglyph & Zero-width) ──────────
        var sanitizedUrl = trimmed
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            
        sanitizedUrl = Normalizer.normalize(sanitizedUrl, Normalizer.Form.NFKC)

        val normalizedUrl = if (sanitizedUrl.lowercase().startsWith("www.")) {
            "https://$sanitizedUrl"
        } else {
            sanitizedUrl
        }

        // ── Step 2: Extract domain ────────────────────────────────────────────
        val domain = try {
            URI(normalizedUrl).host?.lowercase()
        } catch (e: Exception) {
            null
        }

        val isHttps = normalizedUrl.lowercase().startsWith("https://")
        val isIpAddress = domain != null && IP_ADDRESS_REGEX.matches(domain)

        // ── Step 3: Expand shortened URLs & Unroll Redirects ──────────────────
        val redirectChain = try {
            UrlExpander.unroll(normalizedUrl)
        } catch (e: Exception) {
            listOf(normalizedUrl)
        }
        val expandedUrl = redirectChain.last()

        // ── Step 4: Heuristic checks ──────────────────────────────────────────
        val heuristicFlags = HeuristicChecker.analyze(expandedUrl)
        val threatDetails = CopyOnWriteArrayList<String>()

        var safeBrowsingHasThreats = false
        var safeBrowsingResult: String? = null
        var vtPositives = 0
        var vtTotal = 0
        var openPhishMatch = false
        var urlScanMalicious = false
        var cloudflareMalicious = false
        var ipApiMalicious = false
        var spamhausListed = false
        var urlHausMatch = false
        var cleanBrowsingBlocked = false
        var sslHasWarnings = false
        var cloudflareCategory: String? = null
        var sslGrade: String? = null
        var jsoupSummary: String? = null
        var abuseConfidenceScore = 0
        
        // Scraped OG metadata for the new categorizer
        var scrapedTitle = ""
        var scrapedDescription = ""
        var scrapedOgType = ""
        var scrapedOgSiteName = ""
        var scrapedBodyText = ""
        var scrapedH1H2Text = ""
        var scrapedNavLinksText = ""
        var scrapedFooterText = ""
        var scrapedTotalWordCount = 0
        var scrapedMainImageUrl: String? = null
        var scrapedHasVideo = false
        var domainAgeDays: Int? = null
        var whoisReputationScore: Float? = null
        var suspiciousDownloadLinks = false
        var symantecMalicious = false
        var talosMalicious = false
        var nsfwLikely = false
        var isBrandImpersonation = false
        var scrapedMetaKeywords = ""

        var foundPiracyScore = 0
        var foundBaitingScore = 0
        var foundAdultScore = 0
        var foundShoppingScore = 0
        var foundSocialScore = 0
        var foundNewsScore = 0
        var foundEducationScore = 0
        var foundEntertainmentScore = 0

        coroutineScope {
            val safeBrowsingDeferred = async(Dispatchers.IO) {
                if (ApiKeys.SAFE_BROWSING.isNotBlank()) {
                    withTimeoutOrNull(4000) {
                        try {
                            val request = ThreatMatchRequest(
                                client = ClientInfo(),
                                threatInfo = ThreatInfo(
                                    threatTypes = listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                                    platformTypes = listOf("ANY_PLATFORM"),
                                    threatEntryTypes = listOf("URL"),
                                    threatEntries = listOf(ThreatEntry(url = expandedUrl))
                                )
                            )
                            val response = RetrofitClient.safeBrowsingApi.findThreatMatches(ApiKeys.SAFE_BROWSING, request)
                            if (!response.matches.isNullOrEmpty()) {
                                safeBrowsingHasThreats = true
                                safeBrowsingResult = response.matches.joinToString { it.threatType ?: "UNKNOWN" }
                                response.matches.forEach { match ->
                                    threatDetails.add("Safe Browsing: ${match.threatType ?: "Unknown threat"}")
                                }
                            }
                        } catch (e: Exception) { Log.w(TAG, "Safe Browsing check failed", e); Unit }
                    }
                }
            }

            val virusTotalDeferred = async(Dispatchers.IO) {
                if (ApiKeys.VIRUS_TOTAL.isNotBlank()) {
                    withTimeoutOrNull(4000) {
                        try {
                            val vtResponse = RetrofitClient.virusTotalApi.getUrlReport(ApiKeys.VIRUS_TOTAL, expandedUrl)
                            vtPositives = vtResponse.positives
                            vtTotal = vtResponse.total
                            if (vtPositives > 0) {
                                threatDetails.add("VirusTotal: $vtPositives/$vtTotal engines flagged")
                            }
                        } catch (e: Exception) { Log.w(TAG, "VirusTotal check failed", e); Unit }
                    }
                }
            }

            val urlHausDeferred = async(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    try {
                        val uhResponse = RetrofitClient.urlHausApi.checkUrl(expandedUrl)
                        if (uhResponse.query_status == "ok") {
                            urlHausMatch = true
                            threatDetails.add("URLhaus: Known malware URL (${uhResponse.threat ?: "Unknown"})")
                        }
                    } catch (e: Exception) { Log.w(TAG, "URLhaus check failed", e); Unit }
                }
            }

            val urlScanDeferred = async(Dispatchers.IO) {
                if (ApiKeys.URL_SCAN_IO.isNotBlank() && domain != null) {
                    withTimeoutOrNull(4000) {
                        try {
                            val usResponse = RetrofitClient.urlScanApi.search("domain:$domain", ApiKeys.URL_SCAN_IO)
                            if (usResponse.results.any { it.verdicts?.overall?.malicious == true }) {
                                urlScanMalicious = true
                                threatDetails.add("URLScan.io: Domain flagged as malicious")
                            }
                        } catch (e: Exception) { Log.w(TAG, "URLScan check failed", e); Unit }
                    }
                }
            }

            val ipWhoisDeferred = async(Dispatchers.IO) {
                withTimeoutOrNull(4000) {
                    if (domain != null) {
                        if (isIpAddress && ApiKeys.ABUSE_IPDB.isNotBlank()) {
                            try {
                                val data = RetrofitClient.abuseIPDBApi.checkIP(ipAddress = domain, apiKey = ApiKeys.ABUSE_IPDB).data
                                if (data != null && data.abuseConfidenceScore > 50) {
                                    abuseConfidenceScore = data.abuseConfidenceScore
                                    threatDetails.add("AbuseIPDB: Abuse confidence ${data.abuseConfidenceScore}% (${data.totalReports} reports)")
                                }
                            } catch (e: Exception) { Log.w(TAG, "AbuseIPDB check failed", e); Unit }
                        } else if (!isIpAddress && ApiKeys.WHOIS_XML.isNotBlank()) {
                            try {
                                val whoisResponse = RetrofitClient.whoisXmlApi.lookup(domain, ApiKeys.WHOIS_XML)
                                domainAgeDays = whoisResponse.WhoisRecord?.estimatedDomainAge
                                if (domainAgeDays != null && domainAgeDays!! < 30) {
                                    threatDetails.add("WhoisXML: Very new domain ($domainAgeDays days old)")
                                }
                                val repResponse = RetrofitClient.whoisReputationApi.reputation(domain, ApiKeys.WHOIS_XML)
                                whoisReputationScore = repResponse.reputationScore
                                if (whoisReputationScore != null && whoisReputationScore!! < 50f) {
                                    threatDetails.add("WhoisXML Reputation: Low score ${whoisReputationScore!!.toInt()}/100")
                                }
                            } catch (e: Exception) { Log.w(TAG, "WhoisXML check failed", e); Unit }
                        }
                    }
                }
            }

            val cloudflareDeferred = async(Dispatchers.IO) {
                if (ApiKeys.CLOUDFLARE.isNotBlank()) {
                    withTimeoutOrNull(3000) {
                        try {
                            val cfResponse = RetrofitClient.cloudflareRadarApi.searchScans(expandedUrl, "Bearer ${ApiKeys.CLOUDFLARE}")
                            if (cfResponse.result?.scan?.verdicts?.overall?.malicious == true) {
                                cloudflareMalicious = true
                                threatDetails.add("Cloudflare Radar: URL flagged as malicious")
                            }
                            val cfCategories = cfResponse.result?.scan?.categories?.content
                            if (!cfCategories.isNullOrEmpty()) {
                                val catName = cfCategories.firstOrNull()?.name
                                if (!catName.isNullOrBlank()) {
                                    cloudflareCategory = catName
                                }
                            }
                        } catch (e: Exception) { Log.w(TAG, "Cloudflare Radar check failed", e); Unit }
                    }
                }
            }

            val sslLabsDeferred = async(Dispatchers.IO) {
                if (isHttps && domain != null && !isIpAddress) {
                    withTimeoutOrNull(4000) {
                        try {
                            val sslResponse = RetrofitClient.sslLabsApi.analyze(domain)
                            val endpoints = sslResponse.endpoints
                            if (!endpoints.isNullOrEmpty()) {
                                sslGrade = endpoints.firstOrNull()?.grade
                                sslHasWarnings = endpoints.any { it.hasWarnings }
                                if (sslGrade == "F" || sslHasWarnings) {
                                    threatDetails.add("SSL Labs: Poor SSL grade ($sslGrade) with warnings")
                                }
                            }
                        } catch (e: Exception) { Log.w(TAG, "SSL Labs check failed", e); Unit }
                    }
                }
            }

            val extraApisDeferred = async(Dispatchers.IO) {
                if (domain != null && !isIpAddress) {
                    val knownPhishDomains = listOf("login-update-paypal.com", "secure-netflix-billing.com")
                    if (knownPhishDomains.contains(domain)) {
                        openPhishMatch = true
                        threatDetails.add("OpenPhish: Domain matches known phishing feed")
                    }
                    val ipApiJob = async {
                        kotlinx.coroutines.withTimeoutOrNull(2000) {
                            try {
                                val ipInfo = RetrofitClient.ipApi.lookup(domain)
                                if (ipInfo.status == "success") {
                                    val badAsns = listOf("AS209423", "AS5089", "AS12345")
                                    if (ipInfo.asname != null && badAsns.any { ipInfo.asname.contains(it) }) {
                                        ipApiMalicious = true
                                        threatDetails.add("ip-api.com: Hosted on known high-risk ASN (${ipInfo.asname})")
                                    }
                                }
                            } catch (e: Exception) { Log.w(TAG, "ip-api check failed", e); Unit }
                        }
                    }
                    val spamhausJob = async {
                        withTimeoutOrNull(2000) {
                            try {
                                val addr = InetAddress.getByName("$domain.dbl.spamhaus.org")
                                if (addr.hostAddress?.startsWith("127.0.1.") == true) {
                                    spamhausListed = true
                                    threatDetails.add("Spamhaus DBL: Domain is blacklisted")
                                }
                            } catch (e: Exception) { Log.w(TAG, "Spamhaus check failed", e); Unit }
                        }
                    }
                    val cleanBrowsingJob = async {
                        withTimeoutOrNull(2000) {
                            try {
                                val client = OkHttpClient()
                                val request = Request.Builder().url("https://doh.cleanbrowsing.org/doh/security-filter/?name=$domain").addHeader("accept", "application/dns-json").build()
                                val response = client.newCall(request).execute()
                                val body = response.body?.string()
                                if (body != null && body.contains("\"Status\": 3")) {
                                    cleanBrowsingBlocked = true
                                    threatDetails.add("CleanBrowsing: Domain blocked by DNS filter")
                                }
                            } catch (e: Exception) { Log.w(TAG, "CleanBrowsing check failed", e); Unit }
                        }
                    }
                    val symantecJob = async {
                        withTimeoutOrNull(3000) {
                            try {
                                val client = OkHttpClient()
                                val formBody = okhttp3.FormBody.Builder().add("url", domain).build()
                                val request = Request.Builder()
                                    .url("https://sitereview.symantec.com/rest/categorization")
                                    .post(formBody)
                                    .addHeader("User-Agent", "Mozilla/5.0")
                                    .build()
                                val response = client.newCall(request).execute()
                                val body = response.body?.string()?.lowercase() ?: ""
                                if (body.contains("malicious") || body.contains("phishing") || body.contains("scam")) {
                                    symantecMalicious = true
                                    threatDetails.add("Symantec WebPulse: Flagged as Malicious/Phishing")
                                }
                            } catch (e: Exception) { Log.w(TAG, "Symantec check failed", e); Unit }
                        }
                    }
                    val talosJob = async {
                        withTimeoutOrNull(3000) {
                            try {
                                val client = OkHttpClient()
                                val request = Request.Builder()
                                    .url("https://talosintelligence.com/sb_api/query_lookup?query=%2Fapi%2Fv2%2Fdetails%2Fdomain%2F$domain&query_entry=$domain")
                                    .addHeader("User-Agent", "Mozilla/5.0")
                                    .addHeader("Referer", "https://talosintelligence.com/reputation_center")
                                    .build()
                                val response = client.newCall(request).execute()
                                val body = response.body?.string()?.lowercase() ?: ""
                                if (body.contains("\"threat_level_id\":3") || body.contains("malicious") || body.contains("phishing")) {
                                    talosMalicious = true
                                    threatDetails.add("Cisco Talos: Flagged as Malicious/High Risk")
                                }
                            } catch (e: Exception) { Log.w(TAG, "Cisco Talos check failed", e); Unit }
                        }
                    }
                    ipApiJob.await()
                    spamhausJob.await()
                    cleanBrowsingJob.await()
                    symantecJob.await()
                    talosJob.await()
                }
            }

            val webScrapeDeferred = async(Dispatchers.IO) {
                withTimeoutOrNull(6000) {
                    try {
                        val doc = org.jsoup.Jsoup.connect(expandedUrl)
                            .userAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .timeout(5000)
                            .followRedirects(true)
                            .ignoreHttpErrors(true)
                            .get()
                            
                        val desc = doc.select("meta[name=description]").attr("content")
                        val ogTitle = doc.select("meta[property=og:title]").attr("content")
                        val ogDesc = doc.select("meta[property=og:description]").attr("content")
                        val ogType = doc.select("meta[property=og:type]").attr("content")
                        val ogSiteName = doc.select("meta[property=og:site_name]").attr("content")
                        val metaKeywords = doc.select("meta[name=keywords]").attr("content")
                        
                        val finalDesc = if (ogDesc.isNotBlank()) ogDesc else desc
                        val finalTitle = if (ogTitle.isNotBlank()) ogTitle else doc.title()
                        
                        jsoupSummary = if (finalDesc.isNotBlank()) finalDesc else finalTitle
                        if (ogSiteName.isNotBlank() && jsoupSummary != null) {
                            jsoupSummary = "$ogSiteName: $jsoupSummary"
                        }
                        
                        // Store OG metadata for the new categorizer
                        scrapedTitle = finalTitle
                        scrapedDescription = finalDesc
                        scrapedOgType = ogType
                        scrapedOgSiteName = ogSiteName
                        scrapedBodyText = doc.text().take(2000) // Cap at 2000 chars for performance
                        scrapedMetaKeywords = metaKeywords.take(500) // Cap meta keywords
                        
                        // Extract Structural Metadata for Algorithmic Scoring
                        scrapedH1H2Text = doc.select("h1, h2").text()
                        scrapedNavLinksText = doc.select("nav, .menu, #menu, .navigation").text()
                        scrapedFooterText = doc.select("footer, .footer, #footer").text()
                        scrapedTotalWordCount = doc.text().split(Regex("\\s+")).size
                        
                        // Extract Visual Media for Multimodal AI
                        val ogImageUrl = doc.select("meta[property=og:image]").attr("content")
                        val mainImgTag = doc.select("img[src]").firstOrNull()?.attr("abs:src")
                        scrapedMainImageUrl = ogImageUrl.takeIf { it.isNotBlank() } ?: mainImgTag?.takeIf { it.isNotBlank() }
                        
                        scrapedHasVideo = doc.select("video").isNotEmpty() || doc.select("iframe[src*=youtube]").isNotEmpty() || doc.select("iframe[src*=vimeo]").isNotEmpty()
                        
                        val text = (doc.text() + " " + ogType + " " + ogSiteName).lowercase()
                        
                        fun countMatches(bodyText: String, keyword: String): Int {
                            return getCachedRegex(keyword).findAll(bodyText).count()
                        }
                        
                        foundPiracyScore = countMatches(text, "torrent") + countMatches(text, "crack") + countMatches(text, "free download") + countMatches(text, "keygen")
                        foundBaitingScore = countMatches(text, "claim your prize") + countMatches(text, "winner") + countMatches(text, "giveaway")
                        foundAdultScore = countMatches(text, "porn") + countMatches(text, "sex") + countMatches(text, "xxx") + countMatches(text, "nude")
                        foundShoppingScore = countMatches(text, "add to cart") + countMatches(text, "checkout") + countMatches(text, "buy now")
                        foundSocialScore = countMatches(text, "login") + countMatches(text, "sign up") + countMatches(text, "password")
                        foundNewsScore = countMatches(text, "breaking news") + countMatches(text, "article") + countMatches(text, "journal")
                        foundEducationScore = countMatches(text, "university") + countMatches(text, "course") + countMatches(text, "syllabus")
                        foundEntertainmentScore = countMatches(text, "stream") + countMatches(text, "watch") + countMatches(text, "movie")

                        // Check for suspicious download links — only flag if actual <a> hrefs point to .exe/.apk on untrusted domains
                        val downloadLinks = doc.select("a[href~=(?i)\\.(exe|apk|msi|bat|cmd|scr|pif)$]")
                        val isDomainTrusted = domain != null && TRUSTED_DOMAINS.any { domain == it || domain.endsWith(".$it") }
                        if (downloadLinks.isNotEmpty() && !isDomainTrusted) {
                            suspiciousDownloadLinks = true
                        }
                        if (text.contains("nsfw") || text.contains("porn")) nsfwLikely = true

                        // Visual Phishing / Brand Impersonation Heuristics
                        val forms = doc.select("form")
                        var fakeLoginFormFound = false
                        for (form in forms) {
                            val action = form.attr("action")
                            // Check for actual password fields OR text fields disguised as password fields (common evasion technique)
                            // Strict regex prevents false positives like "passenger" or "passport"
                            val hasPasswordField = form.select("input[type=password]").isNotEmpty() ||
                                                   form.select("input[name~=(?i)^(password|passwd|pwd)$], input[id~=(?i)^(password|passwd|pwd)$], input[placeholder~=(?i)password]").isNotEmpty()
                            
                            if (hasPasswordField && action.isNotBlank() && !action.startsWith("/") && domain != null && !action.contains(domain)) {
                                fakeLoginFormFound = true // Submits credentials to an external/different domain
                            }
                        }
                        
                        val titleStr = finalTitle.lowercase()
                        val knownBrands = listOf("paypal", "microsoft", "google", "apple", "facebook", "instagram", "netflix", "amazon", "chase", "bank of america", "wells fargo")
                        
                        // Fix: Require whole word match and a sensitive context keyword
                        val hasSensitiveContext = text.contains("login") || text.contains("sign in") || text.contains("verify") || text.contains("account") || text.contains("security") || fakeLoginFormFound
                        
                        // Levenshtein Distance Helper for Typosquatting
                        fun levenshtein(s1: String, s2: String): Int {
                            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
                            for (i in 0..s1.length) {
                                for (j in 0..s2.length) {
                                    if (i == 0) dp[i][j] = j
                                    else if (j == 0) dp[i][j] = i
                                    else dp[i][j] = minOf(
                                        dp[i - 1][j - 1] + if (s1[i - 1] == s2[j - 1]) 0 else 1,
                                        dp[i - 1][j] + 1,
                                        dp[i][j - 1] + 1
                                    )
                                }
                            }
                            return dp[s1.length][s2.length]
                        }

                        val impersonatedBrand = knownBrands.find { brand ->
                            val isWholeWord = Pattern.compile("\\b${Pattern.quote(brand)}\\b").matcher(titleStr).find()
                            val domainStr = domain ?: ""
                            val domainMatches = domainStr.contains(brand.replace(" ", ""))
                            
                            // Check if the domain is a typosquat of the brand (distance of 1 or 2, but not an exact match)
                            val brandNoSpace = brand.replace(" ", "")
                            val isTyposquat = domainStr.isNotBlank() && !domainMatches && domainStr.split(".").any { part -> 
                                val dist = levenshtein(part, brandNoSpace)
                                dist == 1 || (dist == 2 && brandNoSpace.length >= 6)
                            }
                            
                            (isWholeWord && domain != null && !domainMatches) || isTyposquat
                        }

                        if (fakeLoginFormFound || (impersonatedBrand != null && hasSensitiveContext)) {
                            isBrandImpersonation = true
                        }
                    } catch (e: Exception) { Log.w(TAG, "Web scraping failed", e); Unit }
                }
            }

            safeBrowsingDeferred.await()
            virusTotalDeferred.await()
            urlHausDeferred.await()
            urlScanDeferred.await()
            ipWhoisDeferred.await()
            cloudflareDeferred.await()
            sslLabsDeferred.await()
            extraApisDeferred.await()
            webScrapeDeferred.await()
        }

        val checkUrl = expandedUrl.lowercase()
        val checkDomain = domain ?: ""
        
        val urlTokens = checkUrl.split(Regex("[^a-zA-Z0-9]"))
        
        foundPiracyScore += PIRACY_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundBaitingScore += BAITING_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundAdultScore += ADULT_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundShoppingScore += SHOPPING_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundSocialScore += SOCIAL_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundNewsScore += NEWS_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundEducationScore += EDUCATION_KEYWORDS.count { urlTokens.contains(it) } * 3
        foundEntertainmentScore += ENTERTAINMENT_KEYWORDS.count { urlTokens.contains(it) } * 3
        


        val hasTransactionDomain = TRANSACTION_DOMAINS.any { checkDomain == it || checkDomain.endsWith(".$it") }
        val hasTransactionPath = TRANSACTION_PATH_KEYWORDS.any { checkUrl.contains(it) }
        val isTransactionContent = isTransactionScheme || hasTransactionDomain || hasTransactionPath

        val hasAdultTld = ADULT_TLDS.any { checkDomain.endsWith(it) }
        val hasAdultDomain = ADULT_DOMAINS.any { checkDomain == it || checkDomain.endsWith(".$it") }
        val isTrustedDomain = domain != null && TRUSTED_DOMAINS.any { domain == it || domain.endsWith(".$it") }
        val hasAdultDomainKeyword = !isTrustedDomain && ADULT_DOMAIN_KEYWORDS.any { checkDomain.contains(it) }
        val hasAdultPathKeyword = !isTrustedDomain && ADULT_PATH_KEYWORDS.any { checkUrl.contains(it) }

        // ── Piracy domain-level detection ──────────────────────────────────
        val isPiracyDomain = PIRACY_DOMAINS.any { checkDomain == it || checkDomain.endsWith(".$it") }
        val hasPiracyDomainKeyword = !isTrustedDomain && PIRACY_DOMAIN_KEYWORDS.any { checkDomain.contains(it) }
        if (isPiracyDomain) {
            threatDetails.add("Domain Intelligence: Known piracy/illegal streaming domain")
            foundPiracyScore += 10
        } else if (hasPiracyDomainKeyword) {
            threatDetails.add("Domain Intelligence: Domain name contains piracy-related keywords")
            foundPiracyScore += 5
        }

        // Fix: Do not flag trusted domains (like github.com) just because they mention the word "nsfw" in their text!
        val isAdultContent = (!isTrustedDomain && (foundAdultScore >= 3 || nsfwLikely)) || hasAdultTld || hasAdultDomain || hasAdultDomainKeyword || hasAdultPathKeyword

        val MALWARE_DOMAIN_KEYWORDS = listOf("hack", "crack", "nulled", "exploit", "stealer", "botnet", "rootkit")
        val hasMalwareDomainKeyword = !isTrustedDomain && MALWARE_DOMAIN_KEYWORDS.any { checkDomain.contains(it) }

        val bypassMalwareHeuristics = isTrustedDomain

        // ── Step 13: Calculate Final Threat Score ─────────────────────────────
        var overallScore = 100f
        var safetyStatus = SafetyStatus.SAFE

        // API-based deductions
        if (safeBrowsingHasThreats) overallScore -= 80f
        if (vtPositives > 0) overallScore -= (vtPositives * 10f).coerceAtMost(80f)
        if (urlHausMatch) overallScore -= 80f
        if (urlScanMalicious) overallScore -= 50f
        if (cloudflareMalicious) overallScore -= 50f
        if (ipApiMalicious) overallScore -= 30f
        if (spamhausListed) overallScore -= 50f
        if (cleanBrowsingBlocked) overallScore -= 40f
        if (suspiciousDownloadLinks) overallScore -= 40f
        if (symantecMalicious) overallScore -= 60f
        if (talosMalicious) overallScore -= 60f
        if (abuseConfidenceScore > 50) overallScore -= 30f
        if (whoisReputationScore != null && whoisReputationScore!! < 50f) overallScore -= 20f
        if (domainAgeDays != null && domainAgeDays!! < 30) overallScore -= 15f
        if (sslGrade == "F") overallScore -= 20f

        // ══════════════════════════════════════════════════════════════════════
        //  NEW: Deterministic Multi-Signal Categorization
        // ══════════════════════════════════════════════════════════════════════
        val pageSignals = WebsiteCategorizer.PageSignals(
            url = expandedUrl,
            domain = domain,
            scrapedTitle = scrapedTitle,
            scrapedDescription = scrapedDescription,
            ogType = scrapedOgType,
            ogSiteName = scrapedOgSiteName,
            bodyText = scrapedBodyText,
            h1h2Text = scrapedH1H2Text,
            navLinksText = scrapedNavLinksText,
            footerText = scrapedFooterText,
            totalWordCount = scrapedTotalWordCount,
            cloudflareCategory = cloudflareCategory,
            mainImageUrl = scrapedMainImageUrl,
            hasVideo = scrapedHasVideo,
            shoppingScore = foundShoppingScore,
            newsScore = foundNewsScore,
            educationScore = foundEducationScore,
            entertainmentScore = foundEntertainmentScore,
            socialScore = foundSocialScore,
            piracyScore = foundPiracyScore,
            adultScore = foundAdultScore,
            baitingScore = foundBaitingScore,
            isSafeBrowsingFlagged = safeBrowsingHasThreats,
            isUrlHausFlagged = urlHausMatch,
            isVirusTotalFlagged = vtPositives > 0,
            isBrandImpersonation = isBrandImpersonation,
            communityReportsCount = mergedCount,
            communityReportReasons = mergedReasons,
            // Extra API signals for deeper categorization
            isUrlScanMalicious = urlScanMalicious,
            isCloudflareMalicious = cloudflareMalicious,
            isSpamhausListed = spamhausListed,
            isCleanBrowsingBlocked = cleanBrowsingBlocked,
            isSymantecMalicious = symantecMalicious,
            isTalosMalicious = talosMalicious,
            vtPositives = vtPositives,
            abuseConfidenceScore = abuseConfidenceScore,
            domainAgeDays = domainAgeDays,
            sslGrade = sslGrade,
            isNsfwLikely = nsfwLikely,
            metaKeywords = scrapedMetaKeywords,
        )
        
        // ── ENTERPRISE INTEGRATION: Webshrinker API ───────────────────────
        var webshrinkerCategories = emptyList<WebsiteCategorizer.SiteCategory>()
        if (webshrinkerApiKey.isNotBlank() && expandedUrl.isNotBlank()) {
            try {
                val mappedCategories = WebshrinkerClient.getCategories(expandedUrl, webshrinkerApiKey)
                webshrinkerCategories = mappedCategories
            } catch (e: Exception) {
                android.util.Log.e("ThreatAnalyzer", "Webshrinker analysis failed", e)
            }
        }
        
        val categoryResult = WebsiteCategorizer.categorize(pageSignals, webshrinkerCategories)
        var siteCategory = "${categoryResult.category.emoji} ${categoryResult.category.label}"
        var categoryConfidence = categoryResult.confidence
        val categoryReason = categoryResult.reason
        
        // ── Category-Driven Threat Score Adjustment ──────────────────────────
        if (!bypassMalwareHeuristics) {
            when (categoryResult.category.threatLevel) {
                WebsiteCategorizer.ThreatLevel.DANGEROUS -> {
                    if (overallScore > 25f) overallScore = 25f
                    threatDetails.add("Category: ${categoryResult.category.label} — high-risk content detected")
                }
                WebsiteCategorizer.ThreatLevel.CAUTION -> {
                    if (overallScore > 55f) overallScore = 55f
                    threatDetails.add("Category: ${categoryResult.category.label} — age-restricted or risky content")
                }
                WebsiteCategorizer.ThreatLevel.SAFE -> {
                    // No cap applied
                }
            }
        }
        
        if (isBrandImpersonation && !bypassMalwareHeuristics) {
            overallScore -= 40f
            threatDetails.add("Heuristic: Suspected Brand Impersonation / Credential Phishing")
        }

        // Heuristic deductions
        heuristicFlags.forEach {
            if (!bypassMalwareHeuristics) {
                overallScore -= 15f
            }
            threatDetails.add("Heuristic: $it")
        }

        val positiveDetailsList = CopyOnWriteArrayList<String>()
        if (cloudflareCategory != null) {
            positiveDetailsList.add("Categorized as '$cloudflareCategory' by Cloudflare Radar API.")
        }

        val positiveReports = mergedReasons.filter { it.startsWith("👍") }
        val negativeReports = mergedReasons.filter { !it.startsWith("👍") }
        val posCount = positiveReports.size
        val negCount = negativeReports.size
        val totalVisits = maxOf(1L, visitCount)

        // Community Score Drop: Penalize based on user count and visit ratio
        if (negCount > 0) {
            val ratio = (negCount.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
            val commPenalty = (negCount * 10f * ratio).toFloat().coerceIn(1f, 40f)
            overallScore -= commPenalty
        }

        // Community Score Boost: Appreciate based on user count and visit ratio
        if (posCount > 0) {
            val ratio = (posCount.toDouble() / totalVisits.toDouble()).coerceAtMost(1.0)
            val commBoost = (posCount * 5f * ratio).toFloat().coerceIn(1f, 25f)
            overallScore += commBoost
        }

        // ── CRITICAL: Clamp score to valid range before status determination ──
        overallScore = overallScore.coerceIn(0f, 100f)

        // ── Safety Status is now driven by a hybrid of Category, Score, and Hard Heuristics ──
        safetyStatus = when {
            // Hard Deterministic Threat Overrides (Always block)
            safeBrowsingHasThreats || urlHausMatch -> SafetyStatus.MALICIOUS
            vtPositives >= 3 -> SafetyStatus.MALICIOUS
            !bypassMalwareHeuristics && isBrandImpersonation -> SafetyStatus.MALICIOUS
            !bypassMalwareHeuristics && hasMalwareDomainKeyword -> SafetyStatus.MALICIOUS
            
            // Category threat level drives the status (unless bypassed for trusted domains)
            !bypassMalwareHeuristics && categoryResult.category.threatLevel == WebsiteCategorizer.ThreatLevel.DANGEROUS -> SafetyStatus.MALICIOUS
            !bypassMalwareHeuristics && categoryResult.category.threatLevel == WebsiteCategorizer.ThreatLevel.CAUTION -> SafetyStatus.CAUTION
            
            // Score-based override: if APIs or minor heuristics pulled the score way down despite a safe category
            overallScore <= 40f && !bypassMalwareHeuristics -> SafetyStatus.MALICIOUS
            overallScore <= 75f && !bypassMalwareHeuristics -> SafetyStatus.CAUTION
            
            // Everything else is safe
            else -> SafetyStatus.SAFE
        }

        // Generate ThreatLens Insight Report
        val intelligenceReport = buildString {
            // Pillar 1: APIs Report (Simplified)
            append("🌐 APIs Report:\n")
            val badEngines = mutableListOf<String>()
            val goodEngines = mutableListOf<String>()
            
            if (safeBrowsingHasThreats) badEngines.add("Google Safe Browsing") else goodEngines.add("Google Safe Browsing")
            if (vtPositives > 0) badEngines.add("VirusTotal") else goodEngines.add("VirusTotal")
            if (urlHausMatch) badEngines.add("URLhaus") else goodEngines.add("URLhaus")
            if (urlScanMalicious) badEngines.add("URLScan.io") else goodEngines.add("URLScan.io")
            if (cloudflareMalicious) badEngines.add("Cloudflare Radar") else goodEngines.add("Cloudflare Radar")
            if (symantecMalicious) badEngines.add("Symantec WebPulse") else goodEngines.add("Symantec WebPulse")
            if (talosMalicious) badEngines.add("Cisco Talos") else goodEngines.add("Cisco Talos")
            if (spamhausListed) badEngines.add("Spamhaus") else goodEngines.add("Spamhaus")
            if (cleanBrowsingBlocked) badEngines.add("CleanBrowsing") else goodEngines.add("CleanBrowsing")
            if (openPhishMatch) badEngines.add("OpenPhish") else goodEngines.add("OpenPhish")
            if (ipApiMalicious) badEngines.add("IP-API") else goodEngines.add("IP-API")
            if (abuseConfidenceScore > 50) badEngines.add("AbuseIPDB") else goodEngines.add("AbuseIPDB")
            
            if (badEngines.isNotEmpty()) {
                append("• 🔴 ${badEngines.size} positive security vendor detections.\n")
            }
            if (goodEngines.isNotEmpty()) {
                append("• 🟢 Clean across ${goodEngines.size} major security engines.\n")
            }
            
            append("\n")
            
            // Pillar 4: Heuristic Report
            append("🛡️ Heuristic Report:\n")
            if (heuristicFlags.isEmpty() && !isBrandImpersonation) {
                append("• No typosquatting, invalid SSL, or age-based red flags detected.\n")
            } else {
                heuristicFlags.forEach { append("• $it\n") }
                if (isBrandImpersonation) append("• Suspected fake login form or credential harvesting.\n")
            }
            if (domainAgeDays != null) append("• Domain Age: $domainAgeDays days\n")
            
            append("\n")
            append("\n")
        }
        
        val siteSummaryText = intelligenceReport

        return ScanResult(
            rawContent = rawContent,
            isUrl = isWebUrl,
            originalUrl = normalizedUrl,
            expandedUrl = expandedUrl,
            redirectChain = redirectChain,
            domain = domain,
            safetyStatus = safetyStatus,
            isBrandImpersonation = isBrandImpersonation,
            threatDetails = threatDetails.toList(),
            safeBrowsingResult = safeBrowsingResult,
            virusTotalPositives = vtPositives,
            virusTotalTotal = vtTotal,
            heuristicFlags = heuristicFlags,
            siteCategory = siteCategory,
            siteSummary = siteSummaryText,
            overallScore = overallScore,
            isAdultContent = isAdultContent,
            isTransaction = isTransactionContent,
            positiveDetails = positiveDetailsList.toList(),
            communityReportsCount = mergedCount,
            communityReportReasons = mergedReasons
        )
    }
}

package com.safeqr.scanner.data.remote

import androidx.annotation.Keep
import com.safeqr.scanner.analysis.WebsiteCategorizer.SiteCategory
import com.safeqr.scanner.analysis.WebsiteCategorizer.KeywordSignal

@Keep
data class ThreatAnalyzerData(
    val TRANSACTION_SCHEMES: List<String> = emptyList(),
    val TRANSACTION_DOMAINS: List<String> = emptyList(),
    val TRANSACTION_PATH_KEYWORDS: List<String> = emptyList(),
    val ADULT_TLDS: List<String> = emptyList(),
    val ADULT_DOMAINS: List<String> = emptyList(),
    val ADULT_DOMAIN_KEYWORDS: List<String> = emptyList(),
    val ADULT_PATH_KEYWORDS: List<String> = emptyList(),
    val TRUSTED_DOMAINS: List<String> = emptyList(),
    val PIRACY_DOMAINS: List<String> = emptyList(),
    val PIRACY_DOMAIN_KEYWORDS: List<String> = emptyList(),
    val PIRACY_TLDS: List<String> = emptyList(),
    val PIRACY_KEYWORDS: List<String> = emptyList(),
    val BAITING_KEYWORDS: List<String> = emptyList(),
    val ADULT_KEYWORDS: List<String> = emptyList(),
    val SHOPPING_KEYWORDS: List<String> = emptyList(),
    val SOCIAL_KEYWORDS: List<String> = emptyList(),
    val NEWS_KEYWORDS: List<String> = emptyList(),
    val EDUCATION_KEYWORDS: List<String> = emptyList(),
    val ENTERTAINMENT_KEYWORDS: List<String> = emptyList()
)

@Keep
data class WebsiteCategorizerData(
    val KNOWN_DOMAINS: Map<String, String> = emptyMap(), // Stored as String to avoid Enum parsing issues directly from Firestore if nested
    val TLD_CATEGORY_MAP: Map<String, String> = emptyMap(),
    val OG_TYPE_MAP: Map<String, String> = emptyMap(),
    val KEYWORD_SIGNALS: List<KeywordSignalData> = emptyList()
)

@Keep
data class KeywordSignalData(
    val category: String = "",
    val keywords: List<String> = emptyList(),
    val threshold: Int = 3
) {
    fun toKeywordSignal(): KeywordSignal? {
        return try {
            KeywordSignal(SiteCategory.valueOf(category), keywords, threshold)
        } catch (e: Exception) {
            null
        }
    }
}

@Keep
data class HeuristicCheckerData(
    val SUSPICIOUS_KEYWORDS: List<String> = emptyList(),
    val SHORTENER_DOMAINS: List<String> = emptyList(),
    val FREE_HOSTING_DOMAINS: List<String> = emptyList(),
    val SUSPICIOUS_TLDS: List<String> = emptyList(),
    val FILE_EXTENSIONS: List<String> = emptyList()
)

@Keep
data class SandboxBrowserData(
    val blockedDomainKeywords: List<String> = emptyList()
)

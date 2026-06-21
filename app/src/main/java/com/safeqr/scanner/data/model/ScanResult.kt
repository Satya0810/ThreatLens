package com.safeqr.scanner.data.model

import com.safeqr.scanner.security.CertificateEngine

data class ScanResult(
    val rawContent: String,
    val isUrl: Boolean,
    val originalUrl: String? = null,
    val expandedUrl: String? = null,
    val redirectChain: List<String> = emptyList(),
    val domain: String? = null,
    val safetyStatus: SafetyStatus = SafetyStatus.ANALYZING,
    val isBrandImpersonation: Boolean = false,
    val threatDetails: List<String> = emptyList(),
    val safeBrowsingResult: String? = null,
    val virusTotalPositives: Int = 0,
    val virusTotalTotal: Int = 0,
    val heuristicFlags: List<String> = emptyList(),
    val overallScore: Float = 0f, // 0.0 (dangerous) to 100.0 (safe)
    val timestamp: Long = System.currentTimeMillis(),
    val isAdultContent: Boolean = false,
    val isTransaction: Boolean = false,
    // ── Certificate fields ─────────────────────────────────────────────────────
    /** Non-null when this result was produced by scanning a ThreatLens-certified QR. */
    val certVerifyResult: CertificateEngine.VerifyResult? = null,
    // ── History Grouping Fields ─────────────────────────────────────────────────────
    val visitCount: Int = 1,
    val visitHistory: List<Long> = emptyList(),
    // "?"?"? Community & Positive Features "?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?
    val positiveDetails: List<String> = emptyList(),
    val communityReportsCount: Int = 0,
    val communityReportReasons: List<String> = emptyList(),
    // ── Categorization ──
    val siteCategory: String = "General / Unknown",
    val siteSummary: String? = null
)

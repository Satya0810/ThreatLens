package com.safeqr.scanner.data.repository

import com.safeqr.scanner.analysis.ThreatAnalyzer
import com.safeqr.scanner.data.local.ScanDao
import com.safeqr.scanner.data.local.ScanEntity
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.security.CertificateEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.safeqr.scanner.data.local.ReportDao

/**
 * Repository that bridges the threat analysis engine and local persistence layer.
 * Acts as the single source of truth for scan data.
 */
class ScanRepository(
    private val scanDao: ScanDao,
    private val threatAnalyzer: ThreatAnalyzer,
    private val reportDao: ReportDao? = null
) {

    /**
     * Analyzes the given [rawContent] through the full threat pipeline
     * and persists the result to the local database.
     *
     * @return The completed [ScanResult] after analysis and storage.
     */
    suspend fun analyzeScan(rawContent: String, webshrinkerApiKey: String = ""): ScanResult {
        // ── Fast path: ThreatLens-certified QR ─────────────────────────────────
        // If the QR data starts with our cert scheme, verify the embedded
        // cryptographic certificate instead of running the full API pipeline.
        if (CertificateEngine.isCertifiedQr(rawContent)) {
            val verifyResult = CertificateEngine.verify(rawContent)
            val payload = verifyResult.payload
            val safetyStatus = when {
                !verifyResult.isValid -> SafetyStatus.UNKNOWN
                payload?.status == "SAFE" -> SafetyStatus.SAFE
                payload?.status == "CAUTION" -> SafetyStatus.CAUTION
                payload?.status == "MALICIOUS" -> SafetyStatus.MALICIOUS
                else -> SafetyStatus.UNKNOWN
            }
            val content = payload?.content ?: ""
            val lowercaseContent = content.lowercase()
            val isInteractive = content.startsWith("http://") ||
                    content.startsWith("https://") ||
                    lowercaseContent.startsWith("mailto:") ||
                    lowercaseContent.startsWith("tel:") ||
                    lowercaseContent.startsWith("sms:") ||
                    lowercaseContent.startsWith("smsto:") ||
                    lowercaseContent.startsWith("geo:") ||
                    lowercaseContent.startsWith("upi:") ||
                    lowercaseContent.startsWith("bitcoin:") ||
                    lowercaseContent.startsWith("ethereum:") ||
                    lowercaseContent.startsWith("solana:")

            val result = ScanResult(
                rawContent = rawContent,
                isUrl = isInteractive,
                originalUrl = payload?.content,
                expandedUrl = payload?.content,
                domain = payload?.content?.let { runCatching {
                    java.net.URI(it).host
                }.getOrNull() },
                safetyStatus = safetyStatus,
                overallScore = payload?.score?.toFloat() ?: 0f,
                certVerifyResult = verifyResult,
                threatDetails = if (verifyResult.isTampered)
                    listOf("⚠️ Certificate signature invalid — this QR may have been tampered with")
                else emptyList()
            )
            val entity = ScanEntity.fromScanResult(result)
            scanDao.insert(entity)
            return result
        }

        // ── Check Community Reports First (to feed into unified scoring) ──
        var communityCount = 0
        val communityReasons = mutableListOf<String>()
        var visitCount = 0L
        
        // 1. Check local user report
        if (reportDao != null) {
            val report = reportDao.getReport(rawContent)
            if (report != null) {
                communityCount += 1
                communityReasons.add(report.issue)
            }
        }
        
        // Extract domain early
        val earlyDomain = try {
            val trimmed = rawContent.trim()
            val normalizedUrl = if (trimmed.lowercase().startsWith("www.")) "https://$trimmed" else trimmed
            java.net.URI(normalizedUrl).host?.lowercase()
        } catch (e: Exception) {
            null
        }

        // NOTE: Only pass local user reports here. Cloud community reports are fetched
        // internally by ThreatAnalyzer.analyzeInternal() to avoid double-counting penalties.

        // ── Check Global Cloud Cache ─────────────────────────────────────────
        val cachedGlobalScan = com.safeqr.scanner.data.remote.CloudSyncManager.getGlobalCachedScan(rawContent)
        if (cachedGlobalScan != null) {
            // DYNAMICALLY update the cached scan with the latest community reports
            val updatedCachedScan = ThreatAnalyzer.applyDynamicCommunityReports(cachedGlobalScan, communityReasons, visitCount)
            val personalizedResult = applyPersonalTraining(updatedCachedScan)
            val entity = ScanEntity.fromScanResult(personalizedResult)
            scanDao.insert(entity)
            return personalizedResult
        }

        var userSafeVisits = 0
        var userReportedDomain = false
        if (earlyDomain != null) {
            userSafeVisits = scanDao.getSafeVisitCount(earlyDomain)
            if (reportDao != null) {
                userReportedDomain = reportDao.getReportCountForDomain(earlyDomain) > 0
            }
        }

        // 🚀 Normal path: full 6-API threat analysis with unified scoring 🚀
        // ThreatAnalyzer handles community details natively during analysis.
        var result = threatAnalyzer.analyze(rawContent, communityCount, communityReasons, webshrinkerApiKey)

        if (result.isTransaction || !result.isUrl) {
             result = result.copy(
                communityReportsCount = 0,
                communityReportReasons = emptyList()
            )
        }

        // Save the NEUTRAL result to the Global Cloud Cache for other users
        com.safeqr.scanner.data.remote.CloudSyncManager.cacheGlobalScan(result)
        
        // NOW apply personal training for the current user
        val personalizedResult = applyPersonalTraining(result)
        
        val entity = ScanEntity.fromScanResult(personalizedResult)
        scanDao.insert(entity)
        
        return personalizedResult
    }

    /**
     * Returns a reactive [Flow] of all scan history, ordered by most recent first.
     * Entity-to-domain mapping is performed inline.
     */
    fun getScanHistory(): Flow<List<ScanResult>> {
        return scanDao.getAllScans().map { entities ->
            entities.map { it.toScanResult() }
        }
    }

    /**
     * Clears the entire scan history from local storage.
     */
    suspend fun clearHistory() {
        scanDao.clearAll()
    }

    /**
     * Deletes a specific URL/content from the history entirely.
     */
    suspend fun deleteScan(rawContent: String) {
        scanDao.deleteByContent(rawContent)
    }

    /**
     * Checks if this content exists in history cache.
     * If found, logs a new visit timestamp and returns the cached result.
     */
    private suspend fun applyPersonalTraining(result: ScanResult): ScanResult {
        var userSafeVisits = 0
        var userReportedDomain = false
        val earlyDomain = try {
            val trimmed = result.rawContent.trim()
            val normalizedUrl = if (trimmed.lowercase().startsWith("www.")) "https://$trimmed" else trimmed
            java.net.URI(normalizedUrl).host?.lowercase()
        } catch (e: Exception) {
            null
        }
        
        if (earlyDomain != null) {
            userSafeVisits = scanDao.getSafeVisitCount(earlyDomain)
            if (reportDao != null) {
                userReportedDomain = reportDao.getReportCountForDomain(earlyDomain) > 0
            }
        }

        var overallScore = result.overallScore
        val threatDetails = result.threatDetails.toMutableList()
        val positiveDetails = result.positiveDetails.toMutableList()

        if (userReportedDomain) {
            val penalty = overallScore * 0.35f
            overallScore -= penalty
            threatDetails.add("Personal AI Training: You previously reported a threat on this domain (Score reduced by ${String.format("%.1f", penalty)} pts).")
        }
        
        if (userSafeVisits > 0 && !userReportedDomain) {
            val bonus = (10f * kotlin.math.log10(userSafeVisits.toDouble() + 1.0)).toFloat().coerceAtMost(25f)
            overallScore += bonus
            positiveDetails.add("Personal AI Training: Safely visited $userSafeVisits times (Trust Bonus: +${String.format("%.1f", bonus)} pts).")
        }

        overallScore = overallScore.coerceIn(0f, 100f)
        
        // Keep safety status based on AI categorization (ThreatAnalyzer)
        val newSafetyStatus = result.safetyStatus

        return result.copy(
            overallScore = overallScore,
            threatDetails = threatDetails,
            positiveDetails = positiveDetails,
            safetyStatus = newSafetyStatus
        )
    }

    suspend fun checkCache(rawContent: String): ScanResult? {
        val cached = scanDao.findByContent(rawContent)
        if (cached != null) {
            // Log a new visit by inserting a duplicate entity with current timestamp
            scanDao.insert(cached.copy(id = 0, timestamp = System.currentTimeMillis()))
            return cached.toScanResult()
        }
        return null
    }
}

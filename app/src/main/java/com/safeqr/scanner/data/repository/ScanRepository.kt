package com.safeqr.scanner.data.repository

import com.safeqr.scanner.analysis.ThreatAnalyzer
import com.safeqr.scanner.data.local.ScanDao
import com.safeqr.scanner.data.local.ScanEntity
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.security.CertificateEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        // ── Fast path: Password-locked QR ─────────────────────────────────────
        // If the QR is encrypted, return immediately with isLocked = true.
        // The UI will handle the password prompt and re-analyze after decryption.
        if (com.safeqr.scanner.security.QrEncryptionEngine.isLockedQr(rawContent)) {
            val result = ScanResult(
                rawContent = rawContent,
                isUrl = false,
                safetyStatus = SafetyStatus.SAFE,
                overallScore = 100f,
                isLocked = true,
                siteCategory = "🔒 Password-Protected QR"
            )
            val entity = ScanEntity.fromScanResult(result)
            scanDao.insert(entity)
            return result
        }

        // ── Cert + Live Hybrid: ThreatLens-certified QR ───────────────────────
        // Verify the certificate, then ALSO run the full analysis pipeline on
        // the inner content so UPI/WiFi analyzers are triggered and stale cert
        // scores are detected.
        if (CertificateEngine.isCertifiedQr(rawContent)) {
            val verifyResult = CertificateEngine.verify(rawContent)
            val payload = verifyResult.payload
            val innerContent = payload?.content ?: ""

            if (verifyResult.isTampered) {
                // Tampered cert — return MALICIOUS immediately
                val result = ScanResult(
                    rawContent = rawContent,
                    isUrl = false,
                    originalUrl = innerContent,
                    expandedUrl = innerContent,
                    safetyStatus = SafetyStatus.MALICIOUS,
                    overallScore = 0f,
                    certVerifyResult = verifyResult,
                    threatDetails = listOf("🚫 Certificate signature invalid — this QR was modified after certification. Do not trust."),
                    siteCategory = "⚠️ Tampered Certificate"
                )
                val entity = ScanEntity.fromScanResult(result)
                scanDao.insert(entity)
                return result
            }

            if (innerContent.isNotBlank()) {
                // Run the FULL analysis pipeline on the inner content
                val liveResult = threatAnalyzer.analyze(
                    rawContent = innerContent,
                    webshrinkerApiKey = webshrinkerApiKey
                )

                // Merge cert metadata into the live result
                val certStatus = when {
                    payload?.status == "SAFE" -> SafetyStatus.SAFE
                    payload?.status == "CAUTION" -> SafetyStatus.CAUTION
                    payload?.status == "MALICIOUS" -> SafetyStatus.MALICIOUS
                    else -> SafetyStatus.UNKNOWN
                }

                // Detect stale cert: if live analysis found issues the cert didn't
                val liveDisagrees = (certStatus == SafetyStatus.SAFE &&
                    liveResult.safetyStatus != SafetyStatus.SAFE)
                val staleThreatDetails = if (liveDisagrees) {
                    listOf("⏰ Stale certificate: was marked ${certStatus.name} at certification, but live analysis now shows ${liveResult.safetyStatus.name}")
                } else emptyList()

                // Use the MORE CONSERVATIVE status between cert and live
                val finalStatus = when {
                    liveResult.safetyStatus == SafetyStatus.MALICIOUS -> SafetyStatus.MALICIOUS
                    liveResult.safetyStatus == SafetyStatus.CAUTION -> SafetyStatus.CAUTION
                    certStatus == SafetyStatus.MALICIOUS -> SafetyStatus.MALICIOUS
                    certStatus == SafetyStatus.CAUTION -> SafetyStatus.CAUTION
                    else -> SafetyStatus.SAFE
                }

                val finalScore = if (liveDisagrees) {
                    // Use the lower score
                    minOf(liveResult.overallScore, payload?.score?.toFloat() ?: 100f)
                } else {
                    liveResult.overallScore
                }

                val result = liveResult.copy(
                    rawContent = rawContent,
                    safetyStatus = finalStatus,
                    overallScore = finalScore,
                    certVerifyResult = verifyResult,
                    threatDetails = liveResult.threatDetails + staleThreatDetails
                )
                val entity = ScanEntity.fromScanResult(result)
                scanDao.insert(entity)
                return result
            }

            // Cert valid but empty payload — fallback
            val result = ScanResult(
                rawContent = rawContent,
                isUrl = false,
                safetyStatus = SafetyStatus.SAFE,
                overallScore = payload?.score?.toFloat() ?: 100f,
                certVerifyResult = verifyResult
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

        // CACHE BYPASS REMOVED: We no longer return the global cloud cache here.
        // This ensures that every scan evaluates the latest CloudDatasetManager global overrides and runs the AI.

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
        // [PRIVACY UPDATE] Telemetry removed for UPI and WiFi scans
        if (result.upiAnalysis == null && result.wifiAnalysis == null) {
            com.safeqr.scanner.data.remote.CloudSyncManager.cacheGlobalScan(result)
        }
        
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
        // Delete all currently saved scans from the global cloud cache first
        val allEntities = scanDao.getAllScansSync()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            allEntities.forEach { entity ->
                com.safeqr.scanner.data.remote.CloudSyncManager.deleteGlobalScan(entity.rawContent)
            }
        }
        
        scanDao.clearAll()
    }

    /**
     * Deletes a specific URL/content from the history entirely.
     */
    suspend fun deleteScan(rawContent: String) {
        scanDao.deleteByContent(rawContent)
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.safeqr.scanner.data.remote.CloudSyncManager.deleteGlobalScan(rawContent)
        }
    }

    suspend fun updateFavorite(rawContent: String, isFavorite: Boolean) {
        scanDao.updateFavorite(rawContent, isFavorite)
    }

    suspend fun updateTags(rawContent: String, tags: List<String>) {
        val json = com.google.gson.Gson().toJson(tags)
        scanDao.updateTags(rawContent, json)
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

        // Preserve history enrichment fields from previous scans
        var isFavorite = false
        var tags = emptyList<String>()
        val existingScan = scanDao.findByContent(result.rawContent)
        if (existingScan != null) {
            val existingResult = existingScan.toScanResult()
            isFavorite = existingResult.isFavorite
            tags = existingResult.tags
        }

        return result.copy(
            overallScore = overallScore,
            threatDetails = threatDetails,
            positiveDetails = positiveDetails,
            safetyStatus = newSafetyStatus,
            isFavorite = isFavorite,
            tags = tags
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

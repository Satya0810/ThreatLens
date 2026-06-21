package com.safeqr.scanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val rawContent: String,
    val isUrl: Boolean,
    val domain: String?,
    val safetyStatus: String,
    val threatDetails: String, // JSON list serialized via Gson
    val overallScore: Float,
    val timestamp: Long,
    val isAdultContent: Boolean = false,
    val isTransaction: Boolean = false,
    val positiveDetails: String = "[]",
    val communityReportsCount: Int = 0,
    val communityReportReasons: String = "[]",
    val siteCategory: String = "General / Unknown",
    val siteSummary: String? = null,
    // ── Previously missing fields (Bug 2 fix) ──────────────────────────────
    val originalUrl: String? = null,
    val expandedUrl: String? = null,
    val redirectChain: String = "[]",         // JSON list of URLs
    val isBrandImpersonation: Boolean = false,
    val virusTotalPositives: Int = 0,
    val virusTotalTotal: Int = 0,
    val heuristicFlags: String = "[]",        // JSON list of flag strings
    val safeBrowsingResult: String? = null
) {

    /**
     * Converts this Room entity back into a domain [ScanResult].
     */
    fun toScanResult(): ScanResult {
        val gson = Gson()
        val listType = object : TypeToken<List<String>>() {}.type
        val details: List<String> = try {
            gson.fromJson(threatDetails, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val positiveDetailsList: List<String> = try {
            gson.fromJson(positiveDetails, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        val communityReasonsList: List<String> = try {
            gson.fromJson(communityReportReasons, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val redirectChainList: List<String> = try {
            gson.fromJson(redirectChain, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val heuristicFlagsList: List<String> = try {
            gson.fromJson(heuristicFlags, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return ScanResult(
            rawContent = rawContent,
            isUrl = isUrl,
            originalUrl = originalUrl,
            expandedUrl = expandedUrl,
            redirectChain = redirectChainList,
            domain = domain,
            safetyStatus = try {
                SafetyStatus.valueOf(safetyStatus)
            } catch (e: IllegalArgumentException) {
                SafetyStatus.UNKNOWN
            },
            isBrandImpersonation = isBrandImpersonation,
            threatDetails = details,
            safeBrowsingResult = safeBrowsingResult,
            virusTotalPositives = virusTotalPositives,
            virusTotalTotal = virusTotalTotal,
            heuristicFlags = heuristicFlagsList,
            overallScore = overallScore,
            timestamp = timestamp,
            isAdultContent = isAdultContent,
            isTransaction = isTransaction,
            positiveDetails = positiveDetailsList,
            communityReportsCount = communityReportsCount,
            communityReportReasons = communityReasonsList,
            siteCategory = siteCategory,
            siteSummary = siteSummary
        )
    }

    companion object {
        private val gson = Gson()

        /**
         * Creates a [ScanEntity] from a domain [ScanResult] for persistence.
         */
        fun fromScanResult(result: ScanResult): ScanEntity {
            return ScanEntity(
                rawContent = result.rawContent,
                isUrl = result.isUrl,
                originalUrl = result.originalUrl,
                expandedUrl = result.expandedUrl,
                redirectChain = gson.toJson(result.redirectChain),
                domain = result.domain,
                safetyStatus = result.safetyStatus.name,
                isBrandImpersonation = result.isBrandImpersonation,
                threatDetails = gson.toJson(result.threatDetails),
                safeBrowsingResult = result.safeBrowsingResult,
                virusTotalPositives = result.virusTotalPositives,
                virusTotalTotal = result.virusTotalTotal,
                heuristicFlags = gson.toJson(result.heuristicFlags),
                overallScore = result.overallScore,
                timestamp = result.timestamp,
                isAdultContent = result.isAdultContent,
                isTransaction = result.isTransaction,
                positiveDetails = gson.toJson(result.positiveDetails),
                communityReportsCount = result.communityReportsCount,
                communityReportReasons = gson.toJson(result.communityReportReasons),
                siteCategory = result.siteCategory,
                siteSummary = result.siteSummary
            )
        }
    }
}

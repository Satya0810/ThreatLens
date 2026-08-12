package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a crowdsourced threat report submitted by a user.
 * Reports are saved locally and synced to Firebase for community-wide protection.
 */
@Entity(tableName = "threat_reports")
data class ThreatReportEntity(
    @PrimaryKey val reportId: String,       // UUID
    val rawContent: String,                 // The QR content being reported
    val reportType: String,                 // "PHISHING", "FRAUD", "MALWARE", "SCAM_STICKER", "OTHER"
    val description: String,                // User's description
    val latitude: Double?,                  // GPS location where the QR was found
    val longitude: Double?,
    val locationName: String?,              // Reverse-geocoded place name
    val photoUri: String?,                  // Local URI to photo evidence
    val reporterUserId: String,             // The user who submitted the report
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false           // Whether it has been uploaded to Firebase
)

/** Enum for categorizing threat report types. */
enum class ThreatReportType(val label: String, val icon: String) {
    PHISHING("Phishing", "🎣"),
    FRAUD("Fraud / Scam", "💰"),
    MALWARE("Malware", "🦠"),
    SCAM_STICKER("Scam Sticker", "🏷️"),
    IMPERSONATION("Brand Impersonation", "🎭"),
    OTHER("Other", "⚠️")
}

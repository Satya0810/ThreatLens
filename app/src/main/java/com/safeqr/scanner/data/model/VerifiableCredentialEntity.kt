package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a W3C-style Verifiable Credential in the local Vault.
 * Credentials can be issued by ThreatLens users and verified by scanning the QR code.
 */
@Entity(tableName = "verifiable_credentials")
data class VerifiableCredentialEntity(
    @PrimaryKey val vcId: String,           // Unique credential ID
    val type: String,                       // "IDENTITY", "DIPLOMA", "MEMBERSHIP", "HEALTH", "EMPLOYMENT"
    val issuerName: String,                 // Name of the issuer
    val issuerId: String,                   // ThreatLens user ID of the issuer
    val subjectName: String,                // Name of the credential subject
    val claims: String,                     // JSON map of claim key-value pairs
    val issuedAt: Long,                     // Issuance timestamp
    val expiresAt: Long?,                   // Expiration timestamp (null = no expiry)
    val qrPayload: String,                  // The full threatlensvc:// encoded string
    val isRevoked: Boolean = false,         // Whether the issuer has revoked this credential
    val createdByUserId: String             // The user who created/stored this credential
)

/** Enum for categorizing verifiable credential types. */
enum class CredentialType(val label: String, val icon: String) {
    IDENTITY("Identity", "🆔"),
    DIPLOMA("Diploma / Certificate", "🎓"),
    MEMBERSHIP("Membership", "🏅"),
    HEALTH("Health Record", "🏥"),
    EMPLOYMENT("Employment", "💼"),
    LICENSE("License / Permit", "📜"),
    CUSTOM("Custom Credential", "📋")
}

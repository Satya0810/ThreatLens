package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── QR Types supported ─────────────────────────────────────────────────────
enum class QrType(val label: String, val icon: String) {
    URL("URL / Link", "🔗"),
    TEXT("Plain Text", "📄"),
    EMAIL("Email", "✉️"),
    PHONE("Phone Number", "📞"),
    SMS("SMS", "💬"),
    WIFI("WiFi Network", "📶"),
    CONTACT("Contact (vCard)", "📇"),
    LOCATION("Location", "📍"),
    EVENT("Calendar Event", "📅"),
    FILE("File / Document", "📁"),
    APP("App Store Link", "📱"),
    SOCIAL("Social Profile", "👤"),
    PAYMENT("Payment / UPI", "💳"),
    WHATSAPP("WhatsApp", "📗"),
    TELEGRAM("Telegram", "✈️"),
    PAYPAL("PayPal", "🅿️"),
    SPOTIFY("Spotify", "🎵"),
    YOUTUBE("YouTube", "▶️"),
    CRYPTO("Crypto Wallet", "🪙"),
    DYNAMIC("Dynamic QR", "⚡"),
    TICKET("Event Ticket", "🎟️")
}

// ── Dynamic QR — stored in local DB, URL can be edited after generation ────
@Entity(tableName = "dynamic_qrs")
data class DynamicQrEntity(
    @PrimaryKey val id: String,             // UUID
    val title: String,
    val shortCode: String,                  // e.g. "abc123" → shown as tl.app/abc123
    val targetUrl: String,                  // The real destination URL (editable)
    val createdAt: Long = System.currentTimeMillis(),
    val activeFrom: Long? = null,
    val expiresAt: Long? = null,            // null = never expires
    val maxScans: Int? = null,              // null = unlimited scans
    val scanCount: Int = 0,
    val isActive: Boolean = true,
    val createdByUserId: String = "",
    val scanLocations: String = "[]",       // JSON array of city strings
    val passwordHash: String? = null,       // Password required to unlock redirect
    val alternateUrls: String? = null       // JSON list of fallback/rotating URLs
)

// ── User Account — stored locally ─────────────────────────────────────────
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,         // e.g. "TL-A3F9"  — shareable ID
    val displayName: String,
    val phoneNumber: String = "",
    val password: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val avatarColor: Int = 0xFF00D4FF.toInt(),
    val photoUrl: String? = null,
    val email: String? = null,
    val age: Int? = null,
    val isVerified: Boolean = false,
    val isGatekeeper: Boolean = false       // Determines if user can scan Event Tickets
)

// ── Cloud-based Event Ticket (Simulated) ────────────────────────────────────
data class CloudEventTicket(
    val ticketId: String,                   // UUID
    val eventId: String,
    val attendeeName: String,
    val attendeeId: String?,                // If they are a registered user
    val ticketTier: String = "General Admission",
    val seatAssignment: String? = null,
    val customFlags: String? = null,        // e.g. "VIP, Under 21"
    var isScanned: Boolean = false,
    var scannedAt: Long? = null,
    val signatureHash: String               // Used to verify cryptographic integrity
)

// ── Shared QR Event — legacy, replaced by Gatekeeper tickets ────────────────
@Entity(tableName = "shared_qr_events")
data class SharedQrEvent(
    @PrimaryKey val eventId: String,
    val qrId: String,
    val fromUserId: String,
    val toUserId: String,
    val eventName: String,                  // e.g. "Office Entry", "Conference Badge"
    val sharedAt: Long = System.currentTimeMillis(),
    val isEntryType: Boolean = true         // true=entry, false=exit
)

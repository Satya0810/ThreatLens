package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── First-class Event Entity ────────────────────────────────────────────────
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val eventId: String,        // UUID
    val name: String,
    val description: String = "",
    val venue: String = "",
    val startTime: Long? = null,
    val endTime: Long? = null,
    val capacity: Int = 0,                  // 0 = unlimited
    val bannerColor: Long = 0xFF3B82F6,     // Default PrimaryBlue
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUserId: String = ""
)

// ── Preset event brand colors ───────────────────────────────────────────────
object EventBannerColors {
    val BLUE       = 0xFF3B82F6L
    val PURPLE     = 0xFF8B5CF6L
    val EMERALD    = 0xFF10B981L
    val AMBER      = 0xFFF59E0BL
    val ROSE       = 0xFFF43F5EL
    val CYAN       = 0xFF06B6D4L

    val all = listOf(BLUE, PURPLE, EMERALD, AMBER, ROSE, CYAN)
    val labels = listOf("Blue", "Purple", "Emerald", "Amber", "Rose", "Cyan")
}

// ── Ticket Entity (upgraded with attendee display info) ─────────────────────
@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val ticketId: String,       // Unique ID for the QR code
    val eventId: String,                    // The event this ticket belongs to
    val userId: String?,                    // The guest's user ID (optional)
    val attendeeName: String = "",          // Display name for roster
    val ticketTier: String = "General",     // VIP, General, Staff, Custom
    val maxAllowedScans: Int,               // -1 for unlimited
    val currentScanCount: Int = 0,
    val currentStatus: TicketStatus = TicketStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val activeFrom: Long? = null,
    val activeUntil: Long? = null
)

enum class TicketStatus {
    PENDING, CHECKED_IN, CHECKED_OUT, EXPIRED, REVOKED
}

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey val logId: String,          // UUID
    val ticketId: String,
    val eventId: String,
    val actionType: ActionType,             // ENTRY or EXIT
    val scannedByUserId: String,            // The staff member who scanned it
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {
    ENTRY, EXIT
}

@Entity(tableName = "event_roles")
data class EventRoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: String,
    val userId: String,
    val role: EventRole
)

enum class EventRole {
    ADMIN, ORGANIZER, VERIFIER, VIEWER
}

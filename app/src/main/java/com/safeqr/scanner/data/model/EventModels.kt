package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val ticketId: String,       // Unique ID for the QR code
    val eventId: String,                    // The event this ticket belongs to
    val userId: String?,                    // The guest's user ID (optional)
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

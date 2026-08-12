package com.safeqr.scanner.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.data.local.ScanDatabase
import com.safeqr.scanner.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val eventDao = ScanDatabase.getInstance(application).eventDao()

    private val _scanResult = MutableStateFlow<EventScanResult?>(null)
    val scanResult: StateFlow<EventScanResult?> = _scanResult

    // ── Reactive Events ─────────────────────────────────────────────────
    val events: StateFlow<List<EventEntity>> = eventDao.getAllEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createEvent(
        name: String,
        description: String = "",
        venue: String = "",
        startTime: Long? = null,
        endTime: Long? = null,
        capacity: Int = 0,
        bannerColor: Long = EventBannerColors.BLUE,
        createdByUserId: String = ""
    ): String {
        val eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        viewModelScope.launch {
            eventDao.insertEvent(
                EventEntity(
                    eventId = eventId,
                    name = name,
                    description = description,
                    venue = venue,
                    startTime = startTime,
                    endTime = endTime,
                    capacity = capacity,
                    bannerColor = bannerColor,
                    createdByUserId = createdByUserId
                )
            )
        }
        return eventId
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { eventDao.updateEvent(event) }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventDao.deleteTicketsForEvent(eventId)
            eventDao.deleteEvent(eventId)
        }
    }

    fun toggleEventActive(eventId: String) {
        viewModelScope.launch {
            val event = eventDao.getEventById(eventId)
            if (event != null) {
                eventDao.updateEvent(event.copy(isActive = !event.isActive))
            }
        }
    }

    // ── Reactive Tickets & Logs ─────────────────────────────────────────
    fun getTicketsFlow(eventId: String): Flow<List<TicketEntity>> = eventDao.getTicketsForEventFlow(eventId)

    fun getLogsFlow(eventId: String): Flow<List<AttendanceLogEntity>> = eventDao.getLogsForEventFlow(eventId)

    // ── Ticket Generation ───────────────────────────────────────────────
    fun generateTicket(
        eventId: String,
        maxAllowedScans: Int,
        userId: String? = null,
        attendeeName: String = "",
        ticketTier: String = "General",
        activeFrom: Long? = null,
        activeUntil: Long? = null
    ): String {
        val ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val newTicket = TicketEntity(
            ticketId = ticketId,
            eventId = eventId,
            userId = userId,
            attendeeName = attendeeName,
            ticketTier = ticketTier,
            maxAllowedScans = maxAllowedScans,
            activeFrom = activeFrom,
            activeUntil = activeUntil
        )
        viewModelScope.launch {
            eventDao.insertTicket(newTicket)
        }
        return ticketId
    }

    fun generateBulkTickets(
        eventId: String,
        tier: String = "General",
        count: Int,
        maxAllowedScans: Int = 1
    ): List<String> {
        val ticketIds = mutableListOf<String>()
        for (i in 1..count) {
            val ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
            ticketIds.add(ticketId)
            val ticket = TicketEntity(
                ticketId = ticketId,
                eventId = eventId,
                userId = null,
                attendeeName = "Attendee #$i",
                ticketTier = tier,
                maxAllowedScans = maxAllowedScans
            )
            viewModelScope.launch {
                eventDao.insertTicket(ticket)
            }
        }
        return ticketIds
    }

    // ── Scan Processing ─────────────────────────────────────────────────
    fun processScan(ticketId: String, currentScannerUserId: String, isEntryMode: Boolean) {
        viewModelScope.launch {
            val ticket = eventDao.getTicketById(ticketId)
            if (ticket == null) {
                _scanResult.value = EventScanResult.Error("Invalid or forged ticket.")
                return@launch
            }

            val now = System.currentTimeMillis()
            if (ticket.activeFrom != null && now < ticket.activeFrom) {
                _scanResult.value = EventScanResult.Error("Ticket is not active yet.")
                return@launch
            }
            if (ticket.activeUntil != null && now > ticket.activeUntil) {
                _scanResult.value = EventScanResult.Error("Ticket has expired.")
                return@launch
            }

            if (ticket.currentStatus == TicketStatus.REVOKED) {
                _scanResult.value = EventScanResult.Error("This ticket has been revoked.")
                return@launch
            }

            if (isEntryMode) {
                if (ticket.currentStatus == TicketStatus.CHECKED_IN) {
                    _scanResult.value = EventScanResult.Error("Ticket already checked in!")
                    return@launch
                }
                if (ticket.maxAllowedScans != -1 && ticket.currentScanCount >= ticket.maxAllowedScans) {
                    _scanResult.value = EventScanResult.Error("Scan limit reached for this ticket.")
                    return@launch
                }

                val updatedTicket = ticket.copy(
                    currentStatus = TicketStatus.CHECKED_IN,
                    currentScanCount = ticket.currentScanCount + 1
                )
                eventDao.updateTicket(updatedTicket)
                logAttendance(ticketId, ticket.eventId, ActionType.ENTRY, currentScannerUserId)
                _scanResult.value = EventScanResult.Success("Entry Approved", updatedTicket)

            } else {
                if (ticket.currentStatus == TicketStatus.PENDING) {
                    _scanResult.value = EventScanResult.Error("Guest has not checked in yet. Cannot checkout.")
                    return@launch
                }
                if (ticket.currentStatus == TicketStatus.CHECKED_OUT) {
                    _scanResult.value = EventScanResult.Error("Guest has already checked out.")
                    return@launch
                }

                val updatedTicket = ticket.copy(currentStatus = TicketStatus.CHECKED_OUT)
                eventDao.updateTicket(updatedTicket)
                logAttendance(ticketId, ticket.eventId, ActionType.EXIT, currentScannerUserId)
                _scanResult.value = EventScanResult.Success("Exit Logged Successfully", updatedTicket)
            }
        }
    }

    private suspend fun logAttendance(ticketId: String, eventId: String, actionType: ActionType, scannerId: String) {
        val log = AttendanceLogEntity(
            logId = UUID.randomUUID().toString(),
            ticketId = ticketId,
            eventId = eventId,
            actionType = actionType,
            scannedByUserId = scannerId
        )
        eventDao.insertAttendanceLog(log)
    }

    fun resetScanResult() { _scanResult.value = null }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch { eventDao.deleteTicket(ticketId) }
    }

    fun updateTicketDates(ticketId: String, newActiveFrom: Long?, newActiveUntil: Long?) {
        viewModelScope.launch {
            val ticket = eventDao.getTicketById(ticketId)
            if (ticket != null) {
                eventDao.updateTicket(ticket.copy(activeFrom = newActiveFrom, activeUntil = newActiveUntil))
            }
        }
    }

    fun revokeTicket(ticketId: String) {
        viewModelScope.launch {
            val ticket = eventDao.getTicketById(ticketId)
            if (ticket != null) {
                eventDao.updateTicket(ticket.copy(currentStatus = TicketStatus.REVOKED))
            }
        }
    }

    fun grantRole(eventId: String, targetUserId: String, role: EventRole) {
        viewModelScope.launch {
            eventDao.insertEventRole(EventRoleEntity(eventId = eventId, userId = targetUserId, role = role))
        }
    }

    suspend fun getLogsForEvent(eventId: String): List<AttendanceLogEntity> = eventDao.getLogsForEvent(eventId)

    suspend fun getTicketsForEvent(eventId: String): List<TicketEntity> = eventDao.getTicketsForEvent(eventId)

    // ── CSV Export ───────────────────────────────────────────────────────
    fun exportAttendanceCsv(eventId: String, eventName: String) {
        viewModelScope.launch {
            try {
                val logs = eventDao.getLogsForEvent(eventId)
                val tickets = eventDao.getTicketsForEvent(eventId)
                val ticketMap = tickets.associateBy { it.ticketId }
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

                val sb = StringBuilder()
                sb.appendLine("Ticket ID,Attendee Name,Tier,Action,Scanner,Timestamp")
                for (log in logs) {
                    val tk = ticketMap[log.ticketId]
                    sb.appendLine("${log.ticketId},${tk?.attendeeName ?: "Unknown"},${tk?.ticketTier ?: "-"},${log.actionType},${log.scannedByUserId},${fmt.format(java.util.Date(log.timestamp))}")
                }

                val context = getApplication<Application>()
                val fileName = "Attendance_${eventName.replace(" ", "_")}_${System.currentTimeMillis()}.csv"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(sb.toString().toByteArray()) } }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    java.io.FileOutputStream(java.io.File(downloadsDir, fileName)).use { it.write(sb.toString().toByteArray()) }
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, "CSV exported to Downloads", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // ── Cloud Event Ticketing (Zero-Knowledge & Gatekeeper) ─────────────
    fun processCloudScan(ticketId: String, gatekeeperId: String, signature: String, isEntryMode: Boolean) {
        viewModelScope.launch {
            try {
                val validTicket = com.safeqr.scanner.data.remote.CloudSyncManager.validateTicket(ticketId, gatekeeperId, signature, isEntryMode)
                if (validTicket == null) {
                    _scanResult.value = EventScanResult.Error("Ticket not found or invalid signature.")
                    return@launch
                }
                val modeString = if (isEntryMode) "Entry" else "Exit"
                _scanResult.value = EventScanResult.CloudSuccess("Valid Cloud $modeString", validTicket)
            } catch (e: com.safeqr.scanner.data.remote.CloudSyncManager.NetworkException) {
                val isValidOffline = com.safeqr.scanner.data.remote.CloudSyncManager.verifyTotpSignature(ticketId, signature)
                if (isValidOffline) {
                    val modeAction = if (isEntryMode) ActionType.ENTRY else ActionType.EXIT
                    logAttendance(ticketId, "offline-cloud-event", modeAction, gatekeeperId)
                    val offlineTicket = CloudEventTicket(ticketId = ticketId, eventId = "offline-cloud-event", attendeeName = "Offline Scan", attendeeId = null, signatureHash = signature, isScanned = isEntryMode)
                    _scanResult.value = EventScanResult.CloudSuccess("Offline Verified", offlineTicket)
                } else {
                    _scanResult.value = EventScanResult.Error("Offline Check Failed: Invalid Signature")
                }
            } catch (e: com.safeqr.scanner.data.remote.CloudSyncManager.RoleException) {
                _scanResult.value = EventScanResult.Error(e.message ?: "Unauthorized Gatekeeper")
            } catch (e: Exception) {
                _scanResult.value = EventScanResult.Error("Scan failed: ${e.message}")
            }
        }
    }
}

sealed class EventScanResult {
    data class Success(val message: String, val ticket: TicketEntity) : EventScanResult()
    data class CloudSuccess(val message: String, val ticket: CloudEventTicket) : EventScanResult()
    data class Error(val message: String) : EventScanResult()
}

package com.safeqr.scanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.data.local.ScanDatabase
import com.safeqr.scanner.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val eventDao = ScanDatabase.getInstance(application).eventDao()

    private val _scanResult = MutableStateFlow<EventScanResult?>(null)
    val scanResult: StateFlow<EventScanResult?> = _scanResult

    fun generateTicket(eventId: String, maxAllowedScans: Int, userId: String? = null, activeFrom: Long? = null, activeUntil: Long? = null): String {
        val ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val newTicket = TicketEntity(
            ticketId = ticketId,
            eventId = eventId,
            userId = userId,
            maxAllowedScans = maxAllowedScans,
            activeFrom = activeFrom,
            activeUntil = activeUntil
        )
        viewModelScope.launch {
            eventDao.insertTicket(newTicket)
        }
        return ticketId
    }

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

                // Process Entry
                val updatedTicket = ticket.copy(
                    currentStatus = TicketStatus.CHECKED_IN,
                    currentScanCount = ticket.currentScanCount + 1
                )
                eventDao.updateTicket(updatedTicket)
                
                logAttendance(ticketId, ticket.eventId, ActionType.ENTRY, currentScannerUserId)
                _scanResult.value = EventScanResult.Success("Entry Approved", updatedTicket)

            } else {
                // Exit Mode
                if (ticket.currentStatus == TicketStatus.PENDING) {
                    _scanResult.value = EventScanResult.Error("Guest has not checked in yet. Cannot checkout.")
                    return@launch
                }
                if (ticket.currentStatus == TicketStatus.CHECKED_OUT) {
                    _scanResult.value = EventScanResult.Error("Guest has already checked out.")
                    return@launch
                }

                // Process Exit
                val updatedTicket = ticket.copy(
                    currentStatus = TicketStatus.CHECKED_OUT
                )
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

    fun resetScanResult() {
        _scanResult.value = null
    }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch {
            eventDao.deleteTicket(ticketId)
        }
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
            val newRole = EventRoleEntity(
                eventId = eventId,
                userId = targetUserId,
                role = role
            )
            eventDao.insertEventRole(newRole)
        }
    }

    suspend fun getLogsForEvent(eventId: String): List<AttendanceLogEntity> {
        return eventDao.getLogsForEvent(eventId)
    }

    suspend fun getTicketsForEvent(eventId: String): List<TicketEntity> {
        return eventDao.getTicketsForEvent(eventId)
    }

    // ── Cloud Event Ticketing (Zero-Knowledge & Gatekeeper) ─────────────
    
    fun processCloudScan(ticketId: String, gatekeeperId: String, signature: String, isEntryMode: Boolean) {
        viewModelScope.launch {
            try {
                val validTicket = com.safeqr.scanner.data.remote.CloudSyncManager.validateTicket(
                    ticketId, gatekeeperId, signature, isEntryMode
                )
                
                if (validTicket == null) {
                    _scanResult.value = EventScanResult.Error("Ticket not found or invalid signature.")
                    return@launch
                }

                val modeString = if (isEntryMode) "Entry" else "Exit"
                _scanResult.value = EventScanResult.CloudSuccess("Valid Cloud $modeString", validTicket)
                
            } catch (e: com.safeqr.scanner.data.remote.CloudSyncManager.NetworkException) {
                // Offline Fallback
                val isValidOffline = com.safeqr.scanner.data.remote.CloudSyncManager.verifyTotpSignature(ticketId, signature)
                if (isValidOffline) {
                    // Log offline scan
                    val modeAction = if (isEntryMode) com.safeqr.scanner.data.model.ActionType.ENTRY else com.safeqr.scanner.data.model.ActionType.EXIT
                    logAttendance(ticketId, "offline-cloud-event", modeAction, gatekeeperId)
                    
                    // Create a simulated cloud ticket for UI
                    val offlineTicket = com.safeqr.scanner.data.model.CloudEventTicket(
                        ticketId = ticketId,
                        eventId = "offline-cloud-event",
                        attendeeName = "Offline Scan",
                        attendeeId = null,
                        signatureHash = signature,
                        isScanned = isEntryMode
                    )
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

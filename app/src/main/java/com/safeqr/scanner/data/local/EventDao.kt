package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safeqr.scanner.data.model.AttendanceLogEntity
import com.safeqr.scanner.data.model.EventEntity
import com.safeqr.scanner.data.model.EventRoleEntity
import com.safeqr.scanner.data.model.TicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    // -- Events --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY createdAt DESC")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventById(eventId: String): EventEntity?

    @Query("DELETE FROM events WHERE eventId = :eventId")
    suspend fun deleteEvent(eventId: String)

    // -- Tickets --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity)

    @Update
    suspend fun updateTicket(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE ticketId = :ticketId LIMIT 1")
    suspend fun getTicketById(ticketId: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE eventId = :eventId ORDER BY createdAt DESC")
    suspend fun getTicketsForEvent(eventId: String): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun getTicketsForEventFlow(eventId: String): Flow<List<TicketEntity>>

    @Query("SELECT COUNT(*) FROM tickets WHERE eventId = :eventId")
    suspend fun getTicketCountForEvent(eventId: String): Int

    @Query("SELECT COUNT(*) FROM tickets WHERE eventId = :eventId AND currentStatus = 'CHECKED_IN'")
    suspend fun getCheckedInCountForEvent(eventId: String): Int

    @Query("DELETE FROM tickets WHERE ticketId = :ticketId")
    suspend fun deleteTicket(ticketId: String)

    @Query("DELETE FROM tickets WHERE eventId = :eventId")
    suspend fun deleteTicketsForEvent(eventId: String)

    // -- Attendance Logs --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceLog(log: AttendanceLogEntity)

    @Query("SELECT * FROM attendance_logs WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun getLogsForEvent(eventId: String): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs WHERE eventId = :eventId ORDER BY timestamp DESC")
    fun getLogsForEventFlow(eventId: String): Flow<List<AttendanceLogEntity>>

    // -- Roles --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventRole(role: EventRoleEntity)

    @Query("SELECT * FROM event_roles WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getRoleForUser(eventId: String, userId: String): EventRoleEntity?

    @Query("SELECT * FROM event_roles WHERE userId = :userId")
    suspend fun getRolesForUser(userId: String): List<EventRoleEntity>
}


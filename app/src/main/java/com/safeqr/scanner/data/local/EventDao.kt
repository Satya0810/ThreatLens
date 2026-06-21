package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safeqr.scanner.data.model.AttendanceLogEntity
import com.safeqr.scanner.data.model.EventRoleEntity
import com.safeqr.scanner.data.model.TicketEntity

@Dao
interface EventDao {
    
    // -- Tickets --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity)

    @Update
    suspend fun updateTicket(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE ticketId = :ticketId LIMIT 1")
    suspend fun getTicketById(ticketId: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE eventId = :eventId")
    suspend fun getTicketsForEvent(eventId: String): List<TicketEntity>

    @Query("DELETE FROM tickets WHERE ticketId = :ticketId")
    suspend fun deleteTicket(ticketId: String)

    // -- Attendance Logs --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceLog(log: AttendanceLogEntity)

    @Query("SELECT * FROM attendance_logs WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun getLogsForEvent(eventId: String): List<AttendanceLogEntity>

    // -- Roles --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventRole(role: EventRoleEntity)

    @Query("SELECT * FROM event_roles WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getRoleForUser(eventId: String, userId: String): EventRoleEntity?
    
    @Query("SELECT * FROM event_roles WHERE userId = :userId")
    suspend fun getRolesForUser(userId: String): List<EventRoleEntity>
}

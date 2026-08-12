package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safeqr.scanner.data.model.ThreatReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ThreatReportEntity)

    @Query("SELECT * FROM threat_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ThreatReportEntity>>

    @Query("SELECT * FROM threat_reports WHERE rawContent = :rawContent ORDER BY timestamp DESC")
    suspend fun getReportsForContent(rawContent: String): List<ThreatReportEntity>

    @Query("SELECT COUNT(*) FROM threat_reports WHERE rawContent = :rawContent")
    suspend fun getReportCountForContent(rawContent: String): Int

    @Query("SELECT * FROM threat_reports WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<ThreatReportEntity>

    @Query("UPDATE threat_reports SET isSynced = 1 WHERE reportId = :reportId")
    suspend fun markAsSynced(reportId: String)

    @Query("DELETE FROM threat_reports WHERE reportId = :reportId")
    suspend fun deleteById(reportId: String)

    @Query("SELECT COUNT(*) FROM threat_reports")
    suspend fun getTotalReportCount(): Int

    @Query("SELECT COUNT(*) FROM threat_reports WHERE reporterUserId = :userId")
    suspend fun getReportCountByUser(userId: String): Int
}

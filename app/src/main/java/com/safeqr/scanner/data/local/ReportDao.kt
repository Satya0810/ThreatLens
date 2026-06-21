package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safeqr.scanner.data.model.ReportEntity

@Dao
interface ReportDao {
    @Query("SELECT * FROM reported_websites WHERE url = :url LIMIT 1")
    suspend fun getReport(url: String): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Query("SELECT COUNT(*) FROM reported_websites WHERE url LIKE '%' || :domain || '%'")
    suspend fun getReportCountForDomain(domain: String): Int

}

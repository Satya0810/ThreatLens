package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanEntity)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getCount(): Int

    @Query("SELECT * FROM scan_history WHERE rawContent = :rawContent ORDER BY timestamp DESC LIMIT 1")
    suspend fun findByContent(rawContent: String): ScanEntity?

    @Query("DELETE FROM scan_history WHERE rawContent = :rawContent")
    suspend fun deleteByContent(rawContent: String)

    @Query("SELECT COUNT(*) FROM scan_history WHERE domain = :domain AND safetyStatus = 'SAFE'")
    suspend fun getSafeVisitCount(domain: String): Int

}

package com.safeqr.scanner.data.local

import androidx.room.*
import com.safeqr.scanner.data.model.DynamicQrEntity
import com.safeqr.scanner.data.model.SharedQrEvent
import com.safeqr.scanner.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicQrDao {
    @Query("SELECT * FROM dynamic_qrs ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<DynamicQrEntity>>

    @Query("SELECT * FROM dynamic_qrs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DynamicQrEntity?

    @Query("SELECT * FROM dynamic_qrs WHERE shortCode = :code LIMIT 1")
    suspend fun getByShortCode(code: String): DynamicQrEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(qr: DynamicQrEntity)

    @Update
    suspend fun update(qr: DynamicQrEntity)

    @Query("UPDATE dynamic_qrs SET scanCount = scanCount + 1 WHERE id = :id")
    suspend fun incrementScan(id: String)

    @Query("UPDATE dynamic_qrs SET targetUrl = :newUrl WHERE id = :id")
    suspend fun updateTargetUrl(id: String, newUrl: String)

    @Query("DELETE FROM dynamic_qrs WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Query("SELECT * FROM users WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Query("UPDATE users SET isVerified = 1 WHERE userId = :userId")
    suspend fun verifyUser(userId: String)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(user: UserEntity)
}

@Dao
interface SharedQrDao {
    @Query("SELECT * FROM shared_qr_events ORDER BY sharedAt DESC")
    fun getAllFlow(): Flow<List<SharedQrEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SharedQrEvent)
}

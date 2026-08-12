package com.safeqr.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safeqr.scanner.data.model.VerifiableCredentialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VerifiableCredentialDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: VerifiableCredentialEntity)

    @Query("SELECT * FROM verifiable_credentials WHERE createdByUserId = :userId ORDER BY issuedAt DESC")
    fun getCredentialsByUser(userId: String): Flow<List<VerifiableCredentialEntity>>

    @Query("SELECT * FROM verifiable_credentials ORDER BY issuedAt DESC")
    fun getAllCredentials(): Flow<List<VerifiableCredentialEntity>>

    @Query("SELECT * FROM verifiable_credentials ORDER BY issuedAt DESC")
    suspend fun getAllCredentialsSync(): List<VerifiableCredentialEntity>

    @Query("SELECT * FROM verifiable_credentials WHERE vcId = :vcId LIMIT 1")
    suspend fun getById(vcId: String): VerifiableCredentialEntity?

    @Query("UPDATE verifiable_credentials SET isRevoked = 1 WHERE vcId = :vcId")
    suspend fun revokeCredential(vcId: String)

    @Query("DELETE FROM verifiable_credentials WHERE vcId = :vcId")
    suspend fun deleteById(vcId: String)

    @Query("SELECT COUNT(*) FROM verifiable_credentials WHERE createdByUserId = :userId")
    suspend fun getCountByUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM verifiable_credentials")
    suspend fun getTotalCount(): Int
}

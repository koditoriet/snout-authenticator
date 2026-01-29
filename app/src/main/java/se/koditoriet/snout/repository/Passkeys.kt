package se.koditoriet.snout.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.Passkey

@Dao
interface Passkeys {
    @Query("SELECT * FROM passkeys ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<Passkey>>

    @Query("SELECT * FROM passkeys ORDER BY sortOrder ASC")
    suspend fun getAll(): List<Passkey>

    @Query("SELECT * FROM passkeys WHERE rpId = :rpId ORDER BY sortOrder ASC")
    suspend fun getAll(rpId: String): List<Passkey>

    @Query("SELECT MAX(sortOrder) + 10000000000 FROM passkeys")
    suspend fun getNextSortOrder(): Long

    @Query("UPDATE passkeys SET encryptedBackupPrivateKey = NULL")
    suspend fun eraseBackups()

    @Query("""
        UPDATE passkeys
        SET sortOrder = (
            SELECT rn * 10000000000
            FROM (
                SELECT
                    credentialId,
                    ROW_NUMBER() OVER (ORDER BY sortOrder) AS rn
                FROM totp_secrets
            ) AS ordered
            WHERE ordered.credentialId = passkeys.credentialId
        );
    """)
    suspend fun reindexSortOrder()

    @Query("DELETE FROM passkeys WHERE credentialId = :credentialId")
    suspend fun delete(credentialId: CredentialId)

    @Update
    suspend fun update(passkey: Passkey)

    @Insert
    suspend fun insert(passkey: Passkey)

    @Query("SELECT * FROM passkeys WHERE credentialId = :credentialId")
    suspend fun get(credentialId: CredentialId): Passkey
}

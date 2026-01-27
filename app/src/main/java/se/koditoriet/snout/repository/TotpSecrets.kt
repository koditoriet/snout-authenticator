package se.koditoriet.snout.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import se.koditoriet.snout.vault.TotpSecret

@Dao
interface TotpSecrets {
    @Query("SELECT * FROM totp_secrets ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TotpSecret>>

    @Query("SELECT * FROM totp_secrets ORDER BY sortOrder ASC")
    suspend fun getAll(): List<TotpSecret>

    // Spacing sort order by 10B means we can have a little less than 1B secrets.
    // That should be enough.
    @Query("SELECT MAX(sortOrder) + 10000000000 FROM totp_secrets")
    suspend fun getNextSortOrder(): Long

    @Query("UPDATE totp_secrets SET encryptedBackupSecret = NULL")
    suspend fun eraseBackups()

    @Query("""
        UPDATE totp_secrets
        SET sortOrder = (
            SELECT rn * 10000000000
            FROM (
                SELECT
                    id,
                    ROW_NUMBER() OVER (ORDER BY sortOrder) AS rn
                FROM totp_secrets
            ) AS ordered
            WHERE ordered.id = totp_secrets.id
        );
    """)
    suspend fun reindexSortOrder()

    @Query("DELETE FROM totp_secrets WHERE id = :id")
    suspend fun delete(id: TotpSecret.Id)

    @Query("SELECT * FROM totp_secrets WHERE id = :id")
    suspend fun get(id: TotpSecret.Id): TotpSecret

    @Update
    suspend fun update(totpSecret: TotpSecret)

    @Insert
    suspend fun insert(totpSecret: TotpSecret)
}
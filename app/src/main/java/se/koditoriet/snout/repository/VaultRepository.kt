package se.koditoriet.snout.repository

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import se.koditoriet.snout.vault.TotpSecret

@Database(entities = [TotpSecret::class], version = 1)
abstract class VaultRepository : RoomDatabase() {
    companion object {
        fun open(ctx: Context, path: String, dbKey: ByteArray): VaultRepository {
            val supportFactory = SupportOpenHelperFactory(dbKey)
            return Room.databaseBuilder(ctx, VaultRepository::class.java, path).apply {
                openHelperFactory(supportFactory)
                fallbackToDestructiveMigration(false)
            }.build()
        }
    }

    abstract fun totpSecrets(): TotpSecrets
}

@Dao
interface TotpSecrets {
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

    @Delete
    suspend fun delete(totpSecret: TotpSecret)

    @Update
    suspend fun update(totpSecret: TotpSecret)

    @Insert
    suspend fun insert(item: TotpSecret)
}

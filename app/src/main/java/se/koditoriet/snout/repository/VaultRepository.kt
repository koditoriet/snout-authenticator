package se.koditoriet.snout.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.Passkey
import se.koditoriet.snout.vault.TotpSecret
import se.koditoriet.snout.vault.UserId

@Database(entities = [TotpSecret::class, Passkey::class], version = 2)
@TypeConverters(
    TotpSecret.Id.TypeConverters::class,
    UserId.TypeConverters::class,
    CredentialId.TypeConverters::class,
    Base64Url.TypeConverters::class,
)
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
    abstract fun passkeys(): Passkeys
}

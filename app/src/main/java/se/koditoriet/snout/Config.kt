package se.koditoriet.snout

import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.koditoriet.snout.crypto.KeyHandle
import se.koditoriet.snout.vault.Vault
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class Config(
    val encryptedDbKey: DbKey? = null,
    val backupKeys: BackupKeys? = null,
    val protectAccountList: Boolean = true,
    val lockOnClose: Boolean = true,
    val lockOnCloseGracePeriod: Int = 30,
    val sortMode: SortMode = SortMode.Manual,
    val screenSecurityEnabled: Boolean = true,
    val hideSecretsFromAccessibility: Boolean = false,
) {
    val backupsEnabled: Boolean
        get() = backupKeys != null


    companion object {
        val default = Config()
    }

    @Serializable
    data class BackupKeys(
        val secretsBackupKeyAlias: String,
        val metadataBackupKeyAlias: String,
    ) {
        fun toVaultBackupKeys(): Vault.BackupKeys =
            Vault.BackupKeys(
                secretsBackupKey = KeyHandle.fromAlias(secretsBackupKeyAlias),
                metadataBackupKey = KeyHandle.fromAlias(metadataBackupKeyAlias),
            )

        companion object {
            fun fromVaultBackupKeys(backupKeys: Vault.BackupKeys) = BackupKeys(
                secretsBackupKeyAlias = backupKeys.secretsBackupKey.alias,
                metadataBackupKeyAlias = backupKeys.metadataBackupKey.alias,
            )
        }
    }
}

enum class SortMode {
    Manual,
    Alphabetic,
}

@Serializable
/**
 * An encrypted key for unlocking the vault database.
 */
class DbKey(
    internal val kekAlias: String,
    internal val encryptedDek: String,
)

object ConfigSerializer : Serializer<Config> {
    private val serializer = Config.serializer()
    override val defaultValue: Config = Config.default

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): Config =
        json.decodeFromString(serializer, input.readBytes().decodeToString())

    override suspend fun writeTo(t: Config, output: OutputStream) =
        output.write(json.encodeToString(Config.serializer(), t).encodeToByteArray())
}

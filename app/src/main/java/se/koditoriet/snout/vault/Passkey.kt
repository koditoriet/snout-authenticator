package se.koditoriet.snout.vault

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.crypto.ECAlgorithm
import se.koditoriet.snout.crypto.KeyHandle

@Serializable
@Entity(tableName = "passkeys")
data class Passkey(
    @PrimaryKey(autoGenerate = false)
    val credentialId: CredentialId,
    val sortOrder: Long,
    val userId: UserId,
    val userName: String,
    val displayName: String,
    val rpId: String,
    val keyAlias: String,
    val publicKey: Base64Url,
    val encryptedBackupPrivateKey: String?,
) {
    val keyHandle: KeyHandle<ECAlgorithm> by lazy {
        KeyHandle.fromAlias(keyAlias)
    }

    /**
     * Pretty-print human-readable description of the passkey
     */
    val description: String by lazy {
        "$rpId \u2022 $userName"
    }
}

@JvmInline
@Serializable
value class CredentialId(val id: Base64Url) {
    val string: String
        get() = id.string

    fun toByteArray(): ByteArray =
        id.toByteArray()

    class TypeConverters {
        @TypeConverter
        fun toId(id: String): CredentialId = fromString(id)

        @TypeConverter
        fun fromId(id: CredentialId): String = id.string
    }

    companion object {
        fun fromString(id: String) =
            CredentialId(Base64Url.fromBase64UrlString(id))
    }
}

@JvmInline
@Serializable
value class UserId(val id: Base64Url) {
    val string: String
        get() = id.string

    fun toByteArray(): ByteArray =
        id.toByteArray()

    class TypeConverters {
        @TypeConverter
        fun toId(id: String): UserId = fromString(id)

        @TypeConverter
        fun fromId(id: UserId): String = id.string
    }

    companion object {
        fun fromString(id: String) =
            UserId(Base64Url.fromBase64UrlString(id))
    }
}

package se.koditoriet.snout.vault

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import se.koditoriet.snout.codec.totpAccount
import se.koditoriet.snout.codec.totpAlgorithm
import se.koditoriet.snout.codec.totpDigits
import se.koditoriet.snout.codec.totpIssuer
import se.koditoriet.snout.codec.totpPeriod
import se.koditoriet.snout.codec.totpSecret
import se.koditoriet.snout.crypto.HmacAlgorithm
import se.koditoriet.snout.crypto.KeyHandle

@Serializable
@Entity(tableName = "totp_secrets")
data class TotpSecret(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val sortOrder: Long,
    val issuer: String,
    val account: String?,
    val digits: Int,
    val period: Int,
    val algorithm: TotpAlgorithm,
    val keyAlias: String,
    val encryptedBackupSecret: String?,
) {
    val keyHandle: KeyHandle<HmacAlgorithm> by lazy {
        KeyHandle.fromAlias(keyAlias)
    }
}

data class NewTotpSecret(
    val metadata: Metadata,
    val secretData: SecretData,
) {
    data class Metadata(
        val issuer: String,
        val account: String?,
    )
    data class SecretData(
        val secret: CharArray,
        val digits: Int,
        val period: Int,
        val algorithm: TotpAlgorithm,
    )

    companion object {
        fun fromUri(uri: String): NewTotpSecret {
            val parsedUri = uri.toUri()
            require(parsedUri.scheme == "otpauth")
            require(parsedUri.host == "totp")
            return NewTotpSecret(
                metadata = Metadata(
                    issuer = parsedUri.totpIssuer,
                    account = parsedUri.totpAccount,
                ),
                secretData = SecretData(
                    secret = parsedUri.totpSecret.toCharArray(),
                    digits = parsedUri.totpDigits,
                    period = parsedUri.totpPeriod,
                    algorithm = TotpAlgorithm.valueOf(parsedUri.totpAlgorithm)
                )
            )
        }
    }
}
enum class TotpAlgorithm { SHA1 }

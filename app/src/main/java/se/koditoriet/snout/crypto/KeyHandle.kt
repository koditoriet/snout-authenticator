package se.koditoriet.snout.crypto

import android.security.keystore.KeyProperties

data class KeyHandle<TAlgorithm : KeyAlgorithm>(
    val usage: KeyUsage<TAlgorithm>,
    val algorithm: TAlgorithm,
    val requiresAuthentication: Boolean,
    val isStrongBoxBacked: Boolean,
    val identifier: KeyIdentifier,
) {
    val alias: String
        get() = "${usage.name}:${algorithm.name}:$requiresAuthentication:$isStrongBoxBacked:${identifier.identifier}"

    companion object {
        fun <T : KeyAlgorithm> fromAlias(alias: String): KeyHandle<T> {
            val parts = alias.split(':', limit = 5)
            require(parts.size == 5)
            val keyUsage = KeyUsage.valueOf<T>(parts[0])
            return KeyHandle(
                usage = keyUsage,
                algorithm = KeyAlgorithm.valueOf(keyUsage, parts[1]),
                requiresAuthentication = parts[2].toBooleanStrict(),
                isStrongBoxBacked = parts[3].toBooleanStrict(),
                identifier = KeyIdentifier.valueOf(parts[4]),
            )
        }
    }
}

sealed class KeyUsage<TAlgorithm : KeyAlgorithm>(val name: String) {
    abstract val purposes: Int

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : KeyAlgorithm> valueOf(s: String): KeyUsage<T> = when (s) {
            HMAC.name -> HMAC
            Encrypt.name -> Encrypt
            EncryptDecrypt.name -> EncryptDecrypt
            else -> throw IllegalArgumentException("'$s' is not a valid key usage")
        } as KeyUsage<T>
    }
    object HMAC : KeyUsage<HmacAlgorithm>("hmac") {
        override val purposes: Int
            get() = KeyProperties.PURPOSE_SIGN
    }

    object Encrypt : KeyUsage<SymmetricAlgorithm>("encrypt") {
        override val purposes: Int
            get() = KeyProperties.PURPOSE_ENCRYPT
    }

    object EncryptDecrypt : KeyUsage<SymmetricAlgorithm>("encrypt-decrypt") {
        override val purposes: Int
            get() = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    }
}

sealed interface KeyAlgorithm {
    val name: String
    val algorithmName: String
    val secretKeySpecName: String

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <TAlgorithm : KeyAlgorithm> valueOf(
            keyUsage: KeyUsage<TAlgorithm>,
            keyAlgorithm: String,
        ): TAlgorithm = when (keyUsage) {
            KeyUsage.HMAC -> HmacAlgorithm.valueOf(keyAlgorithm)
            KeyUsage.Encrypt -> SymmetricAlgorithm.valueOf(keyAlgorithm)
            KeyUsage.EncryptDecrypt -> SymmetricAlgorithm.valueOf(keyAlgorithm)
        } as TAlgorithm
    }
}

enum class HmacAlgorithm(
    override val algorithmName: String,
    val keyStoreDigestName: String,
) : KeyAlgorithm {
    SHA1(KeyProperties.KEY_ALGORITHM_HMAC_SHA1, KeyProperties.DIGEST_SHA1),
    SHA256(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KeyProperties.DIGEST_SHA256);

    override val secretKeySpecName: String
        get() = algorithmName
}

enum class SymmetricAlgorithm(
    override val algorithmName: String,
    val keySize: Int,
    val blockMode: String,
    val paddingScheme: String,
    override val secretKeySpecName: String,
) : KeyAlgorithm {
    AES_GCM(
        "AES/GCM/NoPadding",
        256,
        KeyProperties.BLOCK_MODE_GCM,
        KeyProperties.ENCRYPTION_PADDING_NONE,
        KeyProperties.KEY_ALGORITHM_AES,
    )
}

sealed interface KeyIdentifier {
    val name: String

    val identifier: String
        get() = "${this::class.simpleName}:$name"

    class Internal(override val name: String) : KeyIdentifier
    class Random(override val name: String) : KeyIdentifier

    companion object {
        fun valueOf(identifier: String): KeyIdentifier {
            val parts = identifier.split(':', limit = 2)
            require(parts.size == 2) {
                "'$identifier' is not a valid key identifier"
            }
            return when (parts[0]) {
                Internal::class.simpleName -> Internal(parts[1])
                Random::class.simpleName -> Random(parts[1])
                else -> throw IllegalArgumentException("'$identifier' is not a valid key identifier")
            }
        }
    }
}

val KeyHandle<SymmetricAlgorithm>.allowDecrypt: Boolean
    get() = usage == KeyUsage.EncryptDecrypt

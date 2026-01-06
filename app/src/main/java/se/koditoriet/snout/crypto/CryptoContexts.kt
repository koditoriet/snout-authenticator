package se.koditoriet.snout.crypto

import android.util.Base64
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface HmacContext {
    fun hmac(data: ByteArray): ByteArray

    companion object {
        fun create(key: Key, algorithm: HmacAlgorithm): HmacContext =
            HmacContextImpl(key, algorithm)

        fun create(key: ByteArray, algorithm: HmacAlgorithm): HmacContext =
            SecretKeySpec(key, algorithm.algorithmName).run { HmacContextImpl(this, algorithm) }
    }
}

interface EncryptionContext {
    fun encrypt(data: ByteArray): EncryptedData

    companion object {
        fun create(key: Key, algorithm: SymmetricAlgorithm): EncryptionContext =
            SymmetricContextImpl(key, algorithm)

        fun create(key: ByteArray, algorithm: SymmetricAlgorithm): EncryptionContext =
            SecretKeySpec(key, algorithm.algorithmName).run { SymmetricContextImpl(this, algorithm) }
    }
}

interface DecryptionContext {
    fun decrypt(data: EncryptedData): ByteArray

    companion object {
        fun create(key: Key, algorithm: SymmetricAlgorithm): DecryptionContext =
            SymmetricContextImpl(key, algorithm)

        fun create(keyMaterial: ByteArray, algorithm: SymmetricAlgorithm): DecryptionContext {
            val key = SecretKeySpec(keyMaterial, algorithm.secretKeySpecName)
            return SymmetricContextImpl(key, algorithm)
        }
    }
}

private class HmacContextImpl(
    private val key: Key,
    private val algorithm: HmacAlgorithm,
) : HmacContext {
    override fun hmac(data: ByteArray): ByteArray = Mac.getInstance(algorithm.algorithmName).run {
        init(key)
        doFinal(data)
    }
}

private class SymmetricContextImpl(
    private val key: Key,
    private val algorithm: SymmetricAlgorithm,
) : EncryptionContext, DecryptionContext {
    override fun encrypt(data: ByteArray): EncryptedData = Cipher.getInstance(algorithm.algorithmName).run {
        init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = doFinal(data)
        EncryptedData.from(iv, ciphertext)
    }

    override fun decrypt(data: EncryptedData): ByteArray = Cipher.getInstance(algorithm.algorithmName).run {
        val gcmSpec = GCMParameterSpec(128, data.iv.asBytes)
        init(Cipher.DECRYPT_MODE, key, gcmSpec)
        doFinal(data.ciphertext.asBytes)
    }
}

@JvmInline
value class IV(val asBytes: ByteArray) {
    companion object {
        fun decode(data: String): IV =
            IV(Base64.decode(data, Base64.NO_WRAP))
    }
}

@JvmInline
value class Ciphertext(val asBytes: ByteArray) {
    companion object {
        fun decode(data: String): Ciphertext =
            Ciphertext(Base64.decode(data, Base64.NO_WRAP))
    }
}

data class EncryptedData(
    internal val iv: IV,
    internal val ciphertext: Ciphertext,
) {
    fun encode(): String {
        val iv = Base64.encodeToString(iv.asBytes, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(ciphertext.asBytes, Base64.NO_WRAP)
        return "${iv}:${ciphertext}"
    }

    companion object {
        fun from(iv: ByteArray, ciphertext: ByteArray): EncryptedData =
            EncryptedData(IV(iv), Ciphertext(ciphertext))

        fun decode(data: String): EncryptedData {
            val parts = data.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("argument is not valid encrypted data")
            }
            return EncryptedData(
                iv = IV.decode(parts[0]),
                ciphertext = Ciphertext.decode(parts[1]),
            )
        }
    }
}

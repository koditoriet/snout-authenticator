package se.koditoriet.snout.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec

private const val KEY_AUTHENTICATION_LIFETIME: Int = 10

class Cryptographer(
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) },
    private val keyGeneratorFactory: (algorithm: String) -> KeyGenerator = { createKeystoreGenerator(it) },
    private val secureRandom: SecureRandom = SecureRandom(),
    private var cipherAuthenticator: CipherAuthenticator = AlwaysFailCipherAuthenticator
) {
    fun setCipherAuthenticator(cipherAuthenticator: CipherAuthenticator) {
        this.cipherAuthenticator = cipherAuthenticator
    }

    fun wipeKeys() =
        keyStore.aliases().iterator().forEach { keyStore.deleteEntry(it) }

    fun isInitialized(): Boolean =
        keyStore.aliases().hasMoreElements()

    fun getKeySecurityLevel(keyHandle: KeyHandle<*>): KeySecurityLevel {
        val key = keyStore.getEntry(keyHandle.alias, null) as? KeyStore.SecretKeyEntry
        check(key != null) {
            "key '${keyHandle.alias}' does not exist"
        }
        if (keyHandle.isStrongBoxBacked) {
            return KeySecurityLevel.StrongBox
        }
        val factory = SecretKeyFactory.getInstance(keyHandle.algorithm.secretKeySpecName, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(key.secretKey, KeyInfo::class.java) as KeyInfo
        return when (keyInfo.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.StrongBox
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.Software
            else -> {
                error("'${keyInfo.securityLevel}' is not a valid security level!")
            }
        }
    }

    suspend fun <T> withHmacKey(keyHandle: KeyHandle<HmacAlgorithm>, action: HmacContext.() -> T): T =
        withKey(keyHandle) {
            HmacContext.create(this, keyHandle.algorithm).action()
        }

    suspend fun <T> withEncryptionKey(keyHandle: KeyHandle<SymmetricAlgorithm>, action: EncryptionContext.() -> T): T =
        withKey(keyHandle) {
            EncryptionContext.create(this, keyHandle.algorithm).action()
        }

    suspend fun <T> withDecryptionKey(keyHandle: KeyHandle<SymmetricAlgorithm>, action: DecryptionContext.() -> T): T =
        withKey(keyHandle) {
            DecryptionContext.create(this, keyHandle.algorithm).action()
        }

    fun <T> withDecryptionKey(
        keyMaterial: ByteArray,
        algorithm: SymmetricAlgorithm,
        action: DecryptionContext.() -> T,
    ): T =
        DecryptionContext.create(keyMaterial, algorithm).action()

    suspend fun <T> withKey(keyHandle: KeyHandle<*>, action: Key.() -> T): T =
        keyStore.getKey(keyHandle.alias, null)?.run {
            if (keyHandle.requiresAuthentication) {
                cipherAuthenticator.authenticate { action() }
            } else {
                action()
            }
        } ?: throw IllegalArgumentException("key '${keyHandle.alias}' does not exist")

    fun deleteKey(keyHandle: KeyHandle<*>): Unit =
        keyStore.deleteEntry(keyHandle.alias)

    fun storeSymmetricKey(
        keyIdentifier: KeyIdentifier?,
        allowDecrypt: Boolean,
        allowDeviceCredential: Boolean,
        requiresAuthentication: Boolean,
        keyMaterial: ByteArray,
        algorithm: SymmetricAlgorithm = SymmetricAlgorithm.AES_GCM,
    ): KeyHandle<SymmetricAlgorithm> {
        require(keyMaterial.size == algorithm.keySize / 8)

        val keyHandle = KeyHandle(
            usage = if (allowDecrypt) { KeyUsage.EncryptDecrypt } else { KeyUsage.Encrypt },
            algorithm = algorithm,
            requiresAuthentication = requiresAuthentication,
            isStrongBoxBacked = true,
            identifier = keyIdentifier ?: randomKeyIdentifier(),
        )

        return storeKey(keyHandle, keyMaterial, allowDeviceCredential) {
            setBlockModes(keyHandle.algorithm.blockMode)
            setEncryptionPaddings(keyHandle.algorithm.paddingScheme)
        }
    }

    fun storeHmacKey(
        keyIdentifier: KeyIdentifier?,
        hmacAlgorithm: HmacAlgorithm,
        allowDeviceCredential: Boolean,
        requiresAuthentication: Boolean,
        keyMaterial: ByteArray,
    ): KeyHandle<HmacAlgorithm> {
        val keyHandle = KeyHandle(
            usage = KeyUsage.HMAC,
            algorithm = hmacAlgorithm,
            requiresAuthentication = requiresAuthentication,
            isStrongBoxBacked = true,
            identifier = keyIdentifier ?: randomKeyIdentifier(),
        )

        return storeKey(keyHandle, keyMaterial, allowDeviceCredential) {
            setDigests(keyHandle.algorithm.keyStoreDigestName)
        }
    }

    /**
     * Note that this function modifies the key handle to set the correct StrongBox backing state
     */
    private fun <T : KeyAlgorithm> storeKey(
        keyHandle: KeyHandle<T>,
        keyMaterial: ByteArray,
        allowDeviceCredential: Boolean,
        setProtectionParams: KeyProtection.Builder.() -> Unit,
    ): KeyHandle<T> {
        val keySpec = SecretKeySpec(keyMaterial, keyHandle.algorithm.secretKeySpecName)
        val keyEntry = KeyStore.SecretKeyEntry(keySpec)

        val protectionParamsBuilder = KeyProtection.Builder(keyHandle.usage.purposes).apply {
            setProtectionParams()
            setIsStrongBoxBacked(true)
            if (keyHandle.requiresAuthentication) {
                setUserAuthenticationRequired(true)
                var allowedAuthTypes = KeyProperties.AUTH_BIOMETRIC_STRONG
                if (allowDeviceCredential) {
                    allowedAuthTypes = allowedAuthTypes or KeyProperties.AUTH_DEVICE_CREDENTIAL
                }
                setUserAuthenticationParameters(KEY_AUTHENTICATION_LIFETIME, allowedAuthTypes)
            }
        }
        try {
            val updatedKeyHandle = keyHandle.copy(isStrongBoxBacked = true)
            keyStore.setEntry(updatedKeyHandle.alias, keyEntry, protectionParamsBuilder.build())
            return updatedKeyHandle
        } catch (_: KeyStoreException) {
            val updatedKeyHandle = keyHandle.copy(isStrongBoxBacked = false)
            protectionParamsBuilder.setIsStrongBoxBacked(false)
            keyStore.setEntry(updatedKeyHandle.alias, keyEntry, protectionParamsBuilder.build())
            return updatedKeyHandle
        }
    }

    fun generateSymmetricKey(
        keyIdentifier: KeyIdentifier?,
        requiresAuthentication: Boolean,
        allowDecrypt: Boolean,
        allowDeviceCredential: Boolean,
    ): KeyHandle<SymmetricAlgorithm> =
        keyGeneratorFactory(KeyProperties.KEY_ALGORITHM_AES).let { keyGenerator ->
            val handle = KeyHandle(
                usage = if (allowDecrypt) { KeyUsage.EncryptDecrypt } else { KeyUsage.Encrypt },
                algorithm = SymmetricAlgorithm.AES_GCM,
                requiresAuthentication = requiresAuthentication,
                isStrongBoxBacked = true,
                identifier = keyIdentifier ?: randomKeyIdentifier(),
            )
            val keyGenSpecBuilder = KeyGenParameterSpec.Builder(handle.alias, handle.usage.purposes).apply {
                setKeySize(handle.algorithm.keySize)
                setBlockModes(handle.algorithm.blockMode)
                setEncryptionPaddings(handle.algorithm.paddingScheme)
                setIsStrongBoxBacked(true)
                if (handle.requiresAuthentication) {
                    setUserAuthenticationRequired(true)
                    var allowedAuthTypes = KeyProperties.AUTH_BIOMETRIC_STRONG
                    if (allowDeviceCredential) {
                        allowedAuthTypes = allowedAuthTypes or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    }
                    setUserAuthenticationParameters(KEY_AUTHENTICATION_LIFETIME, allowedAuthTypes)
                }
            }

            try {
                keyGenerator.init(keyGenSpecBuilder.build())
                keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                keyGenSpecBuilder.setIsStrongBoxBacked(false)
                keyGenerator.init(keyGenSpecBuilder.build())
                keyGenerator.generateKey()
            }
            return handle
        }

    private fun randomKeyIdentifier(): KeyIdentifier {
        val identifierBytes = ByteArray(16)
        secureRandom.nextBytes(identifierBytes)
        return KeyIdentifier.Random(Base64.encodeToString(identifierBytes, Base64.NO_WRAP))
    }
}

enum class KeySecurityLevel {
    StrongBox,
    TEE,
    Software,
}

private fun createKeystoreGenerator(algorithm: String): KeyGenerator =
    KeyGenerator.getInstance(algorithm, "AndroidKeyStore")

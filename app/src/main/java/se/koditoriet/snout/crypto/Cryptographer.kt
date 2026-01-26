package se.koditoriet.snout.crypto

import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException


private const val KEY_AUTHENTICATION_LIFETIME: Int = 5
private const val TAG = "Cryptographer"

class Cryptographer(
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) },
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun wipeKeys() =
        keyStore.aliases().iterator().forEach { keyStore.deleteEntry(it) }

    fun isInitialized(): Boolean =
        keyStore.aliases().hasMoreElements()

    @Suppress("UNCHECKED_CAST")
    fun <T : KeyAlgorithm> getKeySecurityLevel(keyHandle: KeyHandle<T>): KeySecurityLevel {
        val keyInfo = when (keyHandle.usage) {
            KeyUsage.Sign -> getPrivateKeyInfo(keyHandle as KeyHandle<ECAlgorithm>)
            else -> getSecretKeyInfo(keyHandle as KeyHandle<SymmetricAlgorithm>)
        }

        check(keyInfo != null) {
            "key '${keyHandle.alias}' does not exist"
        }

        return try {
            when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.StrongBox
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.Software
                else -> {
                    error("'${keyInfo.securityLevel}' is not a valid security level!")
                }
            }
        } catch (_: ProviderException) {
            when (keyHandle.isStrongBoxBacked) {
                true -> KeySecurityLevel.StrongBox
                false -> KeySecurityLevel.Unknown
            }
        }
    }

    private fun getSecretKeyInfo(keyHandle: KeyHandle<SymmetricAlgorithm>): KeyInfo? =
        (keyStore.getEntry(keyHandle.alias, null) as? KeyStore.SecretKeyEntry)?.let {
            val factory = SecretKeyFactory.getInstance(keyHandle.algorithm.secretKeySpecName, "AndroidKeyStore")
            factory.getKeySpec(it.secretKey, KeyInfo::class.java) as KeyInfo
        }

    private fun getPrivateKeyInfo(keyHandle: KeyHandle<ECAlgorithm>): KeyInfo? =
        (keyStore.getEntry(keyHandle.alias, null) as? KeyStore.PrivateKeyEntry)?.let {
            val factory = KeyFactory.getInstance(keyHandle.algorithm.secretKeySpecName, "AndroidKeyStore")
            factory.getKeySpec(it.privateKey, KeyInfo::class.java) as KeyInfo
        }

    suspend fun <T> withHmacKey(
        authenticator: Authenticator,
        keyHandle: KeyHandle<HmacAlgorithm>,
        action: suspend HmacContext.() -> T,
    ): T =
        withKey(authenticator, keyHandle) {
            HmacContext.create(this, keyHandle.algorithm).action()
        }

    suspend fun <T> withEncryptionKey(
        authenticator: Authenticator,
        keyHandle: KeyHandle<EncryptionAlgorithm>,
        action: suspend EncryptionContext.() -> T,
    ): T =
        withKey(authenticator, keyHandle) {
            EncryptionContext.create(this, keyHandle.algorithm).action()
        }

    suspend fun <T> withDecryptionKey(
        authenticator: Authenticator,
        keyHandle: KeyHandle<EncryptionAlgorithm>,
        action: suspend DecryptionContext.() -> T,
    ): T =
        withKey(authenticator, keyHandle) {
            DecryptionContext.create(this, keyHandle.algorithm).action()
        }

    suspend fun <T> withSigningKey(
        authenticator: Authenticator,
        keyHandle: KeyHandle<ECAlgorithm>,
        action: suspend SignatureContext.() -> T,
    ): T =
        (keyStore.getKey(keyHandle.alias, null) as? PrivateKey)?.run {
            if (keyHandle.requiresAuthentication) {
                val sig = Signature.getInstance(keyHandle.algorithm.algorithmName)
                sig.initSign(this)
                authenticator.authenticate(sig) {
                    SignatureContext.create(it).action()
                }
            } else {
                SignatureContext.create(this, keyHandle.algorithm).action()
            }
        } ?: throw IllegalArgumentException("key '${keyHandle.alias}' does not exist")

    suspend fun <T> withDecryptionKey(
        keyMaterial: ByteArray,
        algorithm: EncryptionAlgorithm,
        action: suspend DecryptionContext.() -> T,
    ): T =
        DecryptionContext.create(keyMaterial, algorithm).action()

    suspend fun <T> withEncryptionKey(
        keyMaterial: ByteArray,
        algorithm: EncryptionAlgorithm,
        action: suspend EncryptionContext.() -> T,
    ): T =
        EncryptionContext.create(keyMaterial, algorithm).action()

    suspend fun <T> withKey(
        authenticator: Authenticator,
        keyHandle: KeyHandle<*>,
        action: suspend Key.() -> T,
    ): T =
        keyStore.getKey(keyHandle.alias, null)?.run {
            if (keyHandle.requiresAuthentication) {
                authenticator.authenticate { action() }
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
        algorithm: EncryptionAlgorithm = EncryptionAlgorithm.AES_GCM,
    ): KeyHandle<EncryptionAlgorithm> {
        require(keyMaterial.size == algorithm.keySize / 8)

        val keyHandle = KeyHandle(
            usage = if (allowDecrypt) { KeyUsage.EncryptDecrypt } else { KeyUsage.Encrypt },
            algorithm = algorithm,
            requiresAuthentication = requiresAuthentication,
            isStrongBoxBacked = true,
            identifier = keyIdentifier ?: randomKeyIdentifier(),
        )

        return storeSymmetricKey(keyHandle, keyMaterial, allowDeviceCredential) {
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

        return storeSymmetricKey(keyHandle, keyMaterial, allowDeviceCredential) {
            setDigests(keyHandle.algorithm.keyStoreDigestName)
        }
    }

    /**
     * Note that this function modifies the key handle to set the correct StrongBox backing state
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : SymmetricAlgorithm> storeSymmetricKey(
        keyHandle: KeyHandle<T>,
        keyMaterial: ByteArray,
        allowDeviceCredential: Boolean,
        setProtectionParams: KeyProtection.Builder.() -> Unit,
    ): KeyHandle<T> =
        keyStore.importKey(
            keyHandle = keyHandle,
            keyEntry = KeyEntry.create(keyHandle as KeyHandle<SymmetricAlgorithm>, keyMaterial),
            allowDeviceCredential = allowDeviceCredential,
            setProtectionParams = setProtectionParams,
        )

    suspend fun generateECKeyPair(
        keyIdentifier: KeyIdentifier?,
        requiresAuthentication: Boolean,
        allowDeviceCredential: Boolean,
        backupKeyHandle: KeyHandle<EncryptionAlgorithm>? = null,
    ): ECKeyPairInfo {
        val preliminaryHandle = KeyHandle(
            usage = KeyUsage.Sign,
            algorithm = ECAlgorithm.ES256,
            requiresAuthentication = requiresAuthentication,
            isStrongBoxBacked = true,
            identifier = keyIdentifier ?: randomKeyIdentifier(),
        )
        Log.i(
            TAG,
            "Generating EC key pair with identifier '${preliminaryHandle.identifier}'" +
            " and algorithm ${preliminaryHandle.algorithm}",
        )
        val keyPair = KeyPairGenerator.getInstance(preliminaryHandle.algorithm.secretKeySpecName).run {
            initialize(ECGenParameterSpec(preliminaryHandle.algorithm.curve))
            generateKeyPair()
        }
        val keyEntry = KeyEntry.create(preliminaryHandle, keyPair)

        val result = ECKeyPairInfo(
            publicKey = keyPair.public as ECPublicKey,
            keyHandle = keyStore.importKey(preliminaryHandle, keyEntry, allowDeviceCredential) {
                setDigests(preliminaryHandle.algorithm.keyStoreDigestName)
            },
            encryptedPrivateKey = backupKeyHandle?.let {
                // use DummyAuthenticator since backup key never requires authentication
                withEncryptionKey(DummyAuthenticator, it) {
                    encrypt(keyPair.private.encoded)
                }
            },
        )

        try {
            Log.d(TAG, "Attempting to destroy private key material")
            keyPair.private.destroy()
        } catch (e: DestroyFailedException) {
            Log.d(TAG, "Destroying private key failed", e)
        }

        return result
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
    Unknown,
}

data class ECKeyPairInfo(
    val keyHandle: KeyHandle<ECAlgorithm>,
    val publicKey: ECPublicKey,
    val encryptedPrivateKey: EncryptedData?,
)

private sealed interface KeyEntry {
    class Symmetric(val entry: KeyStore.SecretKeyEntry) : KeyEntry
    class Asymmetric(val entry: KeyStore.PrivateKeyEntry) : KeyEntry

    companion object {
        fun create(keyHandle: KeyHandle<SymmetricAlgorithm>, key: ByteArray): KeyEntry =
            create(keyHandle, SecretKeySpec(key, keyHandle.algorithm.secretKeySpecName))

        fun create(keyHandle: KeyHandle<SymmetricAlgorithm>, key: SecretKey): KeyEntry =
            Symmetric(KeyStore.SecretKeyEntry(key))

        fun create(keyHandle: KeyHandle<ECAlgorithm>, keyPair: KeyPair): KeyEntry =
            create(keyHandle, keyPair.private as ECPrivateKey)

        fun create(keyHandle: KeyHandle<ECAlgorithm>, privKey: ECPrivateKey): KeyEntry {
            // We never use the certificate, so we just use a static, pre-generated dummy certificate
            // to make keystore happy.
            val entry = KeyStore.PrivateKeyEntry(
                privKey,
                arrayOf(dummyCertificate)
            )
            return Asymmetric(entry)
        }
    }
}

private fun KeyStore.setEntry(keyHandle: KeyHandle<*>, entry: KeyEntry, protParam: KeyStore.ProtectionParameter) {
    when (entry) {
        is KeyEntry.Symmetric -> { setEntry(keyHandle.alias, entry.entry, protParam) }
        is KeyEntry.Asymmetric -> { setEntry(keyHandle.alias, entry.entry, protParam) }
    }
}

private fun <T : KeyAlgorithm> KeyStore.importKey(
    keyHandle: KeyHandle<T>,
    keyEntry: KeyEntry,
    allowDeviceCredential: Boolean,
    setProtectionParams: KeyProtection.Builder.() -> Unit = {},
): KeyHandle<T> {
    Log.i(TAG, "Importing key to preliminary handle ${keyHandle.alias}")
    val protectionParamsBuilder = KeyProtection.Builder(keyHandle.usage.purposes).apply {
        setProtectionParams()
        setIsStrongBoxBacked(true)
        if (keyHandle.requiresAuthentication) {
            setUserAuthenticationRequired(true)
            var allowedAuthTypes = KeyProperties.AUTH_BIOMETRIC_STRONG
            if (allowDeviceCredential) {
                allowedAuthTypes = allowedAuthTypes or KeyProperties.AUTH_DEVICE_CREDENTIAL
            }

            // timeout needs to be >0 if key is symmetric, since symmetric every symmetric operation we do requires
            // more than one call to Android Key Store
            val timeout = if (keyEntry is KeyEntry.Symmetric) {
                Log.i(
                    TAG,
                    "Key ${keyHandle.alias} is symmetric; setting auth timeout to $KEY_AUTHENTICATION_LIFETIME",
                )
                KEY_AUTHENTICATION_LIFETIME
            } else {
                Log.i(
                    TAG,
                    "Key ${keyHandle.alias} is asymmetric; setting auth timeout to 0",
                )
                0
            }
            setUserAuthenticationParameters(timeout, allowedAuthTypes)
        }
    }
    try {
        val updatedKeyHandle = keyHandle.copy(isStrongBoxBacked = true)
        setEntry(updatedKeyHandle, keyEntry, protectionParamsBuilder.build())
        Log.i(
            TAG,
            "Key ${keyHandle.alias} successfully stored in StrongBox",
        )
        return updatedKeyHandle
    } catch (_: KeyStoreException) {
        val updatedKeyHandle = keyHandle.copy(isStrongBoxBacked = false)
        Log.i(
            TAG,
            "Unable to store key ${keyHandle.alias} in StrongBox, retrying without, as ${updatedKeyHandle.alias}",
        )
        protectionParamsBuilder.setIsStrongBoxBacked(false)
        setEntry(updatedKeyHandle, keyEntry, protectionParamsBuilder.build())
        Log.i(
            TAG,
            "Key ${updatedKeyHandle.alias} successfully stored OUTSIDE StrongBox",
        )
        return updatedKeyHandle
    }
}

// Generated using the following command line:
// openssl req -new -x509 -key dummy.key -out dummy.crt -days 3650 -subj "/CN=dummy"
val dummyCertificate: X509Certificate by lazy {
    val certBase64 = """
        MIIBdDCCARugAwIBAgIUTpBIKgyrQBlsaNIisMLiBc483QgwCgYIKoZIzj0EAwIwEDEOMAwGA1UE
        AwwFZHVtbXkwHhcNMjYwMTI2MDgwMTU0WhcNMzYwMTI0MDgwMTU0WjAQMQ4wDAYDVQQDDAVkdW1t
        eTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABKGmqnkuavcn42W1ekMt3TQFeXO4nCfaZL+m7lJy
        SgRH2QHgkHLXuU8x23s1xzMexjtN6ipMJezOZvqXEbnMliOjUzBRMB0GA1UdDgQWBBSpLEsmrTok
        y/gFxGr6QTkcRgox7DAfBgNVHSMEGDAWgBSpLEsmrToky/gFxGr6QTkcRgox7DAPBgNVHRMBAf8E
        BTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIFORWgmTvgjWmS7sQRCiHIrLIIgxhfZGgYNpwOnfziDE
        AiBDTwzb9RoG5vkFa7VA+pNPIRGg1JKlkuOQVPeqJBTduw==
    """.trimIndent()

    val certBytes = Base64.decode(certBase64, Base64.DEFAULT)
    val cf = CertificateFactory.getInstance("X.509")
    cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
}

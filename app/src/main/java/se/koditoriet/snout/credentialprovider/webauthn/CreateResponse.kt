package se.koditoriet.snout.credentialprovider.webauthn

import androidx.credentials.provider.CallingAppInfo
import com.upokecenter.cbor.CBOREncodeOptions
import com.upokecenter.cbor.CBORObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import se.koditoriet.snout.credentialprovider.appInfoToOrigin
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey

/**
 * Represents a response to a WebAuthn credential creation request.
 * publicKey is assumed to be an ES256 public key.
 */
class CreateResponse(
    val rpId: String,
    val credentialId: ByteArray,
    val publicKey: ECPublicKey,
    val callingAppInfo: CallingAppInfo,
    val flags: Set<AuthDataFlag>,
) {
    val cosePublicKey: ByteArray by lazy {
        val xBytes = toUnsignedFixedLength(publicKey.w.affineX, 32)
        val yBytes = toUnsignedFixedLength(publicKey.w.affineY, 32)

        CBORObject.NewMap().apply {
            set(1, CBORObject.FromObject(2))
            set(3, CBORObject.FromObject(-7))
            set(-1, CBORObject.FromObject(1))
            set(-2, CBORObject.FromObject(xBytes))
            set(-3, CBORObject.FromObject(yBytes))
        }.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical)
    }

    val origin: String by lazy {
        appInfoToOrigin(callingAppInfo)
    }

    val authenticatorData: ByteArray by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        val rpHash = md.digest(rpId.toByteArray(Charsets.UTF_8))
        val flags = byteArrayOf((flags + setOf(AuthDataFlag.AT)).toByte())
        val signCount = ByteArray(4)
        val aaguid = ByteArray(16)
        val credIdLen = byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte())
        rpHash + flags + signCount + aaguid + credIdLen + credentialId + cosePublicKey
    }

    val attestationObject: ByteArray by lazy {
        CBORObject.NewMap().apply {
            set("fmt", CBORObject.FromObject("none"))
            set("attStmt", CBORObject.NewMap())
            set("authData", CBORObject.FromObject(authenticatorData))
        }.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical)
    }

    val response by lazy {
        Response(
            transports = listOf("internal"),
            origin = origin,
            androidPackageName = callingAppInfo.packageName,
            publicKeyAlgorithm = -7,
            publicKey = publicKey.encoded.toBase64Url().string,
            authenticatorData = authenticatorData.toBase64Url().string,
            attestationObject = attestationObject.toBase64Url().string,
        )
    }

    val credential by lazy {
        Credential(
            type = "public-key",
            id = credentialId.toBase64Url().string,
            rawId = credentialId.toBase64Url().string,
            response = response,
            authenticatorAttachment = "platform",
            publicKeyAlgorithm = -7,
            clientExtensionResults = emptyMap(),
        )
    }

    @Serializable
    class Response(
        val transports: List<String>,
        val origin: String,
        val androidPackageName: String,
        val publicKeyAlgorithm: Int,
        val publicKey: String,
        val authenticatorData: String,
        val attestationObject: String,
    )

    @Serializable
    class Credential(
        val type: String,
        val id: String,
        val rawId: String,
        val response: Response,
        val authenticatorAttachment: String,
        val publicKeyAlgorithm: Int,
        val clientExtensionResults: Map<String, String>,
    )

    val json: String by lazy { Json.encodeToString(credential) }
}

private fun toUnsignedFixedLength(
    value: BigInteger,
    size: Int
): ByteArray {
    require(value.signum() >= 0)
    val raw = value.toByteArray()

    val unsigned = if (raw.size > 1 && raw[0] == 0.toByte()) {
        raw.copyOfRange(1, raw.size)
    } else {
        raw
    }

    require(unsigned.size <= size)
    return ByteArray(size - unsigned.size) + unsigned
}

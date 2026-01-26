package se.koditoriet.snout.codec.webauthn

import androidx.credentials.provider.CallingAppInfo
import com.upokecenter.cbor.CBOREncodeOptions
import com.upokecenter.cbor.CBORObject
import org.json.JSONArray
import org.json.JSONObject
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.UserId
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey

/**
 * Represents a response to a WebAuthn credential creation request.
 * publicKey is assumed to be an ES256 public key.
 */
class WebAuthnCreateResponse(
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

    val response: JSONObject by lazy {
        JSONObject().apply {
            put("transports", JSONArray(listOf("internal", "hybrid")))
            put("origin", origin)
            put("androidPackageName", callingAppInfo.packageName)
            put("publicKeyAlgorithm", -7)
            put("publicKey", publicKey.encoded.toBase64Url().string)
            put("authenticatorData", authenticatorData.toBase64Url().string)
            put("attestationObject", attestationObject.toBase64Url().string)
        }
    }

    val credential: JSONObject by lazy {
        JSONObject().apply {
            put("type", "public-key")
            put("id", credentialId.toBase64Url().string)
            put("rawId", credentialId.toBase64Url().string)
            put("response", response)
            put("authenticatorAttachment", "platform")
            put("publicKeyAlgorithm", -7)
            put("clientExtensionResults", JSONObject())
        }
    }

    val json: String by lazy { credential.toString() }
}

class WebAuthnAuthResponse(
    val rpId: String,
    val credentialId: CredentialId,
    val userId: UserId?,
    val flags: Set<AuthDataFlag>,
    val clientDataHash: ByteArray,
) {
    val authenticatorData: ByteArray by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        val rpHash = md.digest(rpId.toByteArray(Charsets.UTF_8))
        val flags = byteArrayOf(flags.toByte())
        val signCount = ByteArray(4)
        rpHash + flags + signCount
    }

    suspend fun sign(signer: suspend (ByteArray) -> ByteArray): SignedWebAuthnAuthResponse {
        val signData = authenticatorData + clientDataHash
        val signature = signer(signData)
        return SignedWebAuthnAuthResponse(
            authenticatorData = authenticatorData.toBase64Url(),
            signature = signature.toBase64Url(),
            userId = userId,
            credentialId = credentialId,
        )
    }
}

class SignedWebAuthnAuthResponse(
    val authenticatorData: Base64Url,
    val signature: Base64Url,
    val userId: UserId?,
    val credentialId: CredentialId,
) {
    val response: JSONObject by lazy {
        JSONObject().apply {
            put("clientDataJSON", "dummy value; Android replaces it anyway")
            put("authenticatorData", authenticatorData.string)
            put("signature", signature.string)
            if (userId != null) {
                put("userHandle", userId.string)
            }
        }
    }

    val credential: JSONObject by lazy {
        JSONObject().apply {
            put("type", "public-key")
            put("id", credentialId.string)
            put("rawId", credentialId.string)
            put("response", response)
            put("clientExtensionResults", JSONObject())
        }
    }

    val json: String by lazy { credential.toString() }
}

fun appInfoToOrigin(callingAppInfo: CallingAppInfo): String {
    val populatedOrigin = callingAppInfo.getOrigin(privilegedAllowlist)
    return if (populatedOrigin != null) {
        populatedOrigin.trimEnd('/')
    } else {
        val cert = callingAppInfo.signingInfo.apkContentsSigners[0].toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val certHash = md.digest(cert)
        "android:apk-key-hash:${certHash.toBase64Url().string}"
    }
}

@JvmInline
value class AuthDataFlag(val flag: Int) {
    companion object {
        /**
         * User Presence
         */
        val UP: AuthDataFlag = AuthDataFlag(0x01)

        /**
         * User Verification
         */
        val UV: AuthDataFlag = AuthDataFlag(0x04)

        /**
         * Backup Eligibility
         */
        val BE: AuthDataFlag = AuthDataFlag(0x08)

        /**
         * Backup Status
         */
        val BS: AuthDataFlag = AuthDataFlag(0x10)

        /**
         * Attestation data attached (this is always set automatically for credential creation)
         */
        val AT: AuthDataFlag = AuthDataFlag(0x40)

        val defaultCreateFlags: Set<AuthDataFlag> = setOf(UP, UV, BE)
        val defaultAuthFlags: Set<AuthDataFlag> = setOf(UP, UV, BE)
    }
}

private fun Set<AuthDataFlag>.toInt(): Int =
    fold(0) { a, b -> a or b.flag }

private fun Set<AuthDataFlag>.toByte(): Byte =
    toInt().toByte()

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

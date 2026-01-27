package se.koditoriet.snout.credentialprovider.webauthn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import java.security.MessageDigest

class AuthResponse(
    val rpId: String,
    val credentialId: Base64Url,
    val userId: Base64Url?,
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

    suspend fun sign(signer: suspend (ByteArray) -> ByteArray): SignedAuthResponse {
        val signData = authenticatorData + clientDataHash
        val signature = signer(signData)
        return SignedAuthResponse(
            authenticatorData = authenticatorData.toBase64Url(),
            signature = signature.toBase64Url(),
            userId = userId,
            credentialId = credentialId,
        )
    }
}

class SignedAuthResponse(
    val authenticatorData: Base64Url,
    val signature: Base64Url,
    val userId: Base64Url?,
    val credentialId: Base64Url,
) {
    val response by lazy {
        Response(
            clientDataJSON = "dummy value; Android replaces it anyway",
            authenticatorData = authenticatorData.string,
            signature = signature.string,
            userHandle = userId?.string,
        )
    }

    val credential by lazy {
        Credential(
            type = "public-key",
            id = credentialId.string,
            rawId = credentialId.string,
            response = response,
            clientExtensionResults = emptyMap(),
            authenticatorAttachment = "platform",
        )
    }

    @Serializable
    class Response(
        val authenticatorData: String,
        val signature: String,
        val userHandle: String?,
        val clientDataJSON: String,
    )

    @Serializable
    class Credential(
        val type: String,
        val id: String,
        val rawId: String,
        val response: Response,
        val clientExtensionResults: Map<String, String>,
        val authenticatorAttachment: String,
    )

    val json: String by lazy {
        Json.encodeToString(credential)
    }
}

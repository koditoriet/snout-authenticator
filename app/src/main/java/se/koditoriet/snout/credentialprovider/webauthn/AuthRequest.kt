package se.koditoriet.snout.credentialprovider.webauthn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.koditoriet.snout.codec.Base64Url

/**
 * Contains the subset of PublicKeyCredentialRequestOptions that we use.
 */
@Serializable
class AuthRequest(
    val challenge: Base64Url,
    val rpId: String,
    val userVerification: String,
    val timeout: Int,
    val allowCredentials: List<CredentialDescriptor> = emptyList(),
) {
    @Serializable
    class CredentialDescriptor(
        val id: Base64Url,
        val type: String,
        val transports: List<String>,
    )
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJSON(s: String): AuthRequest =
            json.decodeFromString(s)
    }
}

package se.koditoriet.snout.credentialprovider.webauthn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.koditoriet.snout.codec.Base64Url

/**
 * Contains the subset of PublicKeyCredentialCreationOptions that we use.
 */
@Serializable
class PublicKeyCredentialCreationOptions(
    val rp: RP,
    val user: User,
    val timeout: Int? = null,
    val excludeCredentials: List<PublicKeyCredentialDescriptor> = emptyList(),
) {
    @Serializable
    class RP(val id: String)

    @Serializable
    class User(
        val id: Base64Url,
        val displayName: String,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJSON(s: String): PublicKeyCredentialCreationOptions =
            json.decodeFromString(s)
    }
}


/**
 * Contains the subset of PublicKeyCredentialRequestOptions that we use.
 */
@Serializable
class PublicKeyCredentialRequestOptions(
    val challenge: Base64Url,
    val rpId: String? = null,
    val userVerification: String = "preferred",
    val timeout: Int? = null,
    val allowCredentials: List<PublicKeyCredentialDescriptor> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJSON(s: String): PublicKeyCredentialRequestOptions =
            json.decodeFromString(s)
    }
}

@Serializable
class PublicKeyCredentialDescriptor(
    val id: Base64Url,
    val type: String,
    val transports: List<String> = emptyList(),
)

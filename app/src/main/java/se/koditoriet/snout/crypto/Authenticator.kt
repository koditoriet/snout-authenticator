package se.koditoriet.snout.crypto

import java.security.Signature

/**
 * An object that contains enough information to create a new authenticator if given a reason and subtitle for a prompt.
 */
interface AuthenticatorFactory {
    fun withReason(reason: String, subtitle: String): Authenticator

    suspend fun <T> withReason(reason: String, subtitle: String, action: suspend (Authenticator) -> T): T =
        action(withReason(reason, subtitle))
}

interface Authenticator {
    suspend fun <T> authenticate(authenticatedAction: suspend () -> T): T
    suspend fun <T> authenticate(sig: Signature, authenticatedAction: suspend (Signature) -> T): T
}

/**
 * Authenticator that always fails.
 */
object DummyAuthenticator : Authenticator {
    override suspend fun <T> authenticate(authenticatedAction: suspend () -> T) =
        throw AuthenticationFailedException(
            "Authentication always fails with DummyAuthenticator"
        )

    override suspend fun <T> authenticate(sig: Signature, authenticatedAction: suspend (Signature) -> T): T =
        throw AuthenticationFailedException(
            "Authentication always fails with DummyAuthenticator"
        )

    class Factory : AuthenticatorFactory {
        override fun withReason(reason: String, subtitle: String) =
            DummyAuthenticator
    }
}

data class AuthenticationFailedException(val reason: String) : Exception(reason)

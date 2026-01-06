package se.koditoriet.snout.crypto

interface CipherAuthenticator {
    suspend fun <T> authenticate(authenticatedAction: () -> T): T

    val reason: String?
        get() = reasonStack.lastOrNull()?.first

    val subtitle: String?
        get() = reasonStack.lastOrNull()?.second

    companion object {
        suspend fun <T> withReason(
            reason: String,
            subtitle: String,
            action: suspend () -> T,
        ): T {
            reasonStack.add(Pair(reason, subtitle))
            try {
                return action()
            } finally {
                reasonStack.removeLastOrNull()
            }
        }

        private val reasonStack: MutableList<Pair<String, String>> = mutableListOf()
    }
}

/**
 * CipherAuthenticator that always fails.
 */
object AlwaysFailCipherAuthenticator : CipherAuthenticator {
    override suspend fun <T> authenticate(authenticatedAction: () -> T) =
        throw AuthenticationFailedException(
            "Authentication always fails with AlwaysFailCipherAuthenticator"
        )

}

data class AuthenticationFailedException(val reason: String) : Exception(reason)

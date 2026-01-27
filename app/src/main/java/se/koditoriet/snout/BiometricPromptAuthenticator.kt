package se.koditoriet.snout

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.crypto.Authenticator
import se.koditoriet.snout.crypto.AuthenticatorFactory
import java.security.Signature
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "BiometricPromptAuthenticator"

class BiometricPromptAuthenticator(
    private val activity: FragmentActivity,
    private val reason: String,
    private val subtitle: String,
) : Authenticator {
    override suspend fun <T> authenticate(authenticatedAction: suspend () -> T): T =
        authenticate(null) {
            authenticatedAction()
        }

    override suspend fun <T> authenticate(sig: Signature, authenticatedAction: suspend (Signature) -> T): T =
        authenticate(BiometricPrompt.CryptoObject(sig)) {
            authenticatedAction(it?.signature!!)
        }

    private suspend fun <T> authenticate(
        cryptoObject: BiometricPrompt.CryptoObject?,
        authenticatedAction: suspend (BiometricPrompt.CryptoObject?) -> T,
    ): T {
        Log.i(TAG, "Authenticating user")
        val result = withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                val callback = AuthenticationCallback(continuation, authenticatedAction)
                val prompt = BiometricPrompt(activity, callback)
                when (cryptoObject) {
                    null -> prompt.authenticate(promptInfo)
                    else -> prompt.authenticate(promptInfo, cryptoObject)
                }
            }
        }
        return result?.invoke() ?: throw AuthenticationFailedException("user canceled authentication")
    }

    private val promptInfo by lazy {
        BiometricPrompt.PromptInfo.Builder().apply {
            setTitle(reason)
            setSubtitle(subtitle)
            setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }.build()
    }

    class Factory(private val activity: FragmentActivity) : AuthenticatorFactory {
        override fun withReason(reason: String, subtitle: String) =
            BiometricPromptAuthenticator(activity, reason, subtitle)
    }
}

private class AuthenticationCallback<T>(
    private val continuation: Continuation<(suspend () -> T)?>,
    private val onSuccess: suspend (BiometricPrompt.CryptoObject?) -> T,
) : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        Log.i(TAG, "Authentication succeeded")
        continuation.resume({ onSuccess(result.cryptoObject) })
    }

    override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        Log.d(TAG, "Authentication failed")
        // Do nothing here; we'll be getting more callbacks as the user keeps trying
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        Log.w(TAG, "Authentication errored ($errorCode): $errString")
        continuation.resume(null)
    }
}

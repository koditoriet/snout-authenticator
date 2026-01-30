package se.koditoriet.snout.credentialprovider.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.credentialprovider.CREDENTIAL_DATA
import se.koditoriet.snout.credentialprovider.CREDENTIAL_ID
import se.koditoriet.snout.credentialprovider.appInfoToRpId
import se.koditoriet.snout.credentialprovider.originIsValid
import se.koditoriet.snout.credentialprovider.rpIsValid
import se.koditoriet.snout.credentialprovider.webauthn.AuthDataFlag
import se.koditoriet.snout.credentialprovider.webauthn.AuthResponse
import se.koditoriet.snout.credentialprovider.webauthn.PublicKeyCredentialRequestOptions
import se.koditoriet.snout.credentialprovider.webauthn.SignedAuthResponse
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.components.InformationDialog
import se.koditoriet.snout.ui.components.PasskeyIcon
import se.koditoriet.snout.ui.screens.EmptyScreen
import se.koditoriet.snout.ui.snoutApp
import se.koditoriet.snout.ui.theme.BACKGROUND_ICON_SIZE
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.Passkey
import se.koditoriet.snout.viewmodel.SnoutViewModel

private val TAG = "AuthenticateActivity"

class AuthenticateActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenStrings = appStrings.credentialProvider
        val authFactory = BiometricPromptAuthenticator.Factory(this@AuthenticateActivity)

        enableEdgeToEdge()
        setContent {
            val viewModel = viewModel<SnoutViewModel>()
            val showUnableToEstablishTrustDialog = remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                try {
                    viewModel.unlockVault(authFactory)
                } catch (_: AuthenticationFailedException) {
                    finishWithResult(null)
                    return@LaunchedEffect
                }

                val requestInfo = GetRequestInfo.fromIntent(intent)

                Log.d(TAG, "Fetching passkey with credential ID ${requestInfo.credentialId}")
                val passkey = viewModel.getPasskey(requestInfo.credentialId)

                if (!requestInfo.isValid(passkey)) {
                    showUnableToEstablishTrustDialog.value = true
                    return@LaunchedEffect
                }

                val response = AuthResponse(
                    rpId = passkey.rpId,
                    credentialId = passkey.credentialId.id,
                    userId = passkey.userId.id,
                    flags = AuthDataFlag.defaultAuthFlags,
                    clientDataHash = requestInfo.clientDataHash,
                )

                try {
                    val signedResponse = response.sign {
                        Log.i(TAG, "Signing authentication request")
                        viewModel.signWithPasskey(authFactory, passkey, it)
                    }
                    finishWithResult(signedResponse)
                } catch (_: AuthenticationFailedException) {
                    Log.i(TAG, "Aborting signing")
                    finishWithResult(null)
                }
            }

            SnoutTheme {
                EmptyScreen {
                    PasskeyIcon(Modifier.size(BACKGROUND_ICON_SIZE))
                }

                if (showUnableToEstablishTrustDialog.value) {
                    InformationDialog(
                        title = screenStrings.unableToEstablishTrust,
                        text = screenStrings.unableToEstablishTrustExplanation,
                        onDismiss = { finishWithResult(null) },
                    )
                }
            }
        }
    }

    private fun finishWithResult(signedResponse: SignedAuthResponse?) {
        Intent().let { intent ->
            if (signedResponse != null) {
                val publicKeyCredential = PublicKeyCredential(signedResponse.json)
                PendingIntentHandler.setGetCredentialResponse(
                    intent = intent,
                    response = GetCredentialResponse(publicKeyCredential)
                )
                setResult(RESULT_OK, intent)
            } else {
                Log.i(TAG, "Aborting signing")
                setResult(RESULT_CANCELED, intent)
            }
        }
        snoutApp.startIdleTimeout()
        Log.d(TAG, "Finishing activity")
        finish()
    }
}

private class GetRequestInfo(
    val credentialId: CredentialId,
    val callingAppInfo: CallingAppInfo,
    val clientDataHash: ByteArray,
    val requestJson: PublicKeyCredentialRequestOptions,
) {
    val rpId by lazy {
        requestJson.rpId ?: appInfoToRpId(callingAppInfo)
    }

    fun isValid(storedPasskey: Passkey): Boolean {
        if (!rpIsValid(rpId)) {
            Log.e(TAG, "Request RP is invalid!")
            return false
        }

        if (!originIsValid(callingAppInfo, rpId)) {
            Log.e(TAG, "Origin is invalid!")
            return false
        }

        if (storedPasskey.rpId != rpId) {
            Log.e(TAG, "Request RP does not match passkey RP!")
            return false
        }

        return true
    }

    companion object {
        fun fromIntent(intent: Intent): GetRequestInfo {
            Log.d(TAG, "Extracting selected credential ID")
            val credentialId = intent
                .getBundleExtra(CREDENTIAL_DATA)!!
                .getString(CREDENTIAL_ID)!!

            Log.i(TAG, "User requested signing with credential $credentialId")

            Log.d(TAG, "Extracting credential options")
            val request = PendingIntentHandler
                .retrieveProviderGetCredentialRequest(intent)!!

            val credentialOption = request.credentialOptions.first() as GetPublicKeyCredentialOption

            Log.d(TAG, "Parsing request JSON")
            return GetRequestInfo(
                credentialId = CredentialId.fromString(credentialId),
                callingAppInfo = request.callingAppInfo,
                clientDataHash = credentialOption.clientDataHash!!,
                requestJson = PublicKeyCredentialRequestOptions.fromJSON(credentialOption.requestJson),
            )
        }
    }
}

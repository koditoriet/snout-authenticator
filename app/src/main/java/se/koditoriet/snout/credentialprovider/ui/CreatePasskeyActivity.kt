package se.koditoriet.snout.credentialprovider.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.credentialprovider.originIsValid
import se.koditoriet.snout.credentialprovider.rpIsValid
import se.koditoriet.snout.credentialprovider.webauthn.AuthDataFlag
import se.koditoriet.snout.credentialprovider.webauthn.PublicKeyCredentialCreationOptions
import se.koditoriet.snout.credentialprovider.webauthn.CreateResponse
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.components.InformationDialog
import se.koditoriet.snout.ui.screens.EmptyScreen
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.viewmodel.SnoutViewModel

private val TAG = "CreatePasskeyActivity"

class CreatePasskeyActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenStrings = appStrings.credentialProvider
        val authFactory = BiometricPromptAuthenticator.Factory(this@CreatePasskeyActivity)
        val requestInfo = CreateRequestInfo.fromIntent(intent!!)

        enableEdgeToEdge()
        setContent {
            var passkeyAlreadyExists by remember { mutableStateOf(false) }
            var invalidRequestInfo by remember { mutableStateOf(false) }
            val viewModel = viewModel<SnoutViewModel>()

            LaunchedEffect(Unit) {
                if (!requestInfo.isValid) {
                    invalidRequestInfo = true
                    return@LaunchedEffect
                }

                try {
                    viewModel.unlockVault(authFactory)
                } catch (_: AuthenticationFailedException) {
                    finishWithResponse(null)
                    return@LaunchedEffect
                }

                val response = createPasskey(viewModel, requestInfo)
                if (response != null) {
                    Log.i(
                        TAG,
                        "Created passkey with credential id ${response.credentialId}"
                    )
                    finishWithResponse(response)
                } else {
                    passkeyAlreadyExists = true
                }
            }

            SnoutTheme {
                EmptyScreen {
                    if (passkeyAlreadyExists) {
                        InformationDialog(
                            title = screenStrings.passkeyAlreadyExists,
                            text = screenStrings.passkeyAlreadyExistsExplanation,
                            onDismiss = { finishWithResponse(null) }
                        )
                    }
                    if (invalidRequestInfo) {
                        InformationDialog(
                            title = screenStrings.unableToEstablishTrust,
                            text = screenStrings.unableToEstablishTrustExplanation,
                            onDismiss = { finishWithResponse(null) }
                        )
                    }
                }
            }
        }
    }

    private suspend fun createPasskey(viewModel: SnoutViewModel, requestInfo: CreateRequestInfo): CreateResponse? {
        val excludeCredentials = requestInfo.requestJson.excludeCredentials.map { CredentialId(it.id) }
        val excludedCredentials = viewModel.passkeys.first().filter {
            it.credentialId in excludeCredentials
        }

        if (excludedCredentials.isNotEmpty()) {
            // If any credential in excludeCredentials is already in the vault, we already have a credential that is
            // recognized by both us and the RP, so we should not create a new one.
            val excluded = excludedCredentials.joinToString(", ") { it.toString() }
            Log.i(
                TAG,
                "Existing passkeys with credential ids in excludeCredentials: $excluded",
            )
            return null
        }

        val (credentialId, pubkey) = viewModel.addPasskey(
            rpId = requestInfo.requestJson.rp.id,
            userId = requestInfo.requestJson.user.id.toByteArray(),
            userName = requestInfo.requestJson.user.displayName,
            displayName = requestInfo.requestJson.user.displayName,
        )

        return CreateResponse(
            rpId = requestInfo.requestJson.rp.id,
            credentialId = credentialId.toByteArray(),
            publicKey = pubkey,
            callingAppInfo = requestInfo.callingAppInfo,
            flags = AuthDataFlag.defaultCreateFlags,
        )
    }

    private fun finishWithResponse(response: CreateResponse?) {
        Intent().apply {
            if (response != null) {
                PendingIntentHandler.setCreateCredentialResponse(
                    intent = this,
                    response = CreatePublicKeyCredentialResponse(response.json)
                )
                setResult(RESULT_OK, this)
            } else {
                Log.i(TAG, "Aborting passkey creation")
                setResult(RESULT_CANCELED, this)
            }
        }
        finish()
    }
}

private class CreateRequestInfo(
    val callingAppInfo: CallingAppInfo,
    val requestJson: PublicKeyCredentialCreationOptions,
) {
    val isValid: Boolean by lazy {
        if (!rpIsValid(requestJson.rp.id)) {
            Log.e(TAG, "Request RP is invalid!")
            return@lazy false
        }

        if (!originIsValid(callingAppInfo, requestJson.rp.id)) {
            Log.e(TAG, "Origin is invalid!")
            return@lazy false
        }

        true
    }

    companion object {
        fun fromIntent(intent: Intent): CreateRequestInfo {
            Log.d(TAG, "Extracting credential options")

            val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)!!
            val publicKeyCredentialRequest = request.callingRequest as CreatePublicKeyCredentialRequest

            Log.d(TAG, "Parsing request JSON")
            return CreateRequestInfo(
                callingAppInfo = request.callingAppInfo,
                requestJson = PublicKeyCredentialCreationOptions.fromJSON(publicKeyCredentialRequest.requestJson),
            )
        }
    }
}

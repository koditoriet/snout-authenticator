package se.koditoriet.snout.credentialprovider

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
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
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.codec.webauthn.AuthDataFlag
import se.koditoriet.snout.codec.webauthn.WebAuthnCreateResponse
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
        val screenStrings = appStrings.createPasskeyScreen

        enableEdgeToEdge()
        setContent {
            var passkeyAlreadyExists by remember { mutableStateOf(false) }
            val viewModel = viewModel<SnoutViewModel>()

            LaunchedEffect(Unit) {
                try {
                    val authFactory = BiometricPromptAuthenticator.Factory(this@CreatePasskeyActivity)
                    viewModel.unlockVault(authFactory)
                } catch (_: AuthenticationFailedException) {
                    finishWithResponse(null)
                    return@LaunchedEffect
                }

                val response = createPasskey(viewModel)
                if (response != null) {
                    Log.i(
                        TAG,
                        "Created passkey with credential id ${response.credentialId}" +
                                " for RP ${response.rpId} at origin ${response.origin}"
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
                }
            }
        }
    }

    private suspend fun createPasskey(viewModel: SnoutViewModel): WebAuthnCreateResponse? {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)!!
        val actualRequest = request.callingRequest as CreatePublicKeyCredentialRequest
        val requestOptions = JSONObject(actualRequest.requestJson)

        val excludeCredentials = requestOptions.optJSONArray("excludeCredentials")?.map {
            CredentialId.fromString(it.getString("id"))
        } ?: emptyList()

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

        val rpId = requestOptions.getJSONObject("rp").getString("id")
        val user = requestOptions.getJSONObject("user")

        val (credentialId, pubkey) = viewModel.addPasskey(
            rpId = rpId,
            userId = Base64Url.fromBase64UrlString(user.getString("id")).toByteArray(),
            userName = user.getString("name"),
            displayName = user.getString("displayName"),
        )

        return WebAuthnCreateResponse(
            rpId = rpId,
            credentialId = credentialId.toByteArray(),
            publicKey = pubkey,
            callingAppInfo = request.callingAppInfo,
            flags = AuthDataFlag.defaultCreateFlags,
        )
    }
}

private fun Activity.finishWithResponse(response: WebAuthnCreateResponse?) {
    Intent().apply {
        if (response != null) {
            PendingIntentHandler.setCreateCredentialResponse(
                this,
                CreatePublicKeyCredentialResponse(response.json)
            )
            setResult(RESULT_OK, this)
        } else {
            Log.i(TAG, "Aborting passkey creation")
            setResult(RESULT_CANCELED, this)
        }
    }
    finish()
}

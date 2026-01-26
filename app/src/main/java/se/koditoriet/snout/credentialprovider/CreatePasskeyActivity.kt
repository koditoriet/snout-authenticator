package se.koditoriet.snout.credentialprovider

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.codec.webauthn.AuthDataFlag
import se.koditoriet.snout.codec.webauthn.WebAuthnCreateResponse
import se.koditoriet.snout.ui.components.InformationDialog
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.viewmodel.SnoutViewModel
import kotlin.getValue


class CreatePasskeyActivity : FragmentActivity() {
    private val TAG = "CreatePasskeyActivity"
    private val viewModel: SnoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var passkeyAlreadyExists by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val response = createPasskey()
                if (response != null) {
                    Intent().apply {
                        PendingIntentHandler.setCreateCredentialResponse(
                            this,
                            CreatePublicKeyCredentialResponse(response.json)
                        )
                        setResult(RESULT_OK, this)
                    }
                    finish()
                } else {
                    passkeyAlreadyExists = true
                }
            }

            if (passkeyAlreadyExists) {
                // TODO: strings
                InformationDialog(
                    title = "Passkey already exists",
                    text = "You already have an active passkey for this service. Try to sign in with that one instead.",
                    onDismiss = { finish() }
                )

                // TODO: setResult?
                // TODO: theme
            }
        }
    }

    private suspend fun createPasskey(): WebAuthnCreateResponse? {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)!!
        val actualRequest = request.callingRequest as CreatePublicKeyCredentialRequest
        val requestOptions = JSONObject(actualRequest.requestJson)

        val excludeCredentials = requestOptions.optJSONArray("excludeCredentials")?.map {
            CredentialId.fromString(it.getString("id"))
        } ?: emptyList()

        if (viewModel.passkeys.first().any { it.credentialId in excludeCredentials }) {
            // If any credential in excludeCredentials is already in the vault, we already have a credential that is
            // recognized by both us and the RP, so we should not create a new one.
            return null
        }

        val rpId = requestOptions.getJSONObject("rp").getString("id")
        val user = requestOptions.getJSONObject("user")

        val authFactory = BiometricPromptAuthenticator.Factory(this@CreatePasskeyActivity)
        viewModel.unlockVault(authFactory)

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

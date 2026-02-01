package se.koditoriet.snout.ui.activities.credentialprovider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.credentialprovider.originIsValid
import se.koditoriet.snout.credentialprovider.rpIsValid
import se.koditoriet.snout.credentialprovider.webauthn.AuthDataFlag
import se.koditoriet.snout.credentialprovider.webauthn.CreateResponse
import se.koditoriet.snout.credentialprovider.webauthn.PublicKeyCredentialCreationOptions
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.components.BadInputInformationDialog
import se.koditoriet.snout.ui.components.PasskeyIcon
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.onIOThread
import se.koditoriet.snout.ui.components.ThemedEmptySpace
import se.koditoriet.snout.ui.components.WarningInformationDialog
import se.koditoriet.snout.ui.screens.main.passkeys.sheets.EditPasskeyNameSheet
import se.koditoriet.snout.ui.theme.BACKGROUND_ICON_SIZE
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.Passkey
import se.koditoriet.snout.viewmodel.SnoutViewModel

private const val TAG = "CreatePasskeyActivity"

class CreatePasskeyActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenStrings = appStrings.credentialProvider
        val authFactory = BiometricPromptAuthenticator.Factory(this@CreatePasskeyActivity)
        val requestInfo = CreateRequestInfo.fromIntent(intent!!)

        enableEdgeToEdge()
        setContent {
            Log.d(TAG, "Starting activity")
            val viewModel = viewModel<SnoutViewModel>()
            val passkeys by viewModel.passkeys.collectAsState(emptyList())
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            LaunchedEffect(Unit) {
                try {
                    viewModel.unlockVault(authFactory)
                } catch (_: AuthenticationFailedException) {
                    finishWithResponse(null)
                    return@LaunchedEffect
                }
            }

            SnoutTheme {
                ThemedEmptySpace {
                    PasskeyIcon(Modifier.size(BACKGROUND_ICON_SIZE))

                    if (credentialAlreadyExists(passkeys, requestInfo)) {
                        BadInputInformationDialog(
                            title = screenStrings.passkeyAlreadyExists,
                            text = screenStrings.passkeyAlreadyExistsExplanation,
                            onDismiss = { finishWithResponse(null) }
                        )
                    }
                    if (!requestInfo.isValid) {
                        WarningInformationDialog(
                            title = screenStrings.unableToEstablishTrust,
                            text = screenStrings.unableToEstablishTrustExplanation,
                            onDismiss = { finishWithResponse(null) }
                        )
                    }
                    BottomSheet(
                        hideSheet = { finishWithResponse(null) },
                        sheetState = sheetState,
                        sheetViewState = Unit,
                    ) { _ ->
                        EditPasskeyNameSheet(
                            prefilledDisplayName = requestInfo.requestJson.rp.id,
                            onSave = onIOThread { displayName ->
                                val response = createPasskey(viewModel, displayName, requestInfo)
                                Log.i(
                                    TAG,
                                    "Created passkey with credential id ${response.credentialId}"
                                )
                                finishWithResponse(response)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun credentialAlreadyExists(passkeys: List<Passkey>, requestInfo: CreateRequestInfo): Boolean {
        val excludeCredentials = requestInfo.requestJson.excludeCredentials.map { CredentialId(it.id) }
        val excludedCredentials = passkeys.filter {
            it.credentialId in excludeCredentials
        }

        // If any credential in excludeCredentials is already in the vault, we already have a credential that is
        // recognized by both us and the RP, so we should not create a new one.
        return excludedCredentials.isNotEmpty()
    }

    private suspend fun createPasskey(
        viewModel: SnoutViewModel,
        displayName: String,
        requestInfo: CreateRequestInfo,
    ): CreateResponse {
        val (credentialId, pubkey) = viewModel.addPasskey(
            rpId = requestInfo.requestJson.rp.id,
            userId = requestInfo.requestJson.user.id.toByteArray(),
            userName = requestInfo.requestJson.user.displayName,
            displayName = displayName,
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
        Log.d(TAG, "Finishing activity")
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

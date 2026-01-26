package se.koditoriet.snout.credentialprovider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.codec.webauthn.AuthDataFlag
import se.koditoriet.snout.codec.webauthn.WebAuthnAuthResponse
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.viewmodel.SnoutViewModel
import kotlin.getValue


class AuthenticateActivity : FragmentActivity() {
    private val TAG = "AuthenticateActivity"

    private val viewModel: SnoutViewModel by viewModels()

    private val supervisorJob by lazy {
        SupervisorJob()
    }

    private val scope by lazy {
        CoroutineScope(Dispatchers.IO + supervisorJob)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val authFactory = BiometricPromptAuthenticator.Factory(this@AuthenticateActivity)
            val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)!!
            val publicKeyRequest = request.credentialOptions.first() as GetPublicKeyCredentialOption
            val requestInfo = intent.getBundleExtra(CREDENTIAL_DATA)
            val credentialId = CredentialId.fromString(requestInfo!!.getString(CREDENTIAL_ID)!!)

            Log.i(TAG, "Authentication with credential $credentialId requested; fetching passkey")
            val passkey = viewModel.getPasskey(credentialId)

            val response = WebAuthnAuthResponse(
                rpId = passkey.rpId,
                credentialId = passkey.credentialId,
                userId = passkey.userId,
                flags = AuthDataFlag.defaultAuthFlags,
                clientDataHash = publicKeyRequest.clientDataHash!!,
            )

            val signedResponse = response.sign {
                Log.i(TAG, "Signing authentication request")
                viewModel.signWithPasskey(authFactory, passkey, it)
            }
            val publicKeyCredential = PublicKeyCredential(signedResponse.json)

            Intent().apply {
                PendingIntentHandler.setGetCredentialResponse(
                    this,
                    GetCredentialResponse(publicKeyCredential)
                )
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }
}

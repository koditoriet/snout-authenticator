package se.koditoriet.snout.credentialprovider.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.SnoutApp
import se.koditoriet.snout.credentialprovider.createBeginGetCredentialResponse
import se.koditoriet.snout.credentialprovider.webauthn.SignedAuthResponse
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.screens.EmptyScreen
import se.koditoriet.snout.ui.snoutApp
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.viewmodel.SnoutViewModel
import kotlin.getValue

private const val TAG = "UnlockVaultActivity"

class UnlockVaultActivity : FragmentActivity() {
    private val viewModel: SnoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "UnlockVaultActivity created, proceeding to unlock vault")
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)!!

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                try {
                    snoutApp.cancelIdleTimeout()
                    viewModel.unlockVault(BiometricPromptAuthenticator.Factory(this@UnlockVaultActivity))
                } catch (_: AuthenticationFailedException) {
                    finishWithResult(null)
                    return@LaunchedEffect
                }

                Log.i(TAG, "Vault successfully unlocked, creating credential response")
                (application as SnoutApp).vault.withLock {
                    val response = createBeginGetCredentialResponse(
                        vault = this,
                        context = applicationContext,
                        request = request,
                    )

                    Log.i(TAG, "Sending BeginGetCredentialResponse to credential manager")
                    finishWithResult(response)
                }
            }
            SnoutTheme {
                EmptyScreen()
            }
        }
    }

    private fun finishWithResult(response: BeginGetCredentialResponse?) {
        Intent().let { intent ->
            if (response != null) {
                PendingIntentHandler.setBeginGetCredentialResponse(intent, response)
                setResult(RESULT_OK, intent)
            } else {
                Log.i(TAG, "Abort passkey listing")
                setResult(RESULT_CANCELED, intent)
            }
        }
        lifecycleScope.launch {
            snoutApp.startIdleTimeout()
            Log.d(TAG, "Finishing activity")
            finish()
        }
    }
}

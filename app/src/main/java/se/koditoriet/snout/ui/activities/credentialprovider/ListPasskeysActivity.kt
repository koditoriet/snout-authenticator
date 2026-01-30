package se.koditoriet.snout.ui.activities.credentialprovider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.SnoutApp
import se.koditoriet.snout.credentialprovider.createBeginGetCredentialResponse
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.components.PasskeyIcon
import se.koditoriet.snout.ui.components.ThemedEmptySpace
import se.koditoriet.snout.ui.theme.BACKGROUND_ICON_SIZE
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.viewmodel.SnoutViewModel

private const val TAG = "UnlockVaultActivity"

class ListPasskeysActivity : FragmentActivity() {
    private val viewModel: SnoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "UnlockVaultActivity created, proceeding to unlock vault")
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)!!

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                try {
                    viewModel.unlockVault(BiometricPromptAuthenticator.Factory(this@ListPasskeysActivity))
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
                ThemedEmptySpace {
                    PasskeyIcon(Modifier.size(BACKGROUND_ICON_SIZE))
                }
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
        Log.d(TAG, "Finishing activity")
        finish()
    }
}

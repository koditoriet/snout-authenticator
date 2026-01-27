package se.koditoriet.snout.credentialprovider.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.SnoutApp
import se.koditoriet.snout.credentialprovider.createBeginGetCredentialResponse
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.screens.EmptyScreen
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.viewmodel.SnoutViewModel
import kotlin.getValue


class UnlockVaultActivity : FragmentActivity() {
    private val TAG = "UnlockVaultActivity"
    private val viewModel: SnoutViewModel by viewModels()

    private val supervisorJob by lazy {
        SupervisorJob()
    }

    private val scope by lazy {
        CoroutineScope(Dispatchers.IO + supervisorJob)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "UnlockVaultActivity created, proceeding to unlock vault")
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)!!

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                try {
                    viewModel.unlockVault(BiometricPromptAuthenticator.Factory(this@UnlockVaultActivity))
                } catch (_: AuthenticationFailedException) {
                    Log.i(TAG, "Abort passkey listing")
                }

                Log.i(TAG, "Vault successfully unlocked, creating credential response")
                (application as SnoutApp).vault.withLock {
                    val response = createBeginGetCredentialResponse(
                        vault = this,
                        context = applicationContext,
                        request = request,
                    )

                    Log.i(TAG, "Sending BeginGetCredentialResponse to credential manager")
                    Intent().apply {
                        PendingIntentHandler.setBeginGetCredentialResponse(this, response)
                        setResult(RESULT_OK, this)
                    }
                    finish()
                }
            }
            SnoutTheme {
                EmptyScreen()
            }
        }
    }
}

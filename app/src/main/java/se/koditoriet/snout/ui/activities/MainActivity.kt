package se.koditoriet.snout.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.Config
import se.koditoriet.snout.ui.ignoreAuthFailure
import se.koditoriet.snout.ui.onIOThread
import se.koditoriet.snout.ui.screens.LockedScreen
import se.koditoriet.snout.ui.screens.main.MainScreen
import se.koditoriet.snout.ui.screens.setup.SetupScreen
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.vault.Vault
import se.koditoriet.snout.viewmodel.SnoutViewModel

private const val TAG = "MainActivity"

class MainActivity : FragmentActivity() {
    val viewModel: SnoutViewModel by viewModels()
    private var isBackgrounded: Boolean = true

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            isBackgrounded = true
        }

        override fun onStart(owner: LifecycleOwner) {
            if (isBackgrounded) {
                lifecycleScope.launch {
                    ignoreAuthFailure {
                        viewModel.unlockVault(BiometricPromptAuthenticator.Factory(this@MainActivity))
                    }
                }
            }
            isBackgrounded = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Main activity created")
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Registering unlock lifecycle observer")
        lifecycle.addObserver(foregroundObserver)

        enableEdgeToEdge()
        setContent {
            MainActivityContent()
        }
    }
}

@Composable
fun MainActivity.MainActivityContent() {
    val vaultState by viewModel.vaultState.collectAsState(Vault.State.Uninitialized)
    val config by viewModel.config.collectAsState(Config.Companion.default)

    LaunchedEffect(config.screenSecurityEnabled) {
        if (config.screenSecurityEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }

    SnoutTheme {
        when (vaultState) {
            Vault.State.Unlocked -> { MainScreen() }
            Vault.State.Uninitialized -> { SetupScreen() }
            Vault.State.Locked -> {
                LockedScreen(
                    onUnlock = onIOThread {
                        val authFactory = BiometricPromptAuthenticator.Factory(this)
                        ignoreAuthFailure { viewModel.unlockVault(authFactory) }
                    },
                )
            }
        }
    }
}

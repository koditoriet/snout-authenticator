package se.koditoriet.snout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.crypto.CipherAuthenticator
import se.koditoriet.snout.crypto.wordMap
import se.koditoriet.snout.ui.theme.SnoutTheme
import se.koditoriet.snout.ui.screens.secrets.AddSecretByTextScreen
import se.koditoriet.snout.ui.screens.setup.BackupSeedScreen
import se.koditoriet.snout.ui.screens.setup.BackupSetupScreen
import se.koditoriet.snout.ui.screens.secrets.ListSecretsScreen
import se.koditoriet.snout.ui.ViewState
import se.koditoriet.snout.ui.onIOThread
import se.koditoriet.snout.ui.screens.secrets.AddSecretByQrScreen
import se.koditoriet.snout.ui.screens.secrets.EditSecretMetadataScreen
import se.koditoriet.snout.ui.screens.LockedScreen
import se.koditoriet.snout.ui.screens.setup.RestoreBackupScreen
import se.koditoriet.snout.ui.screens.SettingsScreen
import se.koditoriet.snout.vault.NewTotpSecret
import se.koditoriet.snout.vault.Vault
import se.koditoriet.snout.viewmodel.SnoutViewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : FragmentActivity() {
    private val viewModel: SnoutViewModel by viewModels()
    private var idleTimeoutJob: Job? = null
    private var isBackgrounded: Boolean = true

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lifecycleScope.launch {
                viewModel.lockVault()
            }
        }
    }

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            isBackgrounded = true
        }

        override fun onStart(owner: LifecycleOwner) {
            if (viewModel.vaultState.value == Vault.State.Locked && isBackgrounded) {
                lifecycleScope.launch {
                    try {
                        viewModel.unlockVault()
                    } catch (_: AuthenticationFailedException) {
                        // vault stays locked
                    }
                }
            }
            isBackgrounded = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Always start off with screen security enabled
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        (application as SnoutApp).cryptographer.setCipherAuthenticator(
            BiometricPromptCipherAuthenticator(this)
        )

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        lifecycle.addObserver(foregroundObserver)

        enableEdgeToEdge()
        setContent {
            MainScreen()
        }
    }

    override fun onStart() {
        super.onStart()
        idleTimeoutJob?.cancel()
    }

    override fun onStop() {
        super.onStop()
        idleTimeoutJob = lifecycleScope.launch {
            viewModel.lockVaultAfterIdleTimeout()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOffReceiver)
    }
}

@Composable
fun MainActivity.MainScreen() {
    val viewModel = viewModel<SnoutViewModel>()
    val totpSecrets by viewModel.secrets.collectAsState()
    val vaultState by viewModel.vaultState.collectAsState()
    val config by viewModel.config.collectAsState(Config.default)
    var viewState by remember { mutableStateOf<ViewState>(ViewState.LockedScreen) }

    LaunchedEffect(config.screenSecurityEnabled) {
        if (config.screenSecurityEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }

    SnoutTheme {
        BackHandler {
            val goToState = viewState.previousViewState
            lifecycleScope.launch {
                if (goToState is ViewState.LockedScreen) {
                    viewModel.lockVault()
                }
                goToState?.apply {
                    viewState = this
                }
            }
        }

        if (vaultState == Vault.State.Uninitialized && !viewState.isSetupViewState) {
            // If the vault is not initialized, the setup flow is the only valid thing to display.
            viewState = ViewState.InitialSetup
        }

        if (vaultState == Vault.State.Locked) {
            // If the vault is locked, we need to send the user to the lock screen.
            viewState = ViewState.LockedScreen
        }

        if (vaultState == Vault.State.Unlocked && viewState is ViewState.LockedScreen) {
            // If we're on the lock screen and the vault becomes unlocked, go on to list secrets.
            viewState = ViewState.ListSecrets
        }

        when (viewState) {
            ViewState.InitialSetup -> {
                BackupSetupScreen(
                    onEnableBackups = {
                        viewState = ViewState.ShowBackupSeed
                    },
                    onSkipBackups = onIOThread {
                        viewModel.createVault(null)
                        viewState = ViewState.ListSecrets
                    },
                    onRestoreBackup = {
                        viewState = ViewState.RestoreBackup
                    }
                )
            }
            ViewState.ShowBackupSeed -> {
                val seed = remember { BackupSeed.generate() }
                BackupSeedScreen(
                    backupSeed = seed,
                    onContinue = onIOThread {
                        viewModel.createVault(seed)
                        seed.wipe()
                        viewState = ViewState.ListSecrets
                    }
                )
            }
            ViewState.RestoreBackup -> {
                RestoreBackupScreen(
                    seedWords = wordMap.keys,
                    onRestore = onIOThread { backupSeed, uri ->
                        viewModel.restoreVaultFromBackup(backupSeed, uri)
                        viewState = ViewState.ListSecrets
                    }
                )
            }
            ViewState.LockedScreen -> {
                LockedScreen(
                    onUnlock = onIOThread {
                        try {
                            viewModel.unlockVault()
                            viewState = ViewState.ListSecrets
                        } catch (_: AuthenticationFailedException) {
                            // vault stays locked
                        }
                    }
                )
            }
            ViewState.ListSecrets -> {
                ListSecretsScreen(
                    secrets = totpSecrets,
                    sortMode = config.sortMode,
                    hideSecretsFromAccessibility = config.hideSecretsFromAccessibility,
                    getTotpCodes = { secret ->
                        viewModel.getTotpCodes(secret, 2)
                    },
                    onLockVault = onIOThread {
                        viewModel.lockVault()
                        viewState = ViewState.LockedScreen
                    },
                    onSettings = { viewState = ViewState.Settings },
                    onAddSecret = { viewState = ViewState.AddSecret(it) },
                    onAddSecretByQR = { viewState = ViewState.ScanSecretQrCode },
                    onSortModeChange = onIOThread { mode -> viewModel.setSortMode(mode) },
                    onEditSecretMetadata = { viewState = ViewState.EditSecretMetadata(it) },
                    onDeleteSecret = onIOThread { secret -> viewModel.deleteTotpSecret(secret) },
                )
            }
            is ViewState.AddSecret -> {
                AddSecretByTextScreen(
                    hideSecretsFromAccessibility = config.hideSecretsFromAccessibility,
                    prefilledSecret = (viewState as ViewState.AddSecret).prefilledSecret,
                    onSave = onIOThread { newSecret ->
                        viewModel.addTotpSecret(newSecret)
                        viewState = ViewState.ListSecrets
                    },
                    onCancel = { viewState = ViewState.ListSecrets },
                )
            }
            is ViewState.EditSecretMetadata -> {
                val secret = (viewState as ViewState.EditSecretMetadata).secret
                val metadata = NewTotpSecret.Metadata(issuer = secret.issuer, account = secret.account)
                EditSecretMetadataScreen(
                    metadata = metadata,
                    onSave = onIOThread { newMetadata ->
                        val updatedSecret = secret.copy(issuer = newMetadata.issuer, account = newMetadata.account)
                        viewModel.updateTotpSecret(updatedSecret)
                        viewState = ViewState.ListSecrets
                    },
                    onCancel = { viewState = ViewState.ListSecrets },
                )
            }
            ViewState.ScanSecretQrCode -> {
                AddSecretByQrScreen(
                    onSave = onIOThread { newSecret ->
                        viewModel.addTotpSecret(newSecret)
                        viewState = ViewState.ListSecrets
                    },
                    onCancel = { viewState = ViewState.ListSecrets },
                )
            }
            ViewState.Settings -> {
                SettingsScreen(
                    enableBackups = config.backupsEnabled,
                    protectAccountList = config.protectAccountList,
                    lockOnClose = config.lockOnClose,
                    lockOnCloseGracePeriod = config.lockOnCloseGracePeriod,
                    screenSecurityEnabled = config.screenSecurityEnabled,
                    hideSecretsFromAccessibility = config.hideSecretsFromAccessibility,
                    onDisableBackups = onIOThread(viewModel::disableBackups),
                    onLockOnCloseChange = onIOThread(viewModel::setLockOnClose),
                    onLockOnCloseGracePeriodChange = onIOThread(viewModel::setLockOnCloseGracePeriod),
                    onProtectAccountListChange = onIOThread(viewModel::rekeyVault),
                    onScreenSecurityEnabledChange = onIOThread(viewModel::setScreenSecurity),
                    onHideSecretsFromAccessibilityChange = onIOThread(viewModel::setHideSecretsFromAccessibility),
                    onWipeVault = onIOThread(viewModel::wipeVault),
                    onExport = onIOThread(viewModel::exportVault),
                    getSecurityReport = {
                        withContext(Dispatchers.IO) {
                            viewModel.getSecurityReport()
                        }
                    },
                )
            }
        }
    }
}

class BiometricPromptCipherAuthenticator(
    private val activity: FragmentActivity,
) : CipherAuthenticator {
    override suspend fun <T> authenticate(authenticatedAction: () -> T): T {
        val result = withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        continuation.resume(authenticatedAction())
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        continuation.resume(null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        continuation.resume(null)
                    }
                }
                val prompt = BiometricPrompt(activity, callback)
                val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                    setTitle(reason ?: activity.appStrings.viewModel.authDefaultReason)
                    setSubtitle(subtitle ?: activity.appStrings.viewModel.authDefaultSubtitle)
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    setNegativeButtonText(activity.appStrings.generic.cancel)
                }.build()
                prompt.authenticate(promptInfo)
            }
        }
        return result ?: throw AuthenticationFailedException("user canceled authentication")
    }
}

package se.koditoriet.snout.ui.screens.main

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.koditoriet.snout.BiometricPromptAuthenticator
import se.koditoriet.snout.Config
import se.koditoriet.snout.ui.ignoreAuthFailure
import se.koditoriet.snout.ui.onIOThread
import se.koditoriet.snout.ui.screens.main.passkeys.ManagePasskeysScreen
import se.koditoriet.snout.ui.screens.main.secrets.AddSecretByQrScreen
import se.koditoriet.snout.ui.screens.main.secrets.AddSecretByTextScreen
import se.koditoriet.snout.ui.screens.main.secrets.ListSecretsScreen
import se.koditoriet.snout.ui.screens.main.settings.SettingsScreen
import se.koditoriet.snout.viewmodel.SnoutViewModel

private const val TAG = "MainScreen"

@Composable
fun FragmentActivity.MainScreen() {
    val authFactory = remember { BiometricPromptAuthenticator.Factory(this) }
    val viewModel = viewModel<SnoutViewModel>()
    val config by viewModel.config.collectAsState(Config.default)
    val totpSecrets by viewModel.secrets.collectAsState(emptyList())
    val passkeys by viewModel.passkeys.collectAsState(emptyList())
    var viewState by remember { mutableStateOf<ViewState>(ViewState.ListSecrets) }

    BackHandler {
        Log.d(TAG, "Back pressed on main screen")
        println("WHAT?! ${viewState.previousViewState}")
        viewState.previousViewState?.apply {
            Log.d(TAG, "Going back to view '$this'")
            viewState = this
        } ?: lifecycleScope.launch {
            Log.d(TAG, "No view to go back to; locking vault and backgrounding")
            viewModel.lockVault()
            moveTaskToBack(true)
        }
    }

    when (viewState) {
        ViewState.ListSecrets -> {
            ListSecretsScreen(
                secrets = totpSecrets,
                sortMode = config.totpSecretSortMode,
                enableDeveloperFeatures = config.enableDeveloperFeatures,
                hideSecretsFromAccessibility = config.hideSecretsFromAccessibility,
                getTotpCodes = { secret -> viewModel.getTotpCodes(authFactory, secret, 2) },
                onLockVault = onIOThread { viewModel.lockVault() },
                onSettings = { viewState = ViewState.Settings },
                onAddSecret = { viewState = ViewState.AddSecret(it) },
                onAddSecretByQR = { viewState = ViewState.ScanSecretQrCode },
                onSortModeChange = onIOThread { mode -> viewModel.setTotpSecretSortMode(mode) },
                onUpdateSecret = onIOThread { secret -> viewModel.updateTotpSecret(secret) },
                onDeleteSecret = onIOThread { secret -> viewModel.deleteTotpSecret(secret.id) },
                onImportFile = onIOThread { uri -> viewModel.importFromFile(uri) },
                onReindexSecrets = onIOThread { viewModel.reindexTotpSecrets() },
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
                onProtectAccountListChange = onIOThread { it ->
                    ignoreAuthFailure {
                        viewModel.rekeyVault(authFactory, it)
                    }
                },
                onScreenSecurityEnabledChange = onIOThread(viewModel::setScreenSecurity),
                onHideSecretsFromAccessibilityChange = onIOThread(viewModel::setHideSecretsFromAccessibility),
                enableDeveloperFeatures = config.enableDeveloperFeatures,
                onEnableDeveloperFeaturesChange = onIOThread(viewModel::setEnableDeveloperFeatures),
                onWipeVault = onIOThread(viewModel::wipeVault),
                onExport = onIOThread(viewModel::exportVault),
                onManagePasskeys = { viewState = ViewState.ManagePasskeys },
                getSecurityReport = {
                    withContext(Dispatchers.IO) {
                        viewModel.getSecurityReport()
                    }
                },
            )
        }
        ViewState.ManagePasskeys -> {
            ManagePasskeysScreen(
                passkeys = passkeys,
                sortMode = config.passkeySortMode,
                onSortModeChange = onIOThread { it -> viewModel.setPasskeySortMode(it) },
                onUpdatePasskey = onIOThread { it -> viewModel.updatePasskey(it) },
                onDeletePasskey = onIOThread { it -> viewModel.deletePasskey(it.credentialId) },
                onReindexPasskeys = onIOThread { viewModel.reindexTotpSecrets() }
            )
        }
    }
}

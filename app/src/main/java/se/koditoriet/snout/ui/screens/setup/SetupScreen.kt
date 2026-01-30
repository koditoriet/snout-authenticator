package se.koditoriet.snout.ui.screens.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.print.PrintHelper
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.crypto.wordMap
import se.koditoriet.snout.ui.onIOThread
import se.koditoriet.snout.ui.components.LoadingOverlay
import se.koditoriet.snout.viewmodel.SnoutViewModel

@Composable
fun FragmentActivity.SetupScreen() {
    val viewModel = viewModel<SnoutViewModel>()
    var viewState by remember { mutableStateOf<ViewState>(ViewState.InitialSetup) }
    var showLoadingScreen by remember { mutableStateOf(false) }

    BackHandler {
        viewState.previousViewState?.apply {
            viewState = this
        }
    }

    when (viewState) {
        ViewState.InitialSetup -> {
            InitialSetupScreen(
                onEnableBackups = { viewState = ViewState.ShowBackupSeed },
                onSkipBackups = onIOThread { viewModel.createVault(null) },
                onRestoreBackup = { viewState = ViewState.RestoreBackup }
            )
        }
        ViewState.ShowBackupSeed -> {
            val seed = remember { BackupSeed.generate() }
            BackupSeedScreen(
                backupSeed = seed,
                onPrintQr = PrintHelper(this)::printBitmap,
                onContinue = onIOThread {
                    viewModel.createVault(seed)
                    seed.wipe()
                }
            )
        }
        ViewState.RestoreBackup -> {
            LoadingOverlay(showLoadingScreen)
            RestoreBackupScreen(
                seedWords = wordMap.keys,
                onRestore = onIOThread { backupSeed, uri ->
                    showLoadingScreen = true
                    viewModel.restoreVaultFromBackup(backupSeed, uri)
                    showLoadingScreen = false
                }
            )
        }
    }
}

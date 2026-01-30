package se.koditoriet.snout.ui.screens.setup

import se.koditoriet.snout.vault.NewTotpSecret

sealed class ViewState(val previousViewState: ViewState?) {
    object InitialSetup : ViewState(null)
    object ShowBackupSeed : ViewState(InitialSetup)
    object RestoreBackup : ViewState(InitialSetup)
}

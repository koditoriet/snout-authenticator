package se.koditoriet.snout.ui

import se.koditoriet.snout.vault.NewTotpSecret

sealed class ViewState(
    val previousViewState: ViewState?,
    val isSetupViewState: Boolean = false,
) {
    // Setup screens
    object InitialSetup : ViewState(null, true)
    object ShowBackupSeed : ViewState(InitialSetup, true)
    object RestoreBackup : ViewState(InitialSetup, true)

    // Daily use screens
    object LockedScreen : ViewState(null)
    object ListSecrets : ViewState(LockedScreen)
    class AddSecret(val prefilledSecret: NewTotpSecret?) : ViewState(ListSecrets)
    object ScanSecretQrCode : ViewState(ListSecrets)
    object Settings : ViewState(ListSecrets)
    object ManagePasskeys: ViewState(Settings)
}

package se.koditoriet.snout.ui.screens.main

import se.koditoriet.snout.vault.NewTotpSecret

sealed class ViewState(val previousViewState: ViewState?) {
    object ListSecrets : ViewState(null)
    class AddSecret(val prefilledSecret: NewTotpSecret?) : ViewState(ListSecrets)
    object ScanSecretQrCode : ViewState(ListSecrets)
    object Settings : ViewState(ListSecrets)
    object ManagePasskeys: ViewState(Settings)
}

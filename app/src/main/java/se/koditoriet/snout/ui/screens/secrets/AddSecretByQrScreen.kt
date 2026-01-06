package se.koditoriet.snout.ui.screens.secrets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.QrScannerScreen
import se.koditoriet.snout.ui.components.TotpSecretForm
import se.koditoriet.snout.ui.components.TotpSecretFormResult
import se.koditoriet.snout.vault.NewTotpSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSecretByQrScreen(
    onSave: (NewTotpSecret) -> Unit,
    onCancel: () -> Unit,
) {
    var viewState by remember { mutableStateOf<AddQrSecretViewState>(AddQrSecretViewState.ScanQrCode) }

    when (val currentViewState = viewState) {
        AddQrSecretViewState.ScanQrCode -> {
            QrScannerScreen {
                val secret = NewTotpSecret.fromUri(it)
                viewState = AddQrSecretViewState.EditMetadata(secret)
            }
        }
        is AddQrSecretViewState.EditMetadata -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(appStrings.addSecretScreens.addSecret) },
                        actions = {},
                        navigationIcon = {
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Close, appStrings.generic.cancel)
                            }
                        }
                    )
                },
            ) { padding ->
                TotpSecretForm<TotpSecretFormResult.TotpMetadata>(
                    padding = padding,
                    metadata = currentViewState.secret.metadata,
                    hideSecretsFromAccessibility = false,
                ) { result ->
                    val secret = NewTotpSecret(
                        metadata = result.metadata,
                        secretData = currentViewState.secret.secretData
                    )
                    onSave(secret)
                }
            }
        }
    }
}

private sealed interface AddQrSecretViewState {
    object ScanQrCode : AddQrSecretViewState
    class EditMetadata(val secret: NewTotpSecret) : AddQrSecretViewState
}

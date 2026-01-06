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
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.TotpSecretForm
import se.koditoriet.snout.ui.components.TotpSecretFormResult
import se.koditoriet.snout.vault.NewTotpSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSecretMetadataScreen(
    metadata: NewTotpSecret.Metadata,
    onSave: (NewTotpSecret.Metadata) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appStrings.editScreen.edit) },
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
            metadata = metadata,
            hideSecretsFromAccessibility = false,
        ) {
            onSave(it.metadata)
        }
    }
}

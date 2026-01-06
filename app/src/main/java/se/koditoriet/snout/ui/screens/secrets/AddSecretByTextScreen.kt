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
fun AddSecretByTextScreen(
    hideSecretsFromAccessibility: Boolean,
    prefilledSecret: NewTotpSecret? = null,
    onSave: (NewTotpSecret) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appStrings.addSecretScreens.addSecret) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, appStrings.generic.cancel)
                    }
                }
            )
        },
    ) { padding ->
        if (prefilledSecret == null) {
            TotpSecretForm<TotpSecretFormResult.TotpSecret>(
                padding = padding,
                hideSecretsFromAccessibility = hideSecretsFromAccessibility,
            ) {
                onSave(it.secret)
            }
        } else {
            TotpSecretForm<TotpSecretFormResult.TotpMetadata>(
                padding = padding,
                metadata = prefilledSecret.metadata,
                hideSecretsFromAccessibility = hideSecretsFromAccessibility,
            ) {
                onSave(prefilledSecret.copy(metadata = it.metadata))
            }
        }
    }
}

package se.koditoriet.snout.ui.sheets

import BottomSheetContextualHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.TotpSecretForm
import se.koditoriet.snout.ui.components.TotpSecretFormResult
import se.koditoriet.snout.vault.NewTotpSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSecretMetadataSheet(
    metadata: NewTotpSecret.Metadata,
    onSave: (NewTotpSecret.Metadata) -> Unit,
) {
    BottomSheetContextualHeader(
        heading = metadata.issuer,
        subheading = metadata.account ?: appStrings.secretsScreen.actionsSheetNoAccount,
        icon = Icons.Default.AccountBox,
    )

    TotpSecretForm<TotpSecretFormResult.TotpMetadata>(
        metadata = metadata,
        hideSecretsFromAccessibility = false,
    ) {
        onSave(it.metadata)
    }
}

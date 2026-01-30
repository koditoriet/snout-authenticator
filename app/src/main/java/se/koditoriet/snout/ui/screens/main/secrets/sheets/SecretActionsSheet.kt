package se.koditoriet.snout.ui.screens.main.secrets.sheets

import BottomSheetAction
import BottomSheetContextualHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.vault.TotpSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretActionsSheet(
    totpSecret: TotpSecret,
    onEditMetadata: (TotpSecret) -> Unit,
    onDeleteSecret: (TotpSecret) -> Unit,
) {
    val screenStrings = appStrings.secretsScreen
    BottomSheetContextualHeader(
        heading = totpSecret.issuer,
        subheading = totpSecret.account ?: screenStrings.actionsSheetNoAccount,
        icon = Icons.Default.AccountBox,
    )
    BottomSheetAction(
        icon = Icons.Default.Edit,
        text = screenStrings.actionsSheetEdit,
        onClick = { onEditMetadata(totpSecret) },
    )
    BottomSheetAction(
        icon = Icons.Default.DeleteForever,
        text = screenStrings.actionsSheetDelete,
        onClick = { onDeleteSecret(totpSecret) },
    )
}

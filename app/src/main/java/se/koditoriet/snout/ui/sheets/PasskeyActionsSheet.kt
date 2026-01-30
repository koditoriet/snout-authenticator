package se.koditoriet.snout.ui.sheets

import BottomSheetAction
import BottomSheetContextualHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.PasskeyIcon
import se.koditoriet.snout.vault.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeyActionsSheet(
    passkey: Passkey,
    onEditDisplayName: (Passkey) -> Unit,
    onDeletePasskey: (Passkey) -> Unit,
) {
    val screenStrings = appStrings.managePasskeysScreen
    BottomSheetContextualHeader(
        heading = passkey.displayName,
        subheading = passkey.description,
        icon = { PasskeyIcon() },
    )
    BottomSheetAction(
        icon = Icons.Default.Edit,
        text = screenStrings.actionsSheetEdit,
        onClick = { onEditDisplayName(passkey) },
    )
    BottomSheetAction(
        icon = Icons.Default.DeleteForever,
        text = screenStrings.actionsSheetDelete,
        onClick = { onDeletePasskey(passkey) },
    )
}

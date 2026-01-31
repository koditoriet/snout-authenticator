package se.koditoriet.snout.ui.screens.main.passkeys

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import se.koditoriet.snout.AppStrings
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.IrrevocableActionConfirmationDialog
import se.koditoriet.snout.ui.components.listview.ListViewTopBar
import se.koditoriet.snout.ui.components.listview.ReorderableList
import se.koditoriet.snout.ui.components.listview.ReorderableListItem
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.screens.main.passkeys.sheets.EditPasskeyNameSheet
import se.koditoriet.snout.ui.screens.main.passkeys.sheets.PasskeyActionsSheet
import se.koditoriet.snout.ui.theme.LIST_ITEM_FONT_SIZE
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.vault.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePasskeysScreen(
    passkeys: List<Passkey>,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    onUpdatePasskey: (Passkey) -> Unit,
    onDeletePasskey: (Passkey) -> Unit,
    onReindexPasskeys: () -> Unit,
) {
    val screenStrings = appStrings.managePasskeysScreen
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var sheetViewState by remember { mutableStateOf<SheetViewState?>(null) }
    var confirmDeletePasskey by remember { mutableStateOf<Passkey?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val passkeyListItems = passkeys.map { passkey ->
        PasskeyListItem(
            passkey = passkey,
            onUpdatePasskey = onUpdatePasskey,
            onLongClickPasskey = { sheetViewState = SheetViewState.Actions(it) },
        )
    }

    Scaffold(
        topBar = {
            ListViewTopBar(
                title = screenStrings.heading,
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appStrings.generic.back,
                        )
                    }
                },
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                filterEnabled = filter != null,
                onFilterToggle = { filter = if (filter == null) "" else null },
            )
        },
    ) { padding ->
        val comparator = compareBy<PasskeyListItem> { it.passkey.displayName.lowercase() }
            .thenBy { it.passkey.rpId.lowercase() }
            .thenBy { it.passkey.userName.lowercase() }

        ReorderableList(
            padding = padding,
            filter = filter,
            items = passkeyListItems,
            selectedItem = (sheetViewState as? SheetViewState.Actions)?.selectedItem,
            sortMode = sortMode,
            alphabeticItemComparator = comparator,
            filterPlaceholderText = screenStrings.filterPlaceholder,
            onFilterChange = { filter = it },
            onReindexItems = onReindexPasskeys,
        )

        confirmDeletePasskey?.let { passkey ->
            IrrevocableActionConfirmationDialog(
                text = screenStrings.actionsSheetDeleteWarning,
                buttonText = screenStrings.actionsSheetDelete,
                onCancel = { confirmDeletePasskey = null },
                onConfirm = {
                    confirmDeletePasskey = null
                    sheetViewState = null
                    onDeletePasskey(passkey)
                }
            )
        }

        sheetViewState?.let { viewState ->
            BottomSheet(
                hideSheet = { sheetViewState = null },
                sheetState = sheetState,
                sheetViewState = viewState,
                padding = padding,
            ) { viewState ->
                when (viewState) {
                    is SheetViewState.Actions -> {
                        PasskeyActionsSheet(
                            passkey = viewState.selectedItem.passkey,
                            onEditDisplayName = {
                                sheetViewState = SheetViewState.EditMetadata(viewState.selectedItem)
                            },
                            onDeletePasskey = {
                                confirmDeletePasskey = viewState.selectedItem.passkey
                            },
                        )
                    }
                    is SheetViewState.EditMetadata -> {
                        BackHandler {
                            sheetViewState = SheetViewState.Actions(viewState.selectedItem)
                        }
                        EditPasskeyNameSheet(
                            existingPasskey = viewState.selectedItem.passkey,
                            onSave = {
                                onUpdatePasskey(viewState.selectedItem.passkey.copy(displayName = it))
                                sheetViewState = null
                            }
                        )
                    }
                }
            }
        }
    }
}

private class PasskeyListItem(
    val passkey: Passkey,
    private val onUpdatePasskey: (Passkey) -> Unit,
    private val onLongClickPasskey: (PasskeyListItem) -> Unit,
) : ReorderableListItem {
    override val key: String
        get() = passkey.credentialId.string

    override val sortOrder: Long
        get() = passkey.sortOrder

    override fun onClickLabel(appStrings: AppStrings): String = ""

    override fun onLongClickLabel(appStrings: AppStrings): String =
        appStrings.generic.selectItem

    override fun filterPredicate(filter: String): Boolean = (
        filter in passkey.displayName.lowercase() ||
        filter in passkey.userName.lowercase() ||
        filter in passkey.rpId.lowercase()
    )

    override fun onUpdateSortOrder(sortOrder: Long) {
        onUpdatePasskey(passkey.copy(sortOrder = sortOrder))
    }

    override fun onClick() { }

    override fun onLongClick() {
        onLongClickPasskey(this)
    }

    @Composable
    override fun RowScope.Render() {
        Column(
            modifier = Modifier
                .weight(1.0f)
                .padding(start = PADDING_M)
        ) {
            Text(
                text = passkey.displayName,
                fontSize = LIST_ITEM_FONT_SIZE,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = passkey.rpId,
                fontSize = LIST_ITEM_FONT_SIZE,
                color = MaterialTheme.colorScheme.primaryHint,
            )
            Text(
                text = passkey.userName,
                fontSize = LIST_ITEM_FONT_SIZE,
                color = MaterialTheme.colorScheme.primaryHint,
            )
        }
    }
}

private sealed interface SheetViewState {
    class Actions(val selectedItem: PasskeyListItem) : SheetViewState
    class EditMetadata(val selectedItem: PasskeyListItem) : SheetViewState
}

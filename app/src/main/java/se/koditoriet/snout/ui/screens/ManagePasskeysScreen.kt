package se.koditoriet.snout.ui.screens

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.Flow
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.sheets.EditPasskeyNameSheet
import se.koditoriet.snout.ui.theme.LIST_ITEM_FONT_SIZE
import se.koditoriet.snout.ui.theme.PADDING_L
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XS
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import se.koditoriet.snout.vault.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePasskeysScreen(
    passkeys: Flow<List<Passkey>>,
    onUpdatePasskey: (Passkey) -> Unit,
    onDeletePasskey: (Passkey) -> Unit,
) {
    val screenStrings = appStrings.managePasskeysScreen
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val passkeys by passkeys.collectAsState(emptyList())
    var selectedPasskey by remember { mutableStateOf<Passkey?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appStrings.generic.back,
                        )
                    }
                },
                title = { Text(screenStrings.heading) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PasskeyList(
                passkeys = passkeys,
                onSelectPasskey = { selectedPasskey = it },
                onDeletePasskey = onDeletePasskey,
            )

            selectedPasskey?.let { passkey ->
                BottomSheet(
                    hideSheet = { selectedPasskey = null },
                    sheetState = sheetState,
                    sheetViewState = passkey,
                    padding = padding,
                ) { passkey ->
                    EditPasskeyNameSheet(
                        existingPasskey = passkey,
                        onSave = {
                            onUpdatePasskey(passkey.copy(displayName = it))
                            selectedPasskey = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PasskeyList(
    passkeys: List<Passkey>,
    onSelectPasskey: (Passkey) -> Unit,
    onDeletePasskey: (Passkey) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PADDING_S)
    ) {
        items(passkeys) { item ->
            ListRow(
                passkey = item,
                onLongPress = onSelectPasskey,
                onDeletePasskey = onDeletePasskey,
            )
        }
    }
}

@Composable
private fun ListRow(
    passkey: Passkey,
    onLongPress: (Passkey) -> Unit,
    onDeletePasskey: (Passkey) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PADDING_XS)
            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = { },
                onLongClick = { onLongPress(passkey) },
                onLongClickLabel = appStrings.generic.selectItem,
            )
            .padding(PADDING_M),
        verticalAlignment = Alignment.CenterVertically,

        ) {
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
        Icon(
            imageVector = Icons.Default.DeleteForever,
            contentDescription = appStrings.managePasskeysScreen.permanentlyDeletePasskey,
            modifier = Modifier
                .clickable { onDeletePasskey(passkey) } // TODO: <- confirmation dialog
                .padding(PADDING_L)
        )
    }
}

package se.koditoriet.snout.ui.screens

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.Flow
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.theme.LIST_ITEM_FONT_SIZE
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XS
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import se.koditoriet.snout.vault.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePasskeysScreen(
    passkeys: Flow<List<Passkey>>,
    onDeletePasskey: (Passkey) -> Unit,
) {
    val screenStrings = appStrings.managePasskeysScreen
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val passkeys by passkeys.collectAsState(emptyList())

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
                onDeletePasskey = onDeletePasskey,
            )
        }
    }
}

@Composable
private fun PasskeyList(
    passkeys: List<Passkey>,
    onDeletePasskey: (Passkey) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PADDING_S)
    ) {
        items(passkeys) { item ->
            ListRow(
                passkey = item,
                onDeletePasskey = onDeletePasskey,
            )
        }
    }
}

@Composable
private fun ListRow(
    passkey: Passkey,
    onDeletePasskey: (Passkey) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PADDING_XS)
            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(PADDING_M),
        verticalAlignment = Alignment.CenterVertically,

        ) {
        Column(
            modifier = Modifier
                .padding(start = PADDING_M)
                .weight(1.0f)
        ) {
            Text(
                text = passkey.displayName,
                fontSize = LIST_ITEM_FONT_SIZE,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${passkey.userName} \u2022 ${passkey.rpId}",
                fontSize = LIST_ITEM_FONT_SIZE,
                color = MaterialTheme.colorScheme.primaryHint,
            )
        }
        Icon(
            imageVector = Icons.Default.DeleteForever,
            contentDescription = appStrings.managePasskeysScreen.permanentlyDeletePasskey,
            modifier = Modifier.clickable { onDeletePasskey(passkey) } // TODO: <- confirmation dialog
        )
    }
}

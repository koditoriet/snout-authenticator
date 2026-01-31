package se.koditoriet.snout.ui.screens.main.secrets

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.koditoriet.snout.AppStrings
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.IrrevocableActionConfirmationDialog
import se.koditoriet.snout.ui.components.listview.ListViewTopBar
import se.koditoriet.snout.ui.components.listview.ReorderableList
import se.koditoriet.snout.ui.components.listview.ReorderableListItem
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.ignoreAuthFailure
import se.koditoriet.snout.ui.primaryDisabled
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.screens.main.secrets.sheets.AddSecretsSheet
import se.koditoriet.snout.ui.screens.main.secrets.sheets.EditSecretMetadataSheet
import se.koditoriet.snout.ui.screens.main.secrets.sheets.SecretActionsSheet
import se.koditoriet.snout.ui.theme.LIST_ITEM_FONT_SIZE
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.SECRET_FONT_SIZE
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.vault.NewTotpSecret
import se.koditoriet.snout.vault.TotpSecret
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ListSecretsScreen(
    secrets: List<TotpSecret>,
    sortMode: SortMode,
    enableDeveloperFeatures: Boolean,
    hideSecretsFromAccessibility: Boolean,
    getTotpCodes: suspend (TotpSecret) -> List<String>,
    onLockVault: () -> Unit,
    onSettings: () -> Unit,
    onAddSecretByQR: () -> Unit,
    onAddSecret: (NewTotpSecret?) -> Unit,
    onImportFile: (Uri) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onUpdateSecret: (TotpSecret) -> Unit,
    onDeleteSecret: (TotpSecret) -> Unit,
    onReindexSecrets: () -> Unit,
    clock: Clock = Clock.System,
) {
    val screenStrings = appStrings.secretsScreen
    var confirmDeleteSecret by remember { mutableStateOf<TotpSecret?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetViewState by remember { mutableStateOf<SheetViewState?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    val secretListItems = secrets.map { secret ->
        TotpSecretListItem(
            totpSecret = secret,
            scope = LocalLifecycleOwner.current.lifecycleScope,
            hideSecretsFromAccessibility = hideSecretsFromAccessibility,
            getTotpCodes = getTotpCodes,
            onUpdateSecret = onUpdateSecret,
            onLongClickSecret = { sheetViewState = SheetViewState.SecretActions(it) },
            clock = clock,
        )
    }

    Scaffold(
        topBar = {
            ListViewTopBar(
                title = appStrings.generic.appName,
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                filterEnabled = filter != null,
                onFilterToggle = { filter = if (filter == null) "" else null },
            ) {
                IconButton(onClick = onLockVault) {
                    Icon(Icons.Filled.LockOpen, screenStrings.lockScreen)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, screenStrings.settings)
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_L)) {
                FloatingActionButton(onClick = { sheetViewState = SheetViewState.AddSecrets }) {
                    Icon(Icons.Filled.Add, screenStrings.addSecret)
                }
            }
        }
    ) { padding ->
        val comparator = compareBy<TotpSecretListItem> { it.totpSecret.issuer.lowercase() }
            .thenBy { it.totpSecret.account?.lowercase() }

        ReorderableList(
            padding = padding,
            filter = filter,
            items = secretListItems,
            selectedItem = (sheetViewState as? SheetViewState.SecretActions)?.selectedItem,
            sortMode = sortMode,
            alphabeticItemComparator = comparator,
            filterPlaceholderText = screenStrings.filterPlaceholder,
            onFilterChange = { filter = it },
            onReindexItems = onReindexSecrets,
        )

        confirmDeleteSecret?.let { secret ->
            IrrevocableActionConfirmationDialog(
                text = screenStrings.actionsSheetDeleteWarning,
                buttonText = screenStrings.actionsSheetDelete,
                onCancel = { confirmDeleteSecret = null },
                onConfirm = {
                    confirmDeleteSecret = null
                    sheetViewState = null
                    onDeleteSecret(secret)
                }
            )
        }

        sheetViewState?.let { viewState ->
            BottomSheet(
                hideSheet = { sheetViewState = null },
                sheetState = sheetState,
                sheetViewState = viewState,
                padding = padding,
            ) { state ->
                when (state) {
                    SheetViewState.AddSecrets -> {
                        AddSecretsSheet(
                            enableFileImport = enableDeveloperFeatures,
                            onAddSecretByQR = {
                                onAddSecretByQR()
                                sheetViewState = null
                            },
                            onAddSecret = {
                                onAddSecret(it)
                                sheetViewState = null
                            },
                            onImportFile = {
                                onImportFile(it)
                                sheetViewState = null
                            }
                        )
                    }

                    is SheetViewState.SecretActions -> {
                        SecretActionsSheet(
                            totpSecret = state.selectedItem.totpSecret,
                            onEditMetadata = {
                                sheetViewState = SheetViewState.EditSecretMetadata(state.selectedItem.update(it))
                            },
                            onDeleteSecret = {
                                confirmDeleteSecret = state.selectedItem.totpSecret
                            },
                        )
                    }
                    is SheetViewState.EditSecretMetadata -> {
                        BackHandler {
                            sheetViewState = SheetViewState.SecretActions(state.selectedItem)
                        }
                        EditSecretMetadataSheet(
                            metadata = NewTotpSecret.Metadata(
                                issuer = state.selectedItem.totpSecret.issuer,
                                account = state.selectedItem.totpSecret.account
                            ),
                            onSave = { newMetadata ->
                                val updatedSecret = state.selectedItem.totpSecret.copy(
                                    issuer = newMetadata.issuer,
                                    account = newMetadata.account
                                )
                                onUpdateSecret(updatedSecret)
                                sheetViewState = null
                            },
                        )
                    }
                }
            }
        }
    }
}

class TotpSecretListItem(
    val totpSecret: TotpSecret,
    private val scope: CoroutineScope,
    private val hideSecretsFromAccessibility: Boolean,
    private val clock: Clock,
    private val getTotpCodes: suspend (TotpSecret) -> List<String>,
    private val onLongClickSecret: (TotpSecretListItem) -> Unit,
    private val onUpdateSecret: (TotpSecret) -> Unit,
) : ReorderableListItem {
    override val key: String
        get() = totpSecret.id.toString()

    override val sortOrder: Long
        get() = totpSecret.sortOrder

    override fun onClickLabel(appStrings: AppStrings): String =
        appStrings.secretsScreen.generateOneTimeCode

    override fun onLongClickLabel(appStrings: AppStrings): String =
        appStrings.generic.selectItem

    override fun filterPredicate(filter: String): Boolean =
        filter in totpSecret.issuer.lowercase() || filter in (totpSecret.account?.lowercase() ?: "")

    override fun onUpdateSortOrder(sortOrder: Long) {
        onUpdateSecret(totpSecret.copy(sortOrder = sortOrder))
    }

    override fun onClick() {
        scope.launch {
            ignoreAuthFailure {
                val codes = getTotpCodes(totpSecret)
                viewState = ListRowViewState.CodeVisible(codes)
            }
        }
    }

    override fun onLongClick() {
        onLongClickSecret(this)
    }

    fun update(updatedSecret: TotpSecret): TotpSecretListItem =
        TotpSecretListItem(
            totpSecret = updatedSecret,
            scope = scope,
            hideSecretsFromAccessibility = hideSecretsFromAccessibility,
            clock = clock,
            getTotpCodes = getTotpCodes,
            onLongClickSecret = onLongClickSecret,
            onUpdateSecret = onUpdateSecret,
        )

    private val dots = "\u2022".repeat(totpSecret.digits)
    private var viewState by mutableStateOf<ListRowViewState>(ListRowViewState.CodeHidden)
    private var totpCode by mutableStateOf(dots)
    private var progress by mutableFloatStateOf(0.0f)

    @Composable
    override fun RowScope.Render() {
        LaunchedEffect(viewState) {
            val state = viewState
            if (state is ListRowViewState.CodeVisible) {
                ignoreAuthFailure {
                    for (code in state.codes) {
                        totpCode = code
                        val now = clock.now()
                        val deciSecondsIntoPeriod = (now.epochSeconds % totpSecret.period) * 10
                        val deciSecondsPeriod = totpSecret.period * 10
                        for (step in deciSecondsIntoPeriod..deciSecondsPeriod) {
                            progress = 1 - step.toFloat() / deciSecondsPeriod
                            delay(100)
                        }
                    }
                }
                viewState = ListRowViewState.CodeHidden
                totpCode = dots
                progress = 0.0f
            }
        }

        Column(
            modifier = Modifier
                .padding(start = PADDING_M)
                .weight(1.0f)
        ) {
            Row {
                Text(
                    text = totpSecret.issuer,
                    fontSize = LIST_ITEM_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = totpSecret.account?.let { "(${it})" } ?: "",
                    fontSize = LIST_ITEM_FONT_SIZE,
                    color = MaterialTheme.colorScheme.primaryHint,
                    modifier = Modifier.padding(start = PADDING_S),
                )
            }
            Text(
                text = totpCode.chunked(3).joinToString("\u202F"),
                fontSize = SECRET_FONT_SIZE,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(top = PADDING_S)
                    .height(35.dp)
                    .semantics {
                        isSensitiveData = true
                        if (hideSecretsFromAccessibility) {
                            hideFromAccessibility()
                        }
                    },
                color = if (viewState is ListRowViewState.CodeVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primaryDisabled
                },
            )
        }
        if (viewState is ListRowViewState.CodeVisible) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(PADDING_S)
                    .size(36.dp),
                progress = { progress },
            )
        }
    }
}

private sealed interface ListRowViewState {
    object CodeHidden : ListRowViewState
    class CodeVisible(val codes: List<String>) : ListRowViewState
}

private sealed interface SheetViewState {
    object AddSecrets : SheetViewState
    data class SecretActions(val selectedItem: TotpSecretListItem) : SheetViewState
    data class EditSecretMetadata(val selectedItem: TotpSecretListItem) : SheetViewState
}

package se.koditoriet.snout.ui.screens.secrets

import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.ignoreAuthFailure
import se.koditoriet.snout.ui.primaryDisabled
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.sheets.AddSecretsSheet
import se.koditoriet.snout.ui.sheets.SecretActionsSheet
import se.koditoriet.snout.ui.theme.LIST_ITEM_FONT_SIZE
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XS
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import se.koditoriet.snout.ui.theme.SECRET_FONT_SIZE
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.vault.NewTotpSecret
import se.koditoriet.snout.vault.TotpSecret
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
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
    onEditSecretMetadata: (TotpSecret) -> Unit,
    onDeleteSecret: (TotpSecret) -> Unit,
    clock: Clock = Clock.System,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetViewState by remember { mutableStateOf<SheetViewState?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopBar(
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                filterEnabled = filter != null,
                onFilterToggle = { filter = if (filter == null) "" else null },
                onLockVault = onLockVault,
                onSettings = onSettings,
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_L)) {
                FloatingActionButton(onClick = { sheetViewState = SheetViewState.AddSecrets }) {
                    Icon(Icons.Filled.Add, appStrings.secretsScreen.addSecret)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val filterEnabled = filter != null
            val filterQuery = filter ?: ""

            AnimatedVisibility(filterEnabled) {
                FilterTextField(
                    filterEnabled = filterEnabled,
                    filterQuery = filterQuery,
                    onFilterChange = { filter = it },
                )
            }

            SecretList(
                filterQuery = filterQuery,
                secrets = secrets,
                selectedSecret = (sheetViewState as? SheetViewState.SecretActions)?.secret,
                sortMode = sortMode,
                hideSecretsFromAccessibility = hideSecretsFromAccessibility,
                clock = clock,
                getTotpCodes = getTotpCodes,
                onLongPressSecret = { sheetViewState = SheetViewState.SecretActions(it) }
            )
        }

        sheetViewState?.let { viewState ->
            BottomSheet(
                hideSheet = { sheetViewState = null },
                sheetState = sheetState,
                padding = padding,
            ) {
                when (viewState) {
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
                            totpSecret = viewState.secret,
                            onEditMetadata = {
                                onEditSecretMetadata(it)
                                sheetViewState = null
                            },
                            onDeleteSecret = {
                                onDeleteSecret(it)
                                sheetViewState = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretList(
    filterQuery: String,
    secrets: List<TotpSecret>,
    selectedSecret: TotpSecret?,
    sortMode: SortMode,
    hideSecretsFromAccessibility: Boolean,
    clock: Clock,
    getTotpCodes: suspend (TotpSecret) -> List<String>,
    onLongPressSecret: (TotpSecret) -> Unit,
) {
    val isManuallySortable = filterQuery.isEmpty() && sortMode == SortMode.Manual
    val lazyListState = rememberLazyListState()
    var reorderableSecrets by remember(secrets) { mutableStateOf(secrets.toList()) }
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderableSecrets = reorderableSecrets.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PADDING_S)
    ) {
        val filteredSecrets = when (filterQuery.isNotBlank()) {
            true -> {
                val filterParts = filterQuery
                    .split(' ')
                    .filter { it.isNotBlank() }
                    .map { it.lowercase() }
                reorderableSecrets.filter {
                    filterParts.all { f -> f in it.issuer.lowercase() || f in (it.account?.lowercase() ?: "") }
                }
            }

            false -> secrets
        }
        val sortedSecrets = when (sortMode) {
            SortMode.Manual -> filteredSecrets
            SortMode.Alphabetic -> filteredSecrets.sortedWith(
                compareBy<TotpSecret> { it.issuer.lowercase() }.thenBy { it.account?.lowercase() }
            )
        }
        items(
            items = if (isManuallySortable) reorderableSecrets else sortedSecrets,
            key = { it.id.toString() })
        { item ->
            ReorderableItem(reorderableLazyListState, key = item.id.toString()) { isDragging ->
                val reorderableScope = this
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                Surface(shadowElevation = elevation) {
                    ListRow(
                        totpSecret = item,
                        selected = item.id == selectedSecret?.id,
                        hideSecretsFromAccessibility = hideSecretsFromAccessibility,
                        clock = clock,
                        getTotpCodes = getTotpCodes,
                        onLongPressSecret = onLongPressSecret,
                        dragHandle = { DragHandle(reorderableScope, isManuallySortable) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTextField(
    filterEnabled: Boolean,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(filterEnabled) {
        if (filterEnabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PADDING_M)
            .focusRequester(focusRequester),
        value = filterQuery,
        singleLine = true,
        placeholder = { Text(appStrings.secretsScreen.filterPlaceholder) },
        onValueChange = { onFilterChange(it) },
        trailingIcon = {
            if (filterQuery.isNotEmpty()) {
                IconButton(onClick = { onFilterChange("") }) {
                    Icon(Icons.Default.Clear, appStrings.secretsScreen.filterClear)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    filterEnabled: Boolean,
    onFilterToggle: () -> Unit,
    onLockVault: () -> Unit,
    onSettings: () -> Unit,
) {
    val screenStrings = appStrings.secretsScreen
    TopAppBar(
        title = { Text(appStrings.generic.appName) },
        actions = {
            IconButton(
                onClick = {
                    val newSortMode = when (sortMode) {
                        SortMode.Manual -> SortMode.Alphabetic
                        SortMode.Alphabetic -> SortMode.Manual
                    }
                    onSortModeChange(newSortMode)
                }
            ) {
                val alphabeticSort = sortMode == SortMode.Alphabetic
                Icon(
                    imageVector = Icons.Filled.SortByAlpha,
                    contentDescription = screenStrings.sortAlphabetically(alphabeticSort),
                    tint = if (alphabeticSort) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onFilterToggle) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = screenStrings.filter(filterEnabled),
                    tint = if (filterEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onLockVault) {
                Icon(Icons.Filled.LockOpen, screenStrings.lockScreen)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, screenStrings.settings)
            }
        }
    )
}

@Composable
private fun ListRow(
    totpSecret: TotpSecret,
    selected: Boolean,
    hideSecretsFromAccessibility: Boolean,
    clock: Clock,
    getTotpCodes: suspend (TotpSecret) -> List<String>,
    onLongPressSecret: (TotpSecret) -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dots = remember { "\u2022".repeat(totpSecret.digits) }
    var viewState by remember { mutableStateOf<ListRowViewState>(ListRowViewState.CodeHidden) }
    var totpCode by remember { mutableStateOf(dots) }
    var progress by remember { mutableFloatStateOf(0.0f) }

    LaunchedEffect(viewState) {
        val state = viewState
        if (state is ListRowViewState.CodeVisible) {
            try {
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
            } catch (_: AuthenticationFailedException) {
                // code remains hidden
            } finally {
                viewState = ListRowViewState.CodeHidden
                totpCode = dots
                progress = 0.0f
            }
        }
    }

    val backgroundColor = when (selected) {
        true -> MaterialTheme.colorScheme.surfaceBright
        false -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    scope.launch {
                        ignoreAuthFailure {
                            val codes = getTotpCodes(totpSecret)
                            viewState = ListRowViewState.CodeVisible(codes)
                        }
                    }
                },
                onLongClick = { onLongPressSecret(totpSecret) },
            )
            .padding(PADDING_XS)
            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE))
            .background(backgroundColor)
            .padding(PADDING_M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    .padding(PADDING_M)
                    .size(48.dp),
                progress = { progress },
            )
        }
        dragHandle()
    }
}

@Composable
fun DragHandle(scope: ReorderableCollectionItemScope, showDragHandle: Boolean) {
    if (showDragHandle) {
        IconButton(
            modifier = with(scope) { Modifier.draggableHandle() },
            onClick = {}
        ) {
            Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder")
        }
    }
}

private sealed interface ListRowViewState {
    object CodeHidden : ListRowViewState
    class CodeVisible(val codes: List<String>) : ListRowViewState
}

private sealed interface SheetViewState {
    object AddSecrets : SheetViewState
    data class SecretActions(val secret: TotpSecret) : SheetViewState
}

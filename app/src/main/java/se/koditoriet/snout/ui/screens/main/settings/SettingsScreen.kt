package se.koditoriet.snout.ui.screens.main.settings

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.IrrevocableActionConfirmationDialog
import se.koditoriet.snout.ui.components.sheet.BottomSheet
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.screens.main.settings.sheets.SecurityReportSheet
import se.koditoriet.snout.ui.theme.GRACE_PERIOD_INPUT_FIELD_HEIGHT
import se.koditoriet.snout.ui.theme.GRACE_PERIOD_INPUT_FIELD_WIDTH
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_PADDING
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_M
import se.koditoriet.snout.ui.theme.SPACING_S
import se.koditoriet.snout.ui.theme.SPACING_XS
import se.koditoriet.snout.viewmodel.SecurityReport
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    enableBackups: Boolean,
    onDisableBackups: () -> Unit,
    protectAccountList: Boolean,
    onProtectAccountListChange: (Boolean) -> Unit,
    lockOnClose: Boolean,
    onLockOnCloseChange: (Boolean) -> Unit,
    lockOnCloseGracePeriod: Int,
    onLockOnCloseGracePeriodChange: (Int) -> Unit,
    screenSecurityEnabled: Boolean,
    onScreenSecurityEnabledChange: (Boolean) ->Unit,
    hideSecretsFromAccessibility: Boolean,
    onHideSecretsFromAccessibilityChange: (Boolean) ->Unit,
    enableDeveloperFeatures: Boolean,
    onEnableDeveloperFeaturesChange: (Boolean) ->Unit,
    onWipeVault: () -> Unit,
    onExport: (Uri) -> Unit,
    onManagePasskeys: () -> Unit,
    getSecurityReport: suspend () -> SecurityReport,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val screenStrings = appStrings.settingsScreen
    var sheetViewState by remember { mutableStateOf<SettingsScreenSheetViewState?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showDisableBackupsDialog by remember { mutableStateOf(false) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = rememberLifecycleOwner()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingActionRow(
                    title = screenStrings.managePasskeys,
                    description = screenStrings.managePasskeysDescription,
                    onClick = onManagePasskeys,
                )
            }

            // Enable backups
            item {
                SettingSwitchRow(
                    title = screenStrings.enableBackups,
                    description = screenStrings.enableBackupsDescription,
                    checked = enableBackups,
                    enabled = enableBackups,
                    onCheckedChange = { showDisableBackupsDialog = true },
                ) {
                    val exportFileLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                        onResult = { it?.run(onExport) }
                    )
                    val suggestedFileName = fileNameFromDate("vault-export-", ".eve", clock, timeZone)
                    AssistChip(
                        onClick = { exportFileLauncher.launch(suggestedFileName) },
                        label = { Text(screenStrings.enableBackupsExport) },
                    )
                }
            }

            // Biometric lock
            item {
                SettingSwitchRow(
                    title = screenStrings.biometricLock,
                    description = screenStrings.biometricLockDescription,
                    checked = protectAccountList,
                    onCheckedChange = onProtectAccountListChange,
                )
            }

            // Lock on minimize / grace period
            item {
                SettingSwitchRow(
                    title = screenStrings.lockOnMinimize,
                    description = screenStrings.lockOnMinimizeDescription,
                    checked = lockOnClose,
                    onCheckedChange = onLockOnCloseChange,
                ) {
                    val partialValue = remember { mutableStateOf(lockOnCloseGracePeriod.toString()) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = partialValue.value,
                            onValueChange = { value ->
                                partialValue.value = value
                                partialValue.value.toIntOrNull()?.takeIf { it >= 0 }?.let {
                                    onLockOnCloseGracePeriodChange(it)
                                }
                            },
                            label = { Text(screenStrings.lockOnMinimizeGracePeriod) },
                            suffix = {
                                Text(
                                    text = appStrings.generic.seconds,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .width(GRACE_PERIOD_INPUT_FIELD_WIDTH)
                                .height(GRACE_PERIOD_INPUT_FIELD_HEIGHT),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            isError = partialValue.value.toIntOrNull()?.let { it < 0 } ?: true,
                        )
                    }
                }
            }

            // Screen security
            item {
                SettingSwitchRow(
                    title = screenStrings.screenSecurity,
                    description = screenStrings.screenSecurityDescription,
                    checked = screenSecurityEnabled,
                    onCheckedChange = onScreenSecurityEnabledChange,
                )
            }

            // Hide secrets from screen readers (accessibility tools)
            item {
                SettingSwitchRow(
                    title = screenStrings.hideSecretsFromScreenReaders,
                    description = screenStrings.hideSecretsFromScreenReadersDescription,
                    checked = hideSecretsFromAccessibility,
                    onCheckedChange = onHideSecretsFromAccessibilityChange,
                )
            }

            // Enable dev features
            item {
                SettingSwitchRow(
                    title = screenStrings.enableDeveloperFeatures,
                    description = screenStrings.enableDeveloperFeaturesDescription,
                    checked = enableDeveloperFeatures,
                    onCheckedChange = onEnableDeveloperFeaturesChange,
                )
            }

            // Key storage overview
            item {
                SettingActionRow(
                    title = screenStrings.keyStorageOverview,
                    description = screenStrings.keyStorageOverviewDescription,
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            sheetViewState = SettingsScreenSheetViewState.SecurityReportSheet(getSecurityReport())
                        }
                    }
                )
            }

            // Erase data
            item {
                SettingActionRow(
                    title = screenStrings.eraseData,
                    description = screenStrings.eraseDataDescription,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showWipeDialog = true }
                )
            }
        }

        // Confirmation dialog - erase data
        if (showWipeDialog) {
            IrrevocableActionConfirmationDialog(
                text = screenStrings.eraseDataDialogText,
                buttonText = screenStrings.eraseDataDialogConfirm,
                onCancel = { showWipeDialog = false },
                onConfirm = {
                    showWipeDialog = false
                    onWipeVault()
                }
            )
        }

        // Confirmation dialog - disable backups
        if (showDisableBackupsDialog) {
            IrrevocableActionConfirmationDialog(
                text = screenStrings.enableBackupsDisableDialogText,
                buttonText = screenStrings.enableBackupsDisableDialogConfirm,
                onCancel = { showDisableBackupsDialog = false },
                onConfirm = {
                    showDisableBackupsDialog = false
                    onDisableBackups()
                }
            )
        }

        // Overview sheet - security
        sheetViewState?.let { viewState ->
            BottomSheet(
                hideSheet = { sheetViewState = null },
                sheetState = sheetState,
                padding = padding,
                sheetViewState = viewState,
            ) {
                when (viewState) {
                    is SettingsScreenSheetViewState.SecurityReportSheet -> {
                        SecurityReportSheet(viewState.report)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    checkedContent: (@Composable () -> Unit)? = null,
) {
    SettingsCard { padding ->
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(SPACING_M),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(SPACING_XS)
            ) {
                SettingsInfo(title, description)
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            }

            if (checkedContent != null) {
                AnimatedVisibility(checked) {
                    checkedContent()
                }
            }
        }
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    description: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    SettingsCard { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(padding)
        ) {
            SettingsTitle(title, titleColor)
            SettingsDescription(description)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.(PaddingValues) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_L, vertical = SPACING_S),
        shape = RoundedCornerShape(ROUNDED_CORNER_SIZE),
    ) {
        content(PaddingValues(ROUNDED_CORNER_PADDING))
    }
}

@Composable
private fun SettingsTitle(title: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

@Composable
private fun SettingsDescription(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primaryHint,
    )
}


@Composable
private fun RowScope.SettingsInfo(
    title: String,
    description: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(Modifier.weight(1f)) {
        SettingsTitle(title, titleColor)
        SettingsDescription(description)
    }
}

private fun fileNameFromDate(prefix: String, suffix: String, clock: Clock, timeZone: TimeZone): String {
    val localNow = clock.now().toLocalDateTime(timeZone)
    val dateString = LocalDate.Formats.ISO.format(localNow.date)
    return "$prefix$dateString$suffix"
}

private sealed interface SettingsScreenSheetViewState {
    class SecurityReportSheet(val report: SecurityReport) : SettingsScreenSheetViewState
}

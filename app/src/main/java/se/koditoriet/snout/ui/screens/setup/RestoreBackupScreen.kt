package se.koditoriet.snout.ui.screens.setup

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.ui.components.BadInputInformationDialog
import se.koditoriet.snout.ui.components.MainButton
import se.koditoriet.snout.ui.components.QrScannerScreen
import se.koditoriet.snout.ui.components.SecondaryButton
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.theme.PADDING_XL
import se.koditoriet.snout.ui.theme.SPACING_M
import se.koditoriet.snout.ui.theme.SPACING_S

private const val TAG = "RestoreBackupScreen"
private val BACKUP_MIME_TYPES = arrayOf("application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    wordCount: Int = 24,
    seedWords: Set<String>,
    onRestore: (BackupSeed, Uri) -> Unit
) {
    val screenStrings = appStrings.seedInputScreen
    val words = remember { mutableStateListOf(*Array(wordCount) { "" }) }
    val focusRequesters = remember { List(wordCount) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var scanSecretQRCode by remember { mutableStateOf(false) }
    var backupSeed by remember { mutableStateOf<BackupSeed?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.run {
                backupSeed?.let { backupSeed ->
                    onRestore(backupSeed, uri)
                } ?: Log.e(TAG, "Backup seed was null when import file launcher completed!")
            } ?: backupSeed?.wipe()
        }
    )

    if (scanSecretQRCode) {
        var invalidBackupSeedQR by remember { mutableStateOf(false) }
        QrScannerScreen(
            onQrScanned = {
                if (!invalidBackupSeedQR) {
                    // Don't interpret QR codes while the "invalid backup seed" dialog is active
                    try {
                        backupSeed = BackupSeed.fromUri(it.toUri())
                        importFileLauncher.launch(BACKUP_MIME_TYPES)
                        scanSecretQRCode = false
                    } catch (e: Exception) {
                        invalidBackupSeedQR = true
                        Log.w(TAG, "Scanned QR code is not a valid backup seed", e)
                    }
                }
            }
        )
        if (invalidBackupSeedQR) {
            BadInputInformationDialog(
                title = screenStrings.invalidSeedQRCode,
                text = screenStrings.invalidSeedQRCodeDescription,
                onDismiss = { invalidBackupSeedQR = false }
            )
        }
    } else {
        var invalidBackupSeedPhrase by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(screenStrings.enterRecoveryPhrase) }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Column(
                    modifier = Modifier
                        .padding(PADDING_XL)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(SPACING_S),
                        horizontalArrangement = Arrangement.spacedBy(SPACING_M),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(wordCount) { index ->
                            SeedWordInput(
                                index = index,
                                isLastWord = index == wordCount - 1,
                                seedWords = seedWords,
                                onValueChange = { words[index] = it },
                                onNextWord = {
                                    scope.launch {
                                        delay(50)
                                        focusRequesters[index + 1].requestFocus()
                                    }
                                },
                                focusRequester = focusRequesters[index],
                            )
                        }
                    }

                    Spacer(Modifier.height(SPACING_M))
                }

                if (invalidBackupSeedPhrase) {
                    BadInputInformationDialog(
                        title = screenStrings.invalidSeedPhrase,
                        text = screenStrings.invalidSeedPhraseDescription,
                        onDismiss = { invalidBackupSeedPhrase = false }
                    )
                }

                val seedPhraseIsValid = words.all { it in seedWords }
                MainButton(
                    text = screenStrings.restoreVault,
                    enabled = seedPhraseIsValid,
                    onClick = {
                        if (seedPhraseIsValid) {
                            try {
                                backupSeed = BackupSeed.fromMnemonic(words)
                                importFileLauncher.launch(BACKUP_MIME_TYPES)
                            } catch (e: Exception) {
                                invalidBackupSeedPhrase = true
                                Log.w(TAG, "Invalid seed phrase", e)
                            }
                        }
                    },
                    secondaryButton = SecondaryButton(
                        text = screenStrings.scanQRCode,
                        onClick = { scanSecretQRCode = true },
                    ),
                )
            }
        }
    }
}

@Composable
fun SeedWordInput(
    index: Int,
    isLastWord: Boolean,
    seedWords: Set<String>,
    onValueChange: (String) -> Unit,
    onNextWord: () -> Unit,
    focusRequester: FocusRequester,
) {
    var value by remember { mutableStateOf("") }

    Column {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primaryHint,
        )

        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it.lowercase().trim()
                onValueChange(value)
                if (it.endsWith(" ") && !isLastWord && it.trim() in seedWords) {
                    onNextWord()
                }
            },
            modifier = Modifier
                .semantics {
                    isSensitiveData = true
                }
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            singleLine = true,
            isError = value.isNotBlank() && value !in seedWords,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = if (isLastWord) ImeAction.Done else ImeAction.Next,
                keyboardType = KeyboardType.Password,
            )
        )
    }
}

package se.koditoriet.snout.ui.screens.setup

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.ui.theme.BACKUP_SEED_QR_CODE_HEIGHT
import se.koditoriet.snout.ui.theme.BACKUP_SEED_QR_CODE_WIDTH
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XL
import se.koditoriet.snout.ui.theme.PADDING_XXS
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_S
import se.koditoriet.snout.ui.theme.SPACING_XL
import se.koditoriet.snout.ui.theme.SPACING_XS
import android.graphics.Color as BitmapColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSeedScreen(
    backupSeed: BackupSeed,
    onPrintQr: (String, Bitmap) -> Unit,
    onContinue: () -> Unit
) {
    val screenStrings = appStrings.seedDisplayScreen
    val openPrintDialog = remember { mutableStateOf(false) }

    PrintQrWarningDialog(
        openPrintDialog = openPrintDialog,
        onConfirmation = {
            printRecoveryPhrase(backupSeed.toMnemonic(), onPrintQr)
            openPrintDialog.value = false
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(screenStrings.recoveryPhrase) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(PADDING_XL)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            Column {
                Text(
                    text = screenStrings.writeThisDown,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(SPACING_L))

                MnemonicGrid(mnemonic = backupSeed.toMnemonic())

                Spacer(Modifier.height(SPACING_L))

                Text(
                    text = screenStrings.keepThemSafe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openPrintDialog.value = true },
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(PADDING_XXS)
                ) {
                    Text(screenStrings.printAsQr)
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(PADDING_XXS)
                ) {
                    Text(appStrings.generic.continueOn)
                }
            }
        }
    }
}

@Composable
fun MnemonicGrid(
    mnemonic: List<String>,
    columns: Int = 3,
) {
    val rows = (mnemonic.size + columns - 1) / columns
    Column {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SPACING_S)
            ) {
                for (colIndex in 0 until columns) {
                    val wordIndex = rowIndex * columns + colIndex
                    if (wordIndex < mnemonic.size) {
                        MnemonicWordCard(
                            modifier = Modifier.weight(1.0f),
                            index = wordIndex + 1,
                            word = mnemonic[wordIndex]
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(SPACING_S))
        }
    }
}

@Composable
fun MnemonicWordCard(index: Int, word: String, modifier: Modifier) {
    Card(
        modifier = modifier.padding(PADDING_XXS),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(PADDING_S)
        ) {
            Text(
                "$index.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(SPACING_XL),
            )
            Spacer(Modifier.width(SPACING_XS))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PrintQrWarningDialog(
    openPrintDialog: MutableState<Boolean>,
    onConfirmation: () -> Unit
) {
    when {
        openPrintDialog.value -> {
            val onDismissRequest = { openPrintDialog.value = false }
            AlertDialog(
                icon = { Icon(Icons.Default.Warning, contentDescription = "Warning icon") },
                text = { Text(text = appStrings.seedDisplayScreen.printAsQrWarning, color = Color.Red) },
                onDismissRequest = onDismissRequest,
                confirmButton = {
                    TextButton(onClick = onConfirmation) {
                        Text(appStrings.generic.continueOn)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(appStrings.generic.cancel)
                    }
                }
            )
        }
    }
}

private fun printRecoveryPhrase(recoveryPhraseAsString: List<String>, onPrint: (String, Bitmap) -> Unit) {
    val bitmap = recoveryPhraseToBitmap(recoveryPhraseAsString)
    onPrint("PrintRecoveryPhrase", bitmap)
}

private fun recoveryPhraseToBitmap(recoveryPhraseAsString: List<String>): Bitmap {
    val bitMatrix =
        QRCodeWriter().encode(recoveryPhraseAsString.joinToString("-"), BarcodeFormat.QR_CODE, BACKUP_SEED_QR_CODE_WIDTH, BACKUP_SEED_QR_CODE_HEIGHT)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = createBitmap(width, height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bitmap[x, y] = if (bitMatrix.get(x, y)) BitmapColor.BLACK else BitmapColor.WHITE
        }
    }

    return bitmap
}
package se.koditoriet.snout.ui.screens.setup

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XL
import se.koditoriet.snout.ui.theme.PADDING_XXS
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_S
import se.koditoriet.snout.ui.theme.SPACING_XL
import se.koditoriet.snout.ui.theme.SPACING_XS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSeedScreen(
    backupSeed: BackupSeed,
    onContinue: () -> Unit
) {
    val screenStrings = appStrings.seedDisplayScreen
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

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(appStrings.generic.continueOn)
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

package se.koditoriet.snout.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.ui.components.LoadingSpinner
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.theme.PADDING_XL
import se.koditoriet.snout.ui.theme.SPACING_M
import se.koditoriet.snout.ui.theme.SPACING_S

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    wordCount: Int = 24,
    seedWords: Set<String>,
    onRestore: (BackupSeed, Uri) -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    val screenStrings = appStrings.seedInputScreen
    val words = remember { MutableList(wordCount) { "" } }
    val focusRequesters = remember { List(wordCount) { FocusRequester() } }
    val scope = rememberCoroutineScope()

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            it?.run {
                loading = true
                val backupSeed = BackupSeed.fromMnemonic(words)
                onRestore(backupSeed, it)
                loading = false
            }
        }
    )
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(screenStrings.enterRecoveryPhrase) }
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

            Button(
                onClick = {
                    if (words.all { it.isNotBlank() && it in seedWords }) {
                        importFileLauncher.launch(arrayOf("application/octet-stream"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) {
                    LoadingSpinner()
                } else {
                    Text(screenStrings.restoreVault)
                }
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
    val value = remember { mutableStateOf("") }

    Column {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primaryHint,
        )

        OutlinedTextField(
            value = value.value,
            onValueChange = {
                value.value = it.lowercase().trim()
                onValueChange(value.value)
                if (it.endsWith(" ") && !isLastWord) {
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
            isError = value.value.isNotBlank() && value.value !in seedWords,
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

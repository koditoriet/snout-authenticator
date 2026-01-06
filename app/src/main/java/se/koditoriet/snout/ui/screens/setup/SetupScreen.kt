@file:OptIn(ExperimentalMaterial3Api::class)

package se.koditoriet.snout.ui.screens.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.appStrings

@Composable
fun BackupSetupScreen(
    onEnableBackups: () -> Unit,
    onSkipBackups: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val screenStrings = appStrings.setupScreen
    var backupChoice by remember { mutableStateOf(BackupChoice.EnableBackups) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(screenStrings.welcome) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            item {
                Column {
                    Text(
                        text = screenStrings.enableBackups,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = screenStrings.enableBackupsDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(32.dp))

                    BackupChoiceCard(
                        title = screenStrings.enableBackupsCardEnable,
                        description = screenStrings.enableBackupsCardEnableDescription,
                        selected = backupChoice == BackupChoice.EnableBackups,
                        onClick = { backupChoice = BackupChoice.EnableBackups }
                    )

                    Spacer(Modifier.height(12.dp))

                    BackupChoiceCard(
                        title = screenStrings.enableBackupsCardDisable,
                        description = screenStrings.enableBackupsCardDisableDescription,
                        selected = backupChoice == BackupChoice.DisableBackups,
                        onClick = { backupChoice = BackupChoice.DisableBackups }
                    )

                    Spacer(Modifier.height(12.dp))

                    BackupChoiceCard(
                        title = screenStrings.enableBackupsCardImport,
                        description = screenStrings.enableBackupsCardImportDescription,
                        selected = backupChoice == BackupChoice.ImportAndEnableBackups,
                        onClick = { backupChoice = BackupChoice.ImportAndEnableBackups }
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        when (backupChoice) {
                            BackupChoice.DisableBackups -> onSkipBackups()
                            BackupChoice.EnableBackups -> onEnableBackups()
                            BackupChoice.ImportAndEnableBackups -> onRestoreBackup()
                        }
                    }
                ) {
                    Text(appStrings.generic.continueOn)
                }
            }
        }
    }
}

@Composable
private fun BackupChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        }  else {
            MaterialTheme.colorScheme.outline
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private enum class BackupChoice {
    DisableBackups,
    EnableBackups,
    ImportAndEnableBackups,
}
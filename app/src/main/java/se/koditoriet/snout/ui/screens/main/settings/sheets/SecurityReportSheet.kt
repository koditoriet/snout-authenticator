package se.koditoriet.snout.ui.screens.main.settings.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.KeySecurityLevel
import se.koditoriet.snout.ui.components.sheet.BottomSheetGlobalHeader
import se.koditoriet.snout.ui.components.sheet.BottomSheetItem
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.viewmodel.SecurityReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityReportSheet(securityReport: SecurityReport) {
    BottomSheetGlobalHeader(
        heading = appStrings.settingsScreen.keyStorageOverviewDescriptionSheetHeading,
        details = appStrings.settingsScreen.keyStorageOverviewDescriptionSheetDescription,
    )

    BackupKeyGrade(securityReport.backupKeyStatus)

    for ((securityLevel, secrets) in securityReport.secretsStatus.toList().sortedBy { it.first }.reversed()) {
        ReportItem(
            securityLevel.grade,
            appStrings.settingsScreen.keyStorageOverviewDescriptionSheetSecretGroupGrade(
                groupSize = secrets,
                totalSecrets = securityReport.totalSecrets,
                storageClass = securityLevel.description,
            ),
        )
    }

    for ((securityLevel, secrets) in securityReport.passkeysStatus.toList().sortedBy { it.first }.reversed()) {
        ReportItem(
            securityLevel.grade,
            appStrings.settingsScreen.keyStorageOverviewDescriptionSheetPasskeyGroupGrade(
                groupSize = secrets,
                totalPasskeys = securityReport.totalPasskeys,
                storageClass = securityLevel.description,
            ),
        )
    }
}

@Composable
private fun BackupKeyGrade(backupKeyStatus: KeySecurityLevel?) {
    val description = backupKeyStatus?.let {
        appStrings.settingsScreen.keyStorageOverviewDescriptionSheetDescription(it.description)
    } ?: appStrings.settingsScreen.keyStorageOverviewDescriptionSheetBackupsDisabled
    ReportItem(backupKeyStatus.grade, description)
}

@Composable
private fun ReportItem(
    grade: Grade,
    description: String,
) {
    BottomSheetItem {
        Icon(
            imageVector = grade.imageVector,
            contentDescription = grade.description,
            tint = grade.color,
        )
        Spacer(Modifier.width(SPACING_L))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private enum class Grade {
    Weak,
    Good,
    Excellent;

    val imageVector: ImageVector
        get() = when (this) {
            Excellent -> Icons.Default.Star
            Good -> Icons.Default.CheckCircle
            Weak -> Icons.Default.WarningAmber
        }

    val description: String
        @Composable
        get() = when (this) {
            Excellent -> appStrings.settingsScreen.keyStorageOverviewDescriptionSheetGradeExcellent
            Good -> appStrings.settingsScreen.keyStorageOverviewDescriptionSheetGradeGood
            Weak -> appStrings.settingsScreen.keyStorageOverviewDescriptionSheetGradeWeak
        }

    val color: Color
        @Composable
        get() = when (this) {
            Excellent -> Color(0xFF00B3A4)
            Good -> Color(0xFF3F8EF4)
            Weak -> Color(0xFFFFB020)
        }
}

private val KeySecurityLevel.description: String
    @Composable
    get() = when (this) {
        KeySecurityLevel.StrongBox -> appStrings.generic.keyStorageStrongbox
        KeySecurityLevel.TEE -> appStrings.generic.keyStorageTee
        KeySecurityLevel.Software -> appStrings.generic.keyStorageSoftware
        KeySecurityLevel.Unknown -> appStrings.generic.keyStorageUnknown
    }

private val KeySecurityLevel?.grade: Grade
    get() = when (this) {
        KeySecurityLevel.StrongBox -> Grade.Excellent
        KeySecurityLevel.TEE -> Grade.Good
        KeySecurityLevel.Software, KeySecurityLevel.Unknown -> Grade.Weak
        null -> Grade.Good
    }

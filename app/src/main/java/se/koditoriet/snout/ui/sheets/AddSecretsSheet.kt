package se.koditoriet.snout.ui.sheets

import BottomSheetAction
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.codec.QrCodeReader
import se.koditoriet.snout.ui.components.sheet.BottomSheetGlobalHeader
import se.koditoriet.snout.ui.supportedImageMimeTypes
import se.koditoriet.snout.vault.NewTotpSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSecretsSheet(
    onAddSecretByQR: () -> Unit,
    onAddSecret: (NewTotpSecret?) -> Unit,
) {
    val screenStrings = appStrings.secretsScreen
    val ctx = LocalContext.current
    val hasCamera = remember {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            it?.run {
                val bitmap = ctx.contentResolver.openInputStream(it).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                val qrCodeReader = QrCodeReader()
                qrCodeReader.tryScanBitmap(bitmap)?.let { uri ->
                    onAddSecret(NewTotpSecret.fromUri(uri))
                }
            }
        }
    )

    BottomSheetGlobalHeader(
        heading = screenStrings.addSecretSheetHeading,
        details = screenStrings.addSecretSheetDescription,
    )
    if (hasCamera) {
        BottomSheetAction(
            icon = Icons.Default.QrCodeScanner,
            text = screenStrings.addSecretSheetScanQrCode,
            onClick = onAddSecretByQR,
        )
    }
    BottomSheetAction(
        icon = Icons.Default.Image,
        text = screenStrings.addSecretSheetScanImage,
        onClick = { importFileLauncher.launch(supportedImageMimeTypes) },
    )
    BottomSheetAction(
        icon = Icons.Default.Edit,
        text = screenStrings.addSecretSheetEnterManually,
        onClick = { onAddSecret(null) },
    )
}

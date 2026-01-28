package se.koditoriet.snout.ui

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import se.koditoriet.snout.SnoutApp
import se.koditoriet.snout.crypto.AuthenticationFailedException
import se.koditoriet.snout.vault.Passkey

val ColorScheme.primaryDisabled: Color
    get() = this.onSurface.copy(0.38f)

val ColorScheme.primaryHint: Color
    get() = this.onSurface.copy(0.6f)

fun FragmentActivity.onIOThread(f: suspend () -> Any): () -> Unit =
    { lifecycleScope.launch(Dispatchers.IO) { f() } }

fun <T> FragmentActivity.onIOThread(f: suspend (T) -> Any): (T) -> Unit =
    { lifecycleScope.launch(Dispatchers.IO) { f(it) } }

fun <T, U> FragmentActivity.onIOThread(f: suspend (T, U) -> Any): (T, U) -> Unit =
    { a, b -> lifecycleScope.launch(Dispatchers.IO) { f(a, b) } }

val supportedImageMimeTypes: Array<String> = arrayOf(
    "image/png",
    "image/jpeg",
    "image/webp",
    "image/gif",
    "image/bmp",
    "image/tiff",
)

val supportedImportFileTypes: Array<String> = arrayOf(
    "application/json",
    "application/octet-stream",
)

suspend fun ignoreAuthFailure(action: suspend () -> Unit) {
    try {
        action()
    } catch (_: AuthenticationFailedException) {
        // nop!
    }
}

val Activity.snoutApp: SnoutApp
    get() = application as SnoutApp

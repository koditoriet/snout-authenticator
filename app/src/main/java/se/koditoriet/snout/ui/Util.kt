package se.koditoriet.snout.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

package se.koditoriet.snout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.theme.SPACING_XL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedScreen(
    onUnlock: () -> Unit,
) {
    val screenStrings = appStrings.lockScreen

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onUnlock() },
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = screenStrings.vaultLocked,
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(Modifier.height(SPACING_XL))

                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = screenStrings.tapToUnlock,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

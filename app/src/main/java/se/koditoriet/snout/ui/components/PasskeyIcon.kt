package se.koditoriet.snout.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import se.koditoriet.snout.R

@Composable
fun PasskeyIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        modifier = modifier,
        painter = painterResource(R.drawable.outline_passkey_24),
        contentDescription = null,
        tint = tint,
    )
}

package se.koditoriet.snout.ui.components.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import se.koditoriet.snout.ui.theme.PADDING_L
import se.koditoriet.snout.ui.theme.SPACING_S

@Composable
fun BottomSheetGlobalHeader(heading: String, details: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = PADDING_L),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (details != null) {
            Spacer(Modifier.height(SPACING_S))
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

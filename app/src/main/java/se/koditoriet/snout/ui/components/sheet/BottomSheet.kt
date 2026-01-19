package se.koditoriet.snout.ui.components.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.ui.theme.PADDING_L
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_PADDING
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_S

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    hideSheet: () -> Unit,
    sheetState: SheetState,
    padding: PaddingValues,
    content: @Composable ColumnScope.(hideSheet: () -> Unit) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        ModalBottomSheet(
            onDismissRequest = hideSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = ROUNDED_CORNER_PADDING, topEnd = ROUNDED_CORNER_PADDING)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PADDING_L),
                verticalArrangement = Arrangement.spacedBy(SPACING_L),
            ) {
                content(hideSheet)
                Spacer(Modifier.height(SPACING_S))
            }
        }
    }
}

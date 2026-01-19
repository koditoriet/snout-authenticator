package se.koditoriet.snout.ui.components.sheet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import se.koditoriet.snout.ui.theme.PADDING_XL
import se.koditoriet.snout.ui.theme.PADDING_XS


@Composable
fun BottomSheetItem(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PADDING_XS, horizontal = PADDING_XL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

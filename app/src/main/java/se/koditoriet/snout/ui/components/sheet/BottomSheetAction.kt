import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import se.koditoriet.snout.ui.components.sheet.BottomSheetItem
import se.koditoriet.snout.ui.theme.SPACING_L

@Composable
fun BottomSheetAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    BottomSheetItem(modifier = Modifier.clickable(onClick = onClick)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(SPACING_L))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

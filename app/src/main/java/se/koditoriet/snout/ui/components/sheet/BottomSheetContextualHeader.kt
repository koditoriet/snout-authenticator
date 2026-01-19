import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_S

@Composable
fun BottomSheetContextualHeader(
    heading: String,
    subheading: String,
    icon: ImageVector,
) {
    Column {
        Surface(
            shape = RoundedCornerShape(ROUNDED_CORNER_SIZE),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier
                .padding(horizontal = PADDING_M)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = PADDING_M, vertical = PADDING_S)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(SPACING_S))
                Column {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subheading,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(SPACING_L))
    }
}

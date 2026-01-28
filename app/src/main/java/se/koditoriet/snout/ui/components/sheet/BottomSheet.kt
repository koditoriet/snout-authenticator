package se.koditoriet.snout.ui.components.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
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
fun <S> BottomSheet(
    hideSheet: () -> Unit,
    sheetState: SheetState,
    padding: PaddingValues? = null,
    sheetViewState: S,
    content: @Composable ColumnScope.(state: S) -> Unit,
) {
    val modifier = Modifier.fillMaxSize()
    Box(modifier.takeIf { padding != null }?.padding(padding!!) ?: modifier) {
        ModalBottomSheet(
            onDismissRequest = hideSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = ROUNDED_CORNER_PADDING, topEnd = ROUNDED_CORNER_PADDING)
        ) {
            AnimatedContent(
                targetState = sheetViewState,
                transitionSpec = {
                    (fadeIn()).togetherWith(fadeOut()).using(
                        SizeTransform(clip = false)
                    )
                }
            ) { state ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PADDING_L),
                    verticalArrangement = Arrangement.spacedBy(SPACING_L),
                ) {
                    content(state)
                    Spacer(Modifier.height(SPACING_S))
                }
            }
        }
    }
}

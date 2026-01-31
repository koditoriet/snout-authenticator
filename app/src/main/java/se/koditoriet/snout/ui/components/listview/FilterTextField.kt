package se.koditoriet.snout.ui.components.listview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.theme.PADDING_M

@Composable
fun FilterTextField(
    filterEnabled: Boolean,
    filterQuery: String,
    placeholderText: String,
    onFilterChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(filterEnabled) {
        if (filterEnabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    AnimatedVisibility(filterEnabled) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PADDING_M)
                .focusRequester(focusRequester),
            value = filterQuery,
            singleLine = true,
            placeholder = { Text(placeholderText) },
            onValueChange = { onFilterChange(it) },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange("") }) {
                        Icon(Icons.Default.Clear, appStrings.listView.filterClear)
                    }
                }
            }
        )
    }
}

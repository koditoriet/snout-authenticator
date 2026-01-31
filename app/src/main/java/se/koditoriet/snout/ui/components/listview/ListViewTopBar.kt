package se.koditoriet.snout.ui.components.listview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListViewTopBar(
    title: String,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    filterEnabled: Boolean,
    onFilterToggle: () -> Unit,
    navigationIcon: @Composable () -> Unit = { },
    content: @Composable () -> Unit = { },
) {
    val screenStrings = appStrings.listView
    TopAppBar(
        title = { Text(title) },
        navigationIcon = navigationIcon,
        actions = {
            IconButton(
                onClick = {
                    val newSortMode = when (sortMode) {
                        SortMode.Manual -> SortMode.Alphabetic
                        SortMode.Alphabetic -> SortMode.Manual
                    }
                    onSortModeChange(newSortMode)
                }
            ) {
                val alphabeticSort = sortMode == SortMode.Alphabetic
                Icon(
                    imageVector = Icons.Filled.SortByAlpha,
                    contentDescription = screenStrings.sortAlphabetically(alphabeticSort),
                    tint = if (alphabeticSort) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onFilterToggle) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = screenStrings.filter(filterEnabled),
                    tint = if (filterEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            content()
        }
    )
}

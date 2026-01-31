package se.koditoriet.snout.ui.components.listview

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.theme.PADDING_M
import se.koditoriet.snout.ui.theme.PADDING_S
import se.koditoriet.snout.ui.theme.PADDING_XS
import se.koditoriet.snout.ui.theme.ROUNDED_CORNER_SIZE
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

interface ReorderableListItem {
    val key: String
    val sortOrder: Long
    val onClickLabel: String
    val onLongClickLabel: String
    fun filterPredicate(filter: String): Boolean
    fun onUpdateSortOrder(sortOrder: Long)
    fun onClick()
    fun onLongClick()
    @Composable fun RowScope.Render()
}

@Composable
fun <T : ReorderableListItem> ReorderableList(
    padding: PaddingValues,
    filter: String?,
    items: List<T>,
    selectedItem: T?,
    sortMode: SortMode,
    alphabeticItemComparator: Comparator<T>,
    filterPlaceholderText: String,
    onFilterChange: (String) -> Unit,
    onReindexItems: () -> Unit,
) {
    val isManuallySortable = filter.isNullOrEmpty() && sortMode == SortMode.Manual
    val reorderableItems = remember {
        mutableStateListOf<T>().apply { addAll(items) }
    }

    // The parent holds the secret list with SnapshotFlow, and feeds it to this component.
    // We need to update our reorderableSecrets list when the parent updates, otherwise
    // we only get an empty secrets list to render.
    LaunchedEffect(items) {
        reorderableItems.clear()
        reorderableItems.addAll(items)
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderableItems.apply {
            add(to.index, removeAt(from.index))
        }
    }
    Column(modifier = Modifier.padding(padding)) {
        FilterTextField(
            filterEnabled = filter != null,
            filterQuery = filter ?: "",
            placeholderText = filterPlaceholderText,
            onFilterChange = onFilterChange,
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PADDING_S)
        ) {
            val filterQuery = filter ?: ""
            val filteredSecrets = when (filterQuery.isNotBlank()) {
                true -> {
                    val filterParts = filterQuery
                        .split(' ')
                        .filter { it.isNotBlank() }
                        .map { it.lowercase() }
                    reorderableItems.filter {
                        filterParts.all { f -> it.filterPredicate(f) }
                    }
                }

                false -> items
            }
            val sortedSecrets = when (sortMode) {
                SortMode.Manual -> filteredSecrets
                SortMode.Alphabetic -> filteredSecrets.sortedWith(alphabeticItemComparator)
            }
            items(
                items = if (isManuallySortable) reorderableItems else sortedSecrets,
                key = { it.key })
            { item ->
                ReorderableItem(reorderableLazyListState, key = item.key) { isDragging ->
                    val reorderableScope = this
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                    Surface(shadowElevation = elevation) {
                        ListRow(
                            item = item,
                            selected = item.key == selectedItem?.key,
                            dragHandle = {
                                DragHandle(
                                    scope = reorderableScope,
                                    showDragHandle = isManuallySortable,
                                    onDragStopped = {
                                        val itemSortOrder = computeSortOrder(item, reorderableItems)
                                        val shouldReindexAfterUpdate = shouldReindexSecrets(
                                            movedItemKey = item.key,
                                            movedItemSortOrder = itemSortOrder,
                                            secretList = reorderableItems,
                                        )

                                        item.onUpdateSortOrder(itemSortOrder)
                                        if (shouldReindexAfterUpdate) {
                                            onReindexItems()
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : ReorderableListItem> ListRow(
    item: T,
    selected: Boolean,
    dragHandle: @Composable () -> Unit,
) {
    val backgroundColor = when (selected) {
        true -> MaterialTheme.colorScheme.surfaceBright
        false -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PADDING_XS)
            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE))
            .background(backgroundColor)
            .combinedClickable(
                onClick = { item.onClick() },
                onClickLabel = item.onClickLabel,
                onLongClick = { item.onLongClick() },
                onLongClickLabel = item.onLongClickLabel,
            )
            .padding(PADDING_M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.apply { Render() }
        dragHandle()
    }
}

@Composable
private fun DragHandle(
    scope: ReorderableCollectionItemScope,
    showDragHandle: Boolean,
    onDragStopped: () -> Unit,
) {
    if (showDragHandle) {
        IconButton(
            modifier = with(scope) {
                Modifier
                    .draggableHandle(onDragStopped = onDragStopped)
                    .fillMaxHeight()
            },
            onClick = {}
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = appStrings.generic.dragToChangeOrder,
            )
        }
    }
}

private fun <T : ReorderableListItem> computeSortOrder(item: T, items: List<T>): Long {
    val secretIndex = items.indexOfFirst { it.key == item.key }
    val sortOrderOfPrev = items.getOrNull(secretIndex - 1)?.sortOrder ?: 0
    val sortOrderOfNext = items.getOrNull(secretIndex + 1)?.sortOrder ?: Long.MAX_VALUE
    return  sortOrderOfPrev / 2 + sortOrderOfNext / 2
}

private fun <T : ReorderableListItem> shouldReindexSecrets(
    movedItemKey: String,
    movedItemSortOrder: Long,
    secretList: List<T>,
): Boolean {
    val indexOfLastMovedSecret = secretList.indexOfFirst { it.key == movedItemKey }
    val sortOrderOfPrev = secretList.getOrNull(indexOfLastMovedSecret - 1)?.sortOrder ?: 0
    val sortOrderOfNext = secretList.getOrNull(indexOfLastMovedSecret + 1)?.sortOrder ?: Long.MAX_VALUE
    return movedItemSortOrder == sortOrderOfPrev + 1 || movedItemSortOrder == sortOrderOfNext - 1
}

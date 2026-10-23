package com.nexora.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.ListState

/**
 * One column: header text, relative width, the API sort field when the column is sortable (only fields the
 * endpoint lists as sortable, 06 section 3.5) and how to draw a cell.
 */
class TableColumn<T>(
    val title: String,
    val weight: Float = 1f,
    val sortField: String? = null,
    val numeric: Boolean = false,
    val cell: @Composable RowScope.(T) -> Unit,
)

/** A cell with plain text; numbers are right-aligned. */
fun <T> textColumn(title: String, weight: Float = 1f, sortField: String? = null, numeric: Boolean = false, text: (T) -> String) =
    TableColumn<T>(title, weight, sortField, numeric) { row ->
        Text(
            text(row),
            modifier = Modifier.weight(weight),
            textAlign = if (numeric) TextAlign.End else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

/**
 * Server-paged table (06 section 6): sortable headers, keyboard row navigation (arrows, Enter, PgUp/PgDn),
 * skeleton rows while loading, empty text, and the "Showing 21–40 of 312" footer with page buttons and size.
 */
@Composable
fun <T> DataTable(
    state: ListState<T>,
    columns: List<TableColumn<T>>,
    onOpen: ((T) -> Unit)?,
    onSort: (String) -> Unit,
    onPage: (Int) -> Unit,
    onSize: (Int) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    emptyAction: Pair<String, () -> Unit>? = null,
    rowsOverride: List<T>? = null,
) {
    val page = state.page
    val rows = rowsOverride ?: page?.content.orEmpty()
    var selected by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows) { selected = 0 }
    Column(modifier) {
        // Header row
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
            columns.forEach { column ->
                val sortMark = when (state.query.sort) {
                    "${column.sortField},asc" -> s("table.sortedAsc")
                    "${column.sortField},desc" -> s("table.sortedDesc")
                    else -> ""
                }
                val headerModifier = Modifier.weight(column.weight)
                if (column.sortField != null) {
                    TextButton(onClick = { onSort(column.sortField) }, modifier = headerModifier) {
                        Text(column.title + sortMark, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                } else {
                    Text(
                        column.title,
                        modifier = headerModifier.padding(vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = if (column.numeric) TextAlign.End else TextAlign.Start,
                        maxLines = 2,
                    )
                }
            }
        }
        when {
            state.loading && page == null -> repeat(5) { SkeletonRow() }
            rows.isEmpty() -> {
                val filtered = state.query.filters.isNotEmpty() || state.query.search.isNotBlank()
                EmptyState(if (filtered) s("table.noResults") else emptyMessage, emptyAction?.first, emptyAction?.second)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 2000.dp).testTag("table")
                    .onFocusChanged { focused = it.hasFocus }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(rows.lastIndex); true }
                            Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                            Key.Enter -> { rows.getOrNull(selected)?.let { onOpen?.invoke(it) }; true }
                            Key.PageDown -> { page?.let { onPage(it.page + 1) }; true }
                            Key.PageUp -> { page?.let { onPage(it.page - 1) }; true }
                            else -> false
                        }
                    }
                    // 2 px focus outline in the accent colour (NFR-USE-06).
                    .border(if (focused) 2.dp else 0.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent),
            ) {
                itemsIndexed(rows) { index, row ->
                    val highlight = focused && index == selected
                    val rowBg = when {
                        highlight -> MaterialTheme.colorScheme.primaryContainer
                        index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else -> Color.Transparent
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            .background(rowBg)
                            .let { if (onOpen != null) it.clickable { selected = index; onOpen(row) } else it }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        columns.forEach { column -> column.cell(this, row) }
                    }
                    HorizontalDivider()
                }
            }
        }
        page?.let { Footer(it, onPage, onSize) }
        if (state.error != null && state.lastUpdated != null) {
            // Last good data stays visible with its time (06 section 3.4).
            Text(s("common.lastUpdated", Dates.formatTime(state.lastUpdated)), color = Color(0xFF5A5F69), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SkeletonRow() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .semantics { contentDescription = s("common.loading") },
    )
}

@Composable
private fun <T> Footer(page: PageResponse<T>, onPage: (Int) -> Unit, onSize: (Int) -> Unit) {
    val first = if (page.totalElements == 0L) 0 else page.page.toLong() * page.size + 1
    val last = minOf(page.totalElements, first + page.content.size - 1)
    // FlowRow wraps the buttons onto a second line when the text is large (200 % text size).
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s("table.showing", first, maxOf(last, 0), page.totalElements), Modifier.padding(end = 16.dp))
        Dropdown(
            s("table.pageSize"),
            listOf(20, 50, 100).map { it to it.toString() },
            page.size,
            onSize,
            modifier = Modifier.padding(end = 8.dp),
        )
        TextButton(onClick = { onPage(0) }, enabled = page.page > 0) { Text(s("table.first")) }
        TextButton(onClick = { onPage(page.page - 1) }, enabled = page.page > 0) { Text(s("table.previous")) }
        TextButton(onClick = { onPage(page.page + 1) }, enabled = page.page + 1 < page.totalPages) { Text(s("table.next")) }
        TextButton(onClick = { onPage(page.totalPages - 1) }, enabled = page.page + 1 < page.totalPages) { Text(s("table.last")) }
    }
}

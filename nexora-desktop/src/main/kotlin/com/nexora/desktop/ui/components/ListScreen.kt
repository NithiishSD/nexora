package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.util.FileDialogs
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import kotlinx.coroutines.launch

/**
 * Layout shared by every list screen (06 section 3.5): title with "New" and "Export CSV", search box (Ctrl+F
 * focuses it, 300 ms debounce in the view-model), a filter row, the error banner and the paged table.
 */
@Composable
fun <T> ListScreen(
    title: String,
    vm: PagedListViewModel<T>,
    columns: List<TableColumn<T>>,
    emptyMessage: String,
    reloadTick: Int,
    searchTick: Int,
    onOpen: ((T) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    searchable: Boolean = false,
    newLabel: String? = null,
    onNew: (() -> Unit)? = null,
    csvName: String? = null,
    exportCsv: (suspend () -> ApiResult<ByteArray>)? = null,
    extraActions: @Composable () -> Unit = {},
    filters: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var exportError by remember { androidx.compose.runtime.mutableStateOf<com.nexora.desktop.api.ApiError?>(null) }
    val searchFocus = remember { FocusRequester() }
    ReloadOn(reloadTick, vm::load)
    LaunchedEffect(searchTick) { if (searchTick > 0 && searchable) searchFocus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack) {
            extraActions()
            if (exportCsv != null && csvName != null) {
                OutlinedButton(onClick = {
                    scope.launch {
                        // Same filters as the table; the server builds the file (format=csv, 06 section 3.7).
                        when (val result = exportCsv()) {
                            is ApiResult.Success -> FileDialogs.save(s("common.exportCsv"), csvName, result.value)
                            is ApiResult.Failure -> exportError = result.error
                        }
                    }
                }) { Text(s("common.exportCsv")) }
            }
            if (newLabel != null && onNew != null) Button(onClick = onNew) { Text(newLabel) }
        }
        header()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            if (searchable) {
                OutlinedTextField(
                    value = state.query.search,
                    onValueChange = { vm.onSearch(it.take(100)) },
                    label = { Text(s("common.searchHint")) },
                    singleLine = true,
                    modifier = Modifier.width(320.dp).focusRequester(searchFocus).testTag("list.search"),
                )
            }
            filters()
            if (state.query.filters.isNotEmpty()) OutlinedButton(onClick = vm::clearFilters) { Text(s("table.clearFilters")) }
        }
        exportError?.let { ErrorBanner(it, onDismiss = { exportError = null }) }
        state.error?.let { ErrorBanner(it, onRetry = vm::load) }
        DataTable(
            state, columns, onOpen,
            onSort = vm::sortBy, onPage = vm::goToPage, onSize = vm::setSize,
            emptyMessage = emptyMessage,
            emptyAction = if (newLabel != null && onNew != null) newLabel to onNew else null,
        )
    }
}

/** A filter as a dropdown whose first entry ("All") removes it. */
@Composable
fun FilterDropdown(label: String, options: List<Pair<String, String>>, current: String?, onChange: (String?) -> Unit) {
    Dropdown(label, listOf("" to s("common.all")) + options, current ?: "", { onChange(it.ifEmpty { null }) }, Modifier.width(220.dp))
}

/** From / to date filters (inclusive business dates); typed dates are sent as ISO `yyyy-MM-dd`. */
@Composable
fun DateRangeFilter(current: Map<String, String>, onChange: (String, String?) -> Unit, fromKey: String = "from", toKey: String = "to") {
    val clock: java.time.Clock = org.koin.compose.koinInject()
    listOf(fromKey to s("filter.from"), toKey to s("filter.to")).forEach { (key, label) ->
        var text by remember(current[key]) { androidx.compose.runtime.mutableStateOf(com.nexora.desktop.util.Dates.formatIso(current[key]).takeIf { current[key] != null }.orEmpty()) }
        DateField(label, text, { typed ->
            text = typed
            val iso = com.nexora.desktop.util.Dates.toIso(typed, clock)
            if (typed.isBlank()) onChange(key, null) else if (iso != null) onChange(key, iso)
        }, Modifier.width(180.dp))
    }
}

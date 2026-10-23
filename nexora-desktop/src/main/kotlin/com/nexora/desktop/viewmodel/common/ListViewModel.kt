package com.nexora.desktop.viewmodel.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.repository.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** Paging, sorting, search and filters of a list screen (06 section 3.5), sent as query parameters. */
data class ListQuery(
    val page: Int = 0,
    val size: Int = 20,
    val sort: String? = null,
    val search: String = "",
    val filters: Map<String, String> = emptyMap(),
) {
    fun params(): Map<String, Any?> =
        filters + mapOf("page" to page, "size" to size, "sort" to sort, "search" to search.trim().takeIf { it.length >= MIN_SEARCH })

    companion object {
        const val MIN_SEARCH = 2
    }
}

data class ListState<T>(
    val query: ListQuery = ListQuery(),
    val loading: Boolean = false,
    val page: PageResponse<T>? = null,
    val error: ApiError? = null,
    /** Time of the last successful load; shown as "Last updated hh:mm" when a reload fails (06 section 3.4). */
    val lastUpdated: Instant? = null,
)

/**
 * A server-paged list. Rows, totals and counts are shown exactly as the API returns them.
 * - Search waits 300 ms after the last keystroke and needs at least 2 characters.
 * - A newer load cancels the one still running, so an old answer can never overwrite a newer one.
 * - A failed reload keeps the rows already on screen and only adds the error.
 */
open class PagedListViewModel<T>(
    initial: ListQuery,
    private val clock: Clock,
    private val fetch: suspend (ListQuery) -> ApiResult<PageResponse<T>>,
) : ViewModel() {

    private val _state = MutableStateFlow(ListState<T>(query = initial))
    val state: StateFlow<ListState<T>> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true) }
        val query = _state.value.query
        loadJob = viewModelScope.launch {
            when (val result = fetch(query)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, page = result.value, error = null, lastUpdated = clock.instant())
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun onSearch(text: String) {
        _state.update { it.copy(query = it.query.copy(search = text, page = 0)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length >= ListQuery.MIN_SEARCH) load()
        }
    }

    /** Sets (or with null removes) a filter and reloads from the first page. */
    fun setFilter(name: String, value: String?) {
        _state.update {
            val filters = if (value.isNullOrEmpty()) it.query.filters - name else it.query.filters + (name to value)
            it.copy(query = it.query.copy(filters = filters, page = 0))
        }
        load()
    }

    fun clearFilters() {
        _state.update { it.copy(query = it.query.copy(filters = emptyMap(), search = "", page = 0)) }
        load()
    }

    /** Click on a sortable header: first ascending, again descending. */
    fun sortBy(field: String) {
        _state.update {
            val next = if (it.query.sort == "$field,asc") "$field,desc" else "$field,asc"
            it.copy(query = it.query.copy(sort = next, page = 0))
        }
        load()
    }

    fun goToPage(page: Int) {
        val last = (_state.value.page?.totalPages ?: 1) - 1
        _state.update { it.copy(query = it.query.copy(page = page.coerceIn(0, maxOf(0, last)))) }
        load()
    }

    /** 20 / 50 / 100 rows per page (the API caps at 100). */
    fun setSize(size: Int) {
        _state.update { it.copy(query = it.query.copy(size = size.coerceIn(1, 100), page = 0)) }
        load()
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

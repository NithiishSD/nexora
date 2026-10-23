package com.nexora.desktop.viewmodel.expenses

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.dto.ExpenseResponse
import com.nexora.desktop.api.dto.ExpenseSummaryResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ExpenseRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.SubmitController
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * S-EXP-01 Expenses (FR-FIN-12, -13, -14): the list plus the server's summary for the selected period, where
 * business expenses and personal drawings are two separate totals - they are never added together here.
 */
class ExpensesViewModel(private val repository: ExpenseRepository, private val clock: Clock) :
    PagedListViewModel<ExpenseResponse>(
        // Current month by default (06 section 11 #12).
        ListQuery(filters = mapOf("from" to Dates.monthStart(clock).toString(), "to" to Dates.today(clock).toString())),
        clock,
        { repository.list(it) },
    ) {

    private val _summary = MutableStateFlow<ExpenseSummaryResponse?>(null)
    val summary: StateFlow<ExpenseSummaryResponse?> = _summary.asStateFlow()

    private val _reverseError = MutableStateFlow<ApiError?>(null)
    val reverseError: StateFlow<ApiError?> = _reverseError.asStateFlow()

    private val reverser = SubmitController()

    init {
        loadSummary()
    }

    /** Summary for the same from/to as the list. */
    fun loadSummary() {
        val filters = state.value.query.filters
        val from = filters["from"] ?: Dates.monthStart(clock).toString()
        val to = filters["to"] ?: Dates.today(clock).toString()
        viewModelScope.launch { _summary.value = repository.summary(from, to).getOrNull() }
    }

    suspend fun csv() = repository.csv(state.value.query)

    fun reload() {
        load()
        loadSummary()
    }

    /** Reverse a recorded expense (compensating transaction); a reason is required. */
    fun reverse(id: String, reason: String) {
        viewModelScope.launch {
            when (val result = reverser.submit { key -> repository.reverse(id, reason.trim(), key) }) {
                is ApiResult.Success -> { _reverseError.value = null; reload() }
                is ApiResult.Failure -> _reverseError.value = result.error
                null -> Unit
            }
        }
    }
}

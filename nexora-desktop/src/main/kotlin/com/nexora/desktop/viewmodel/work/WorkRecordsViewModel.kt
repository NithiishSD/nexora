package com.nexora.desktop.viewmodel.work

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.dto.WorkRecordResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.WorkRepository
import com.nexora.desktop.repository.map
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock

/**
 * S-WRK-01 Work records (FR-WORK-04, -05, -06). Defaults to the current payroll week (Monday-Sunday); the total is
 * the server's `totalAmount` over all matching rows, not a sum of the rows on screen.
 */
class WorkRecordsViewModel private constructor(
    private val repository: WorkRepository,
    clock: Clock,
    filters: Map<String, String>,
    private val totalHolder: MutableStateFlow<BigDecimal?>,
) : PagedListViewModel<WorkRecordResponse>(
    ListQuery(filters = mapOf("from" to Dates.weekStart(clock).toString(), "to" to Dates.weekEnd(clock).toString()) + filters),
    clock,
    { q -> repository.list(q).map { totalHolder.value = it.totalAmount; it.page } },
) {
    constructor(repository: WorkRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
        this(repository, clock, filters, MutableStateFlow(null))

    val totalAmount: StateFlow<BigDecimal?> = totalHolder

    private val _reverseError = MutableStateFlow<ApiError?>(null)
    val reverseError: StateFlow<ApiError?> = _reverseError

    /** Reversal is allowed only for a recorded entry without payments (the server checks the allocation). */
    fun canReverse(r: WorkRecordResponse) = r.status == "RECORDED" && (r.allocatedAmount?.signum() ?: 0) == 0

    fun reverse(id: String, reason: String) {
        viewModelScope.launch {
            when (val result = repository.reverse(id, reason.trim())) {
                is ApiResult.Success -> { _reverseError.value = null; load() }
                is ApiResult.Failure -> _reverseError.value = result.error
            }
        }
    }
}

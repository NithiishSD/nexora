package com.nexora.desktop.viewmodel.orders

import com.nexora.desktop.api.dto.OrderSummaryResponse
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/** Quick filter chips of S-ORD-01: Pending (default), Late, All. */
enum class OrderQuickFilter { PENDING, LATE, ALL }

/** S-ORD-01 Order list (FR-ORD-05, -14, -16). Pending and late are decided by the server (`pending=`, `late=`). */
class OrderListViewModel(private val repository: OrderRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<OrderSummaryResponse>(
        // From a customer's Activity tab all orders are shown; from the sidebar only pending ones.
        ListQuery(filters = if (filters.isEmpty()) mapOf("pending" to "true") else filters),
        clock,
        { repository.list(it) },
    ) {

    fun quickFilter(filter: OrderQuickFilter) {
        setFilter("pending", if (filter == OrderQuickFilter.PENDING) "true" else null)
        setFilter("late", if (filter == OrderQuickFilter.LATE) "true" else null)
    }

    fun currentQuickFilter(): OrderQuickFilter = when {
        state.value.query.filters["pending"] == "true" -> OrderQuickFilter.PENDING
        state.value.query.filters["late"] == "true" -> OrderQuickFilter.LATE
        else -> OrderQuickFilter.ALL
    }

    suspend fun csv() = repository.csv(state.value.query)
}

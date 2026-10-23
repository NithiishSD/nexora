package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.api.dto.PurchaseSummaryResponse
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/** S-PUR-01 Purchase list (FR-PROC-07, -16). */
class PurchaseListViewModel(private val repository: PurchaseRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<PurchaseSummaryResponse>(ListQuery(filters = filters), clock, { repository.list(it) }) {

    suspend fun csv() = repository.csv(state.value.query)
}

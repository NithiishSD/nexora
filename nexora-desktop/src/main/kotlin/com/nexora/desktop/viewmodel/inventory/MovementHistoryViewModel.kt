package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.api.dto.ExternalWipResponse
import com.nexora.desktop.api.dto.MovementResponse
import com.nexora.desktop.api.dto.StockSummaryResponse
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/**
 * S-INV-02 Movement history (FR-INV-08), read-only: this view-model has no way to create, edit or delete a
 * movement (FR-INV-01); filters only.
 */
class MovementHistoryViewModel(private val repository: InventoryRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<MovementResponse>(ListQuery(filters = filters), clock, { repository.movements(it) }) {

    suspend fun csv() = repository.movementsCsv(state.value.query)
}

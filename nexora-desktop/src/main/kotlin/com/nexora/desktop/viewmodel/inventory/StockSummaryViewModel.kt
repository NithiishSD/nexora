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
 * S-INV-01 Stock summary (FR-INV-07, -16): tiles, balances and wastage exactly as the server returns them;
 * nothing is added up on the client (NFR-USE-02).
 */
class StockSummaryViewModel(repository: InventoryRepository, clock: Clock) :
    DetailViewModel<StockSummaryResponse>(clock, { repository.summary() })

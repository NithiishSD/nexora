package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.api.dto.ExternalWipResponse
import com.nexora.desktop.api.dto.MovementResponse
import com.nexora.desktop.api.dto.StockSummaryResponse
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/** S-INV-03 Material with manufacturers (FR-INV-14). */
class ExternalWipViewModel(repository: InventoryRepository, clock: Clock) :
    DetailViewModel<ExternalWipResponse>(clock, { repository.externalWip() })

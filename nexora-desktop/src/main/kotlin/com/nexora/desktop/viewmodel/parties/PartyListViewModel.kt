package com.nexora.desktop.viewmodel.parties

import com.nexora.desktop.api.dto.PartySummaryResponse
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/**
 * S-PTY-01 Party list: role and status filters (Active by default) and search by name, code or phone
 * (FR-PARTY-07). Search is debounced 300 ms and stale requests are cancelled by the base class.
 */
class PartyListViewModel(repository: PartyRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<PartySummaryResponse>(
        ListQuery(filters = mapOf("status" to "ACTIVE") + filters),
        clock,
        { query -> repository.list(query) },
    )

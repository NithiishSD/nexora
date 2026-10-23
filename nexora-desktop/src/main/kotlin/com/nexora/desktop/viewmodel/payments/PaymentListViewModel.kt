package com.nexora.desktop.viewmodel.payments

import com.nexora.desktop.api.dto.PaymentSummaryResponse
import com.nexora.desktop.repository.PaymentRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/** S-PAY-01 Payment list (FR-PAY-14). */
class PaymentListViewModel(private val repository: PaymentRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<PaymentSummaryResponse>(ListQuery(filters = filters), clock, { repository.list(it) }) {

    suspend fun csv() = repository.csv(state.value.query)
}

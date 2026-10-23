package com.nexora.desktop.viewmodel.money

import com.nexora.desktop.api.dto.TransactionResponse
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import java.time.Clock

/**
 * S-FIN-03 Account transactions with the server's running balance ([accountId] set) and S-FIN-04 All transactions.
 * Read-only: no transaction is ever created from these screens (FR-FIN-04).
 */
class TransactionsViewModel(
    private val repository: FinanceRepository,
    clock: Clock,
    private val accountId: String?,
    filters: Map<String, String> = emptyMap(),
) :
    PagedListViewModel<TransactionResponse>(
        ListQuery(filters = filters),
        clock,
        { q -> if (accountId != null) repository.accountTransactions(accountId, q) else repository.transactions(q) },
    ) {

    suspend fun csv() = if (accountId != null) repository.accountTransactionsCsv(accountId, state.value.query) else repository.transactionsCsv(state.value.query)
}

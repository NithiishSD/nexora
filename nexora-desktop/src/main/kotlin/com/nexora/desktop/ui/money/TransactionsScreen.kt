package com.nexora.desktop.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.TransactionResponse
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.money.TransactionsViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel

private val TRANSACTION_TYPES = listOf(
    "OPENING_BALANCE", "CUSTOMER_PAYMENT", "SUPPLIER_PAYMENT", "MANUFACTURER_PAYMENT", "WORKER_PAYMENT", "EXPENSE", "PERSONAL_DRAWING",
    "LOAN_RECEIVED", "LOAN_PRINCIPAL_REPAYMENT", "LOAN_INTEREST_PAYMENT", "CHIT_CONTRIBUTION", "CHIT_PAYOUT", "CHIT_ORGANISER_PAYMENT",
    "TRANSFER_OUT", "TRANSFER_IN", "ADJUSTMENT", "REVERSAL",
)

/**
 * S-FIN-03 Account transactions (with [accountId]: oldest first, server running balance) and S-FIN-04 All
 * transactions. Read-only: there is no create button (FR-FIN-04).
 */
@Composable
fun TransactionsScreen(nav: NavigationViewModel, accountId: String?, accountName: String?, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: TransactionsViewModel = screenViewModel(accountId, filters)
    val state by vm.state.collectAsState()
    val columns = buildList<TableColumn<TransactionResponse>> {
        add(textColumn(s("common.date"), 1f, if (accountId == null) "transactionDate" else null) { Dates.formatIso(it.transactionDate) })
        if (accountId == null) add(textColumn(s("payment.account"), 1.2f) { it.account.accountName })
        add(textColumn(s("money.type"), 1.5f) { enumLabel("TransactionType", it.transactionType) })
        add(textColumn(s("money.direction"), 0.7f) { enumLabel("LedgerDirection", it.direction) })
        add(textColumn(s("common.amount"), 1.2f, if (accountId == null) "amount" else null, numeric = true) { money(it.amount) })
        if (accountId != null) add(textColumn(s("money.runningBalance"), 1.2f, numeric = true) { money(it.runningBalance) })
        add(textColumn(s("inventory.reference"), 1.2f) { it.referenceNumber.orEmpty() })
        add(textColumn(s("money.description"), 2f) { it.description.orEmpty() })
    }
    ListScreen(
        title = accountName?.let { s("money.accountTransactions", it) } ?: s("money.allTransactions"),
        vm = vm,
        columns = columns,
        emptyMessage = s("money.noTransactions"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onBack = nav::back,
        csvName = "transactions.csv",
        exportCsv = vm::csv,
        filters = {
            FilterDropdown(s("money.type"), TRANSACTION_TYPES.map { it to enumLabel("TransactionType", it) }, state.query.filters["type"]) {
                vm.setFilter("type", it)
            }
            if (accountId == null) {
                FilterDropdown(s("money.direction"), listOf("IN", "OUT").map { it to enumLabel("LedgerDirection", it) }, state.query.filters["direction"]) {
                    vm.setFilter("direction", it)
                }
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

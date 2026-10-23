package com.nexora.desktop.ui.payments

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.api.dto.PaymentSummaryResponse
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.payments.PaymentListViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-PAY-01 Payment list (FR-PAY-14). */
@Composable
fun PaymentListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: PaymentListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.payments"),
        vm = vm,
        columns = listOf(
            textColumn<PaymentSummaryResponse>(s("common.number"), 1.2f, "paymentNumber") { it.paymentNumber },
            textColumn(s("common.date"), 1f, "paymentDate") { Dates.formatIso(it.paymentDate) },
            textColumn(s("payment.party"), 1.5f) { it.party.name },
            textColumn(s("payment.direction"), 1f) { enumLabel("PaymentDirection", it.direction) },
            textColumn(s("payment.method"), 1f) { enumLabel("PaymentMethod", it.paymentMethod) },
            textColumn(s("payment.account"), 1.2f) { it.account.accountName },
            textColumn(s("payment.amount"), 1.2f, "amount", numeric = true) { money(it.amount) },
            textColumn(s("payment.unallocated"), 1.2f, numeric = true) { money(it.unallocatedAmount) },
            TableColumn(s("common.status"), 1f) { p -> Row(Modifier.weight(1f)) { StatusChip("RecordStatus", p.status) } },
        ),
        emptyMessage = s("payment.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.PaymentDetail(it.id)) },
        onBack = nav.backIfNested(),
        newLabel = if (nav.can("PAYMENT_CREATE")) s("payment.record") else null,
        onNew = { nav.show(Overlay.RecordPayment()) },
        csvName = "payments.csv",
        exportCsv = vm::csv,
        extraActions = { OutlinedButton(onClick = { nav.push(Screen.SupplierPayables) }) { Text(s("payables.title")) } },
        filters = {
            FilterDropdown(s("payment.direction"), listOf("RECEIVED", "PAID").map { it to enumLabel("PaymentDirection", it) }, state.query.filters["direction"]) {
                vm.setFilter("direction", it)
            }
            FilterDropdown(s("payment.method"), AccountRules.METHODS.map { it to enumLabel("PaymentMethod", it) }, state.query.filters["method"]) {
                vm.setFilter("method", it)
            }
            FilterDropdown(s("common.status"), listOf("RECORDED", "REVERSED", "PENDING", "BOUNCED").map { it to enumLabel("RecordStatus", it) }, state.query.filters["status"]) {
                vm.setFilter("status", it)
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

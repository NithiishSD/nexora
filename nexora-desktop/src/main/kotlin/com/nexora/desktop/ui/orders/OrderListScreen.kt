package com.nexora.desktop.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.OrderSummaryResponse
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.FlagChip
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.orders.OrderListViewModel
import com.nexora.desktop.viewmodel.orders.OrderQuickFilter
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

val ORDER_STATUSES = listOf("PLACED", "CONFIRMED", "IN_PROGRESS", "READY", "DELIVERED", "COMPLETED", "CANCELLED")

/** S-ORD-01 Order list with the Pending / Late / All quick filters. */
@Composable
fun OrderListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: OrderListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.orders"),
        vm = vm,
        columns = listOf(
            textColumn<OrderSummaryResponse>(s("common.number"), 1.2f, "orderNumber") { it.orderNumber },
            textColumn(s("common.date"), 1f, "orderDate") { Dates.formatIso(it.orderDate) },
            textColumn(s("order.customer"), 1.6f) { it.customer.name },
            textColumn(s("order.items"), 0.7f, numeric = true) { it.itemCount.toString() },
            textColumn(s("order.total"), 1.2f, numeric = true) { money(it.totalAmount) },
            textColumn(s("common.outstanding"), 1.2f, numeric = true) { money(it.outstandingAmount) },
            textColumn(s("order.expected"), 1f, "requiredDate") { Dates.formatIso(it.requiredDate) },
            TableColumn(s("common.status"), 2.2f) { o ->
                Row(Modifier.weight(2.2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip("OrderStatus", o.status)
                    StatusChip("PaymentStatusView", o.paymentStatus)
                    if (o.late) FlagChip(s("flag.late"))
                }
            },
        ),
        emptyMessage = s("order.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.OrderDetail(it.id)) },
        onBack = nav.backIfNested(),
        newLabel = if (nav.can("ORDER_MANAGE")) s("order.new") else null,
        onNew = { nav.show(Overlay.OrderForm()) },
        csvName = "orders.csv",
        exportCsv = vm::csv,
        filters = {
            val current = vm.currentQuickFilter()
            OrderQuickFilter.entries.forEach { quick ->
                FilterChip(selected = current == quick, onClick = { vm.quickFilter(quick) }, label = { Text(s("order.quick.${quick.name}")) })
            }
            FilterDropdown(s("common.status"), ORDER_STATUSES.map { it to enumLabel("OrderStatus", it) }, state.query.filters["status"]) {
                vm.setFilter("status", it)
            }
            FilterDropdown(s("order.payment"), listOf("UNPAID", "PARTIALLY_PAID", "PAID").map { it to enumLabel("PaymentStatusView", it) }, state.query.filters["paymentStatus"]) {
                vm.setFilter("paymentStatus", it)
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

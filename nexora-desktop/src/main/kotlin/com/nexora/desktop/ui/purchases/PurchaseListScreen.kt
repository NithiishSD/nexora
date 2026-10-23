package com.nexora.desktop.ui.purchases

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PurchaseSummaryResponse
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
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.purchases.PurchaseListViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-PUR-01 Purchase list (FR-PROC-07, -16): every figure is the server's summary. */
@Composable
fun PurchaseListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: PurchaseListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.purchases"),
        vm = vm,
        columns = listOf(
            textColumn<PurchaseSummaryResponse>(s("common.number"), 1.2f, "purchaseNumber") { it.purchaseNumber },
            textColumn(s("common.date"), 1f, "purchaseDate") { Dates.formatIso(it.purchaseDate) },
            textColumn(s("purchase.supplier"), 1.5f) { it.supplier.name },
            textColumn(s("purchase.product"), 1f) { enumLabel("ProductType", it.productType) },
            textColumn(s("purchase.orderedKg"), 1f, numeric = true) { weight(it.orderedWeightKg) },
            textColumn(s("purchase.receivedKg"), 1f, numeric = true) { weight(it.receivedWeightKg) },
            textColumn(s("purchase.value"), 1.2f, numeric = true) { money(it.orderedValue) },
            textColumn(s("common.outstanding"), 1.2f, numeric = true) { money(it.outstandingAmount) },
            textColumn(s("purchase.dueDate"), 1f, "dueDate") { Dates.formatIso(it.dueDate) },
            TableColumn(s("common.status"), 1.6f) { p ->
                Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip("PurchaseStatus", p.status)
                    if (p.overdue) FlagChip(s("flag.overdueShort"))
                }
            },
        ),
        emptyMessage = s("purchase.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.PurchaseDetail(it.id)) },
        onBack = nav.backIfNested(),
        newLabel = if (nav.can("PURCHASE_CREATE")) s("purchase.new") else null,
        onNew = { nav.show(Overlay.NewPurchase) },
        csvName = "purchases.csv",
        exportCsv = vm::csv,
        filters = {
            FilterDropdown(
                s("common.status"),
                listOf("ORDERED", "PARTIALLY_RECEIVED", "FULLY_RECEIVED", "CANCELLED").map { it to enumLabel("PurchaseStatus", it) },
                state.query.filters["status"],
            ) { vm.setFilter("status", it) }
            FilterDropdown(
                s("purchase.product"),
                listOf("RAW_SILK", "VUDA_WARP").map { it to enumLabel("ProductType", it) },
                state.query.filters["productType"],
            ) { vm.setFilter("productType", it) }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

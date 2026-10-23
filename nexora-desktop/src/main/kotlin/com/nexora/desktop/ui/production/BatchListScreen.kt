package com.nexora.desktop.ui.production

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.production.BatchListViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-PRD-01 Production batches. */
@Composable
fun BatchListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: BatchListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.production"),
        vm = vm,
        columns = listOf(
            textColumn<BatchResponse>(s("common.number"), 1.1f, "batchNumber") { it.batchNumber },
            textColumn(s("common.date"), 1f, "productionDate") { Dates.formatIso(it.productionDate) },
            textColumn(s("batch.warpType"), 1f) { enumLabel("ProductType", it.productType) },
            textColumn(s("batch.inputShort"), 1f, numeric = true) { weight(it.inputWeightKg) },
            textColumn(s("batch.output"), 1f, numeric = true) { weight(it.outputWeightKg) },
            textColumn(s("batch.wastage"), 1f, numeric = true) { weight(it.wastageWeightKg) },
            textColumn(s("batch.discrepancy"), 1f, numeric = true) { weight(it.discrepancyWeightKg) },
            textColumn(s("batch.orders"), 1.3f) { b -> b.orderItems.orEmpty().mapNotNull { it.orderNumber }.distinct().joinToString() },
            TableColumn(s("common.status"), 1.1f) { b -> Row(Modifier.weight(1.1f)) { StatusChip("BatchStatus", b.status) } },
        ),
        emptyMessage = s("batch.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.BatchDetail(it.id)) },
        onBack = nav.backIfNested(),
        newLabel = if (nav.can("PRODUCTION_MANAGE")) s("batch.new") else null,
        onNew = { nav.show(Overlay.BatchForm()) },
        filters = {
            FilterDropdown(s("common.status"), listOf("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED").map { it to enumLabel("BatchStatus", it) }, state.query.filters["status"]) {
                vm.setFilter("status", it)
            }
            FilterDropdown(s("batch.warpType"), WARP_TYPES.map { it to enumLabel("ProductType", it) }, state.query.filters["productType"]) {
                vm.setFilter("productType", it)
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

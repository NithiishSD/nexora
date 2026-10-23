package com.nexora.desktop.ui.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.MovementResponse
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.inventory.MovementHistoryViewModel
import com.nexora.desktop.viewmodel.inventory.StockAdjustmentViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

private val MOVEMENT_TYPES = listOf(
    "OPENING_BALANCE", "RECEIPT", "PURCHASE_RETURN", "DIRECT_SALE", "CONSUMPTION", "PRODUCTION_OUTPUT", "WASTAGE",
    "OUTSOURCE_ISSUE", "OUTSOURCE_RECEIPT", "DELIVERY", "CUSTOMER_RETURN", "ADJUSTMENT",
)

/** S-INV-02 Movement history: read-only, there is no create button (FR-INV-01). */
@Composable
fun MovementHistoryScreen(nav: NavigationViewModel, screen: Screen.Movements, reloadTick: Int, searchTick: Int) {
    val initial = listOfNotNull(screen.productType?.let { "productType" to it }, screen.location?.let { "location" to it }).toMap()
    val vm: MovementHistoryViewModel = screenViewModel(initial)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("inventory.movements"),
        vm = vm,
        columns = listOf(
            textColumn<MovementResponse>(s("common.number"), 1.2f, "movementNumber") { it.movementNumber },
            textColumn(s("common.date"), 1f, "movementDate") { Dates.formatIso(it.movementDate) },
            textColumn(s("inventory.type"), 1.3f) { enumLabel("MovementType", it.movementType) },
            textColumn(s("purchase.product"), 1.3f) { m ->
                enumLabel("ProductType", m.productType) +
                    (m.toProductType?.takeIf { it != m.productType }?.let { " " + s("inventory.toProduct", enumLabel("ProductType", it)) } ?: "")
            },
            textColumn(s("inventory.from"), 1f) { enumLabel("StockLocation", it.fromLocation) },
            textColumn(s("inventory.to"), 1f) { enumLabel("StockLocation", it.toLocation) },
            textColumn(s("inventory.quantity"), 1f, numeric = true) { weight(it.quantityKg) },
            textColumn(s("inventory.reference"), 1.2f) { it.referenceNumber.orEmpty() },
            textColumn(s("common.notes"), 1.2f) { listOfNotNull(it.reason, it.notes).joinToString(" · ") },
        ),
        emptyMessage = s("inventory.noMovements"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { m -> openReference(nav, m) },
        onBack = nav::back,
        csvName = "movements.csv",
        exportCsv = vm::csv,
        filters = {
            FilterDropdown(s("purchase.product"), listOf("RAW_SILK", "SILK_WARP", "KORA_WARP", "VUDA_WARP").map { it to enumLabel("ProductType", it) }, state.query.filters["productType"]) {
                vm.setFilter("productType", it)
            }
            FilterDropdown(s("inventory.location"), StockAdjustmentViewModel.ALL_LOCATIONS.map { it to enumLabel("StockLocation", it) }, state.query.filters["location"]) {
                vm.setFilter("location", it)
            }
            FilterDropdown(s("inventory.type"), MOVEMENT_TYPES.map { it to enumLabel("MovementType", it) }, state.query.filters["movementType"]) {
                vm.setFilter("movementType", it)
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

/** Opens the purchase, batch or job a movement belongs to (06 S-INV-02 Reference column). */
private fun openReference(nav: NavigationViewModel, m: MovementResponse) {
    val id = m.referenceId ?: return
    when (m.referenceType) {
        "PURCHASE" -> nav.push(Screen.PurchaseDetail(id))
        "PRODUCTION_BATCH" -> nav.push(Screen.BatchDetail(id))
        "OUTSOURCING_JOB" -> nav.push(Screen.JobDetail(id))
    }
}

package com.nexora.desktop.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.FlagChip
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.inventory.StockSummaryViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

/** S-INV-01 Stock summary: tiles, product x location balances, cumulative wastage. */
@Composable
fun StockSummaryScreen(nav: NavigationViewModel, reloadTick: Int) {
    val vm: StockSummaryViewModel = screenViewModel()
    val data by vm.data.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    ReloadOn(reloadTick, vm::reload)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(s("inventory.title")) {
            data.value?.asOf?.let { Text(s("dashboard.asOf", Dates.formatIsoTimestamp(it))) }
            OutlinedButton(onClick = { nav.push(Screen.Movements()) }) { Text(s("inventory.movements")) }
            OutlinedButton(onClick = { nav.push(Screen.ExternalWip) }) { Text(s("inventory.withManufacturers")) }
            if (nav.can("INVENTORY_ADJUST")) {
                OutlinedButton(onClick = { dialog = "thresholds" }) { Text(s("inventory.thresholds")) }
                OutlinedButton(onClick = { dialog = "wastage" }) { Text(s("inventory.recordWastage")) }
                OutlinedButton(onClick = { dialog = "adjust" }) { Text(s("inventory.adjust")) }
            }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        val d = data.value ?: return@Column
        val rawSilkLowStock = d.balances.firstOrNull { it.productType == "RAW_SILK" && it.location == "RAW_STOCK" }?.isLowStock == true
        val internalWipLowStock = d.balances.any { it.location == "INTERNAL_WIP" && it.isLowStock }
        val externalWipLowStock = d.balances.any { it.location == "EXTERNAL_WIP" && it.isLowStock }
        val finishedSilkLowStock = d.balances.firstOrNull { it.productType == "SILK_WARP" && it.location == "FINISHED_STOCK" }?.isLowStock == true
        val finishedKoraLowStock = d.balances.firstOrNull { it.productType == "KORA_WARP" && it.location == "FINISHED_STOCK" }?.isLowStock == true
        val vudaLowStock = d.balances.firstOrNull { it.productType == "VUDA_WARP" && it.location == "RAW_STOCK" }?.isLowStock == true

        FlowRow {
            FigureTile(
                s("dashboard.rawSilk"),
                weight(d.rawSilkAvailableKg),
                valueColour = if (rawSilkLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (rawSilkLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
            )
            FigureTile(
                s("dashboard.internalWip"),
                weight(d.internalWipKg),
                valueColour = if (internalWipLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (internalWipLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
            )
            FigureTile(
                s("dashboard.externalWip"),
                weight(d.externalWipKg),
                valueColour = if (externalWipLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (externalWipLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
                onClick = { nav.push(Screen.ExternalWip) },
            )
            FigureTile(
                s("dashboard.finishedSilk"),
                weight(d.finishedSilkWarpKg),
                valueColour = if (finishedSilkLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (finishedSilkLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
            )
            FigureTile(
                s("dashboard.finishedKora"),
                weight(d.finishedKoraWarpKg),
                valueColour = if (finishedKoraLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (finishedKoraLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
            )
            FigureTile(
                s("dashboard.vuda"),
                weight(d.vudaWarpKg),
                valueColour = if (vudaLowStock) MaterialTheme.colorScheme.error else null,
                badge = if (vudaLowStock) { { FlagChip(s("inventory.lowStock")) } } else null,
            )
        }
        Section(s("inventory.balances")) {
            StaticTable(
                listOf(s("purchase.product"), s("inventory.location"), s("inventory.quantity"), s("inventory.threshold")),
                d.balances.map {
                    listOf(
                        enumLabel("ProductType", it.productType),
                        enumLabel("StockLocation", it.location),
                        weight(it.quantityKg),
                        it.minThresholdKg?.let { t -> weight(t) } ?: "-",
                    )
                },
                numeric = setOf(2, 3),
                // A balance opens its movements, filtered by that product and location.
                onRowClick = { i -> d.balances[i].let { nav.push(Screen.Movements(it.productType, it.location)) } },
                trailing = { i ->
                    val b = d.balances[i]
                    if (b.isLowStock) {
                        FlagChip(s("inventory.lowStock"))
                    }
                },
            )
        }
        Section(s("inventory.wastageToDate")) {
            StaticTable(
                listOf(s("purchase.product"), s("inventory.quantity")),
                d.wastageToDateKg.map { listOf(enumLabel("ProductType", it.productType), weight(it.quantityKg)) },
                numeric = setOf(1),
            )
        }
    }
    when (dialog) {
        "thresholds" -> InventoryThresholdDialog(onDone = { dialog = null; vm.reload() }, onCancel = { dialog = null })
        "wastage" -> StockChangeDialog(wastage = true, onDone = { dialog = null; vm.reload() }, onCancel = { dialog = null })
        "adjust" -> StockChangeDialog(wastage = false, onDone = { dialog = null; vm.reload() }, onCancel = { dialog = null })
    }
}

package com.nexora.desktop.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.AllSupplierPayablesResponse
import com.nexora.desktop.api.dto.SupplierPayablesResponse
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.theme.AppColors
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Clock

/** S-PUR-06 view-model: `GET /payables/suppliers` as returned. */
class SupplierPayablesViewModel(repository: PurchaseRepository, clock: Clock) :
    DetailViewModel<AllSupplierPayablesResponse>(clock, { repository.supplierPayables() })

/** S-PUR-06 Supplier payables overview (FR-PAY-19); each supplier expands to its purchases. */
@Composable
fun SupplierPayablesScreen(nav: NavigationViewModel, reloadTick: Int) {
    val vm: SupplierPayablesViewModel = screenViewModel()
    val parties: PartyRepository = koinInject()
    val data by vm.data.collectAsState()
    val expanded = remember { mutableStateMapOf<String, SupplierPayablesResponse>() }
    val scope = rememberCoroutineScope()
    ReloadOn(reloadTick, vm::reload)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(s("payables.title"), onBack = nav.backIfNested())
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        val d = data.value ?: return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FigureTile(s("party.totalPayable"), money(d.totalPayable))
            FigureTile(s("party.totalOverdue"), money(d.totalOverdue), valueColour = AppColors.negative)
            FigureTile(s("party.dueWithin7"), money(d.dueWithin7Days))
        }
        d.suppliers.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    if (expanded.remove(row.supplier.id) == null) {
                        scope.launch { parties.supplierPayables(row.supplier.id).getOrNull()?.let { expanded[row.supplier.id] = it } }
                    }
                }) { Text(row.supplier.name) }
                Text(s("payables.outstanding", money(row.outstanding)), Modifier.weight(1f))
                Text(s("payables.overdue", money(row.overdue)), Modifier.weight(1f))
                Text(s("payables.oldestDue", Dates.formatIso(row.oldestDueDate)), Modifier.weight(1f))
                if (nav.can("PAYMENT_CREATE")) {
                    OutlinedButton(onClick = { nav.show(Overlay.RecordPayment(row.supplier.id, "SUPPLIER")) }) { Text(s("payables.pay")) }
                }
            }
            expanded[row.supplier.id]?.let { detail ->
                StaticTable(
                    listOf(s("purchase.number"), s("purchase.dueDate"), s("purchase.payable"), s("common.outstanding"), s("purchase.overdueDays")),
                    detail.purchases.map { listOf(it.purchaseNumber, Dates.formatIso(it.dueDate), money(it.payable), money(it.outstanding), if (it.overdue) it.overdueDays.toString() else "") },
                    numeric = setOf(2, 3, 4),
                    onRowClick = { i -> nav.push(Screen.PurchaseDetail(detail.purchases[i].purchaseId)) },
                )
            }
            HorizontalDivider()
        }
    }
}

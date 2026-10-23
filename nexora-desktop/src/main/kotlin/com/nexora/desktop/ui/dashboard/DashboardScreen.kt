package com.nexora.desktop.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.DashboardResponse
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.FlagChip
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.theme.AppColors
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.dashboard.DashboardViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen
import com.nexora.desktop.viewmodel.shell.Section as NavSection
import kotlinx.coroutines.delay

/**
 * S-DASH-01 Owner dashboard: three bands (money available, obligations, stock and work) plus the supplier dues
 * and recent transactions panels. Every figure is the server's; obligations are never mixed into money available.
 */
@Composable
fun DashboardScreen(nav: NavigationViewModel, reloadTick: Int) {
    val vm: DashboardViewModel = screenViewModel()
    val data by vm.data.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    // Auto refresh every 5 minutes while this screen is shown; leaving the screen cancels the loop.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DashboardViewModel.REFRESH_MILLIS)
            vm.reload()
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenHeader(s("nav.dashboard")) {
            data.value?.let { Text(s("dashboard.asOf", Dates.formatIsoTimestamp(it.asOf))) }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        data.value?.let { d ->
            GettingStartedCard(d, nav)
            DashboardBands(d, nav)
        }
    }
}

@Composable
private fun DashboardBands(d: DashboardResponse, nav: NavigationViewModel) {
    val money = { nav.open(NavSection.MONEY) }
    Section(s("dashboard.moneyAvailable")) {
        FlowRow {
            FigureTile(s("dashboard.totalAvailable"), money(d.totalAvailable), onClick = money)
            FigureTile(s("dashboard.cash"), money(d.cash), onClick = money)
            d.banks.forEach { FigureTile(it.account.accountName, money(it.balance), onClick = money) }
        }
    }
    HorizontalDivider()
    Section(s("dashboard.obligations")) {
        FlowRow {
            FigureTile(s("dashboard.customerReceivables"), money(d.customerReceivables), onClick = { nav.push(Screen.Report("customer-outstanding")) })
            FigureTile(s("dashboard.customerAdvances"), money(d.customerAdvances))
            FigureTile(s("dashboard.supplierPayables"), money(d.supplierPayables), onClick = { nav.push(Screen.SupplierPayables) })
            FigureTile(s("dashboard.supplierOverdue"), money(d.supplierOverdue), valueColour = AppColors.negative, onClick = { nav.push(Screen.SupplierPayables) })
            FigureTile(s("dashboard.manufacturerPayable"), money(d.manufacturerPayable), onClick = { nav.open(NavSection.OUTSOURCING) })
            FigureTile(s("dashboard.workerPayable"), money(d.workerPayable), onClick = { nav.open(NavSection.WORK) })
            FigureTile(s("dashboard.loanOutstanding"), money(d.loanOutstandingPrincipal), onClick = { nav.open(NavSection.LOANS) })
            FigureTile(s("dashboard.chitCommitments"), money(d.chitMonthlyCommitments), onClick = { nav.open(NavSection.CHITS) })
        }
    }
    HorizontalDivider()
    Section(s("dashboard.stockAndWork")) {
        val stock = { nav.open(NavSection.INVENTORY) }
        FlowRow {
            FigureTile(s("dashboard.rawSilk"), weight(d.stock.rawSilkKg), onClick = stock)
            FigureTile(s("dashboard.internalWip"), weight(d.stock.internalWipKg), onClick = stock)
            FigureTile(s("dashboard.externalWip"), weight(d.stock.externalWipKg), onClick = { nav.push(Screen.ExternalWip) })
            FigureTile(s("dashboard.finishedSilk"), weight(d.stock.finishedSilkWarpKg), onClick = stock)
            FigureTile(s("dashboard.finishedKora"), weight(d.stock.finishedKoraWarpKg), onClick = stock)
            FigureTile(s("dashboard.vuda"), weight(d.stock.vudaWarpKg), onClick = stock)
        }
        FlowRow {
            d.ordersByStatus.forEach { (status, count) ->
                FigureTile(s("dashboard.ordersIn", enumLabel("OrderStatus", status)), count.toString(), onClick = { nav.open(NavSection.ORDERS) })
            }
            FigureTile(s("dashboard.lateOrders"), d.lateOrders.toString(), onClick = { nav.open(NavSection.ORDERS) })
            d.productionBatches.forEach { (status, count) ->
                FigureTile(s("dashboard.batchesIn", enumLabel("BatchStatus", status)), count.toString(), onClick = { nav.open(NavSection.PRODUCTION) })
            }
            FigureTile(s("dashboard.openJobs"), d.openOutsourcingJobs.toString(), onClick = { nav.open(NavSection.OUTSOURCING) })
        }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Section(s("dashboard.dues")) {
                if (d.upcomingSupplierDues.isEmpty()) Text(s("dashboard.noDues"))
                d.upcomingSupplierDues.forEach { due ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push(Screen.PurchaseDetail(due.purchaseId)) }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(due.supplier.name, Modifier.weight(1f))
                        Text(due.purchaseNumber, Modifier.width(140.dp))
                        Text(Dates.formatIso(due.dueDate), Modifier.width(110.dp))
                        Text(money(due.outstanding), Modifier.width(140.dp))
                        if (due.overdue) FlagChip(s("flag.overdue", due.overdueDays))
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Section(s("dashboard.recentTransactions")) {
                d.recentTransactions.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push(Screen.Transactions(t.account.id)) }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(Dates.formatIso(t.transactionDate), Modifier.width(110.dp))
                        Text(enumLabel("TransactionType", t.transactionType), Modifier.weight(1f))
                        Text(t.account.accountName, Modifier.width(140.dp))
                        Text(
                            (if (t.direction == "OUT") s("money.out") else s("money.in")) + " " + money(t.amount),
                            color = if (t.direction == "OUT") AppColors.negative else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

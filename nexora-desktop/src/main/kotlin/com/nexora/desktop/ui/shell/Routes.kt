package com.nexora.desktop.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.dashboard.DashboardScreen
import com.nexora.desktop.ui.parties.PartyDetailScreen
import com.nexora.desktop.ui.parties.PartyFormPanel
import com.nexora.desktop.ui.parties.PartyListScreen
import com.nexora.desktop.util.s
import com.nexora.desktop.ui.settings.SettingsScreen
import com.nexora.desktop.ui.documents.AttachDocumentDialog
import com.nexora.desktop.ui.documents.DocumentsScreen
import com.nexora.desktop.ui.notifications.NotificationsScreen
import com.nexora.desktop.ui.reports.ReportViewerScreen
import com.nexora.desktop.ui.reports.ReportsHomeScreen
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.outsourcing.JobDetailScreen
import com.nexora.desktop.ui.outsourcing.JobListScreen
import com.nexora.desktop.ui.outsourcing.NewJobPanel
import com.nexora.desktop.ui.production.BatchDetailScreen
import com.nexora.desktop.ui.production.BatchFormPanel
import com.nexora.desktop.ui.production.BatchListScreen
import com.nexora.desktop.ui.work.RecordWorkPanel
import com.nexora.desktop.ui.work.WorkRecordsScreen
import com.nexora.desktop.ui.chits.ChitDetailScreen
import com.nexora.desktop.ui.chits.ChitsScreen
import com.nexora.desktop.ui.chits.NewChitPanel
import com.nexora.desktop.ui.expenses.ExpensesScreen
import com.nexora.desktop.ui.expenses.RecordExpensePanel
import com.nexora.desktop.ui.loans.LoanDetailScreen
import com.nexora.desktop.ui.loans.LoansScreen
import com.nexora.desktop.ui.loans.NewLoanPanel
import com.nexora.desktop.ui.money.AccountsScreen
import com.nexora.desktop.ui.money.NewAccountPanel
import com.nexora.desktop.ui.money.TransactionsScreen
import com.nexora.desktop.ui.money.TransferDialog
import com.nexora.desktop.ui.payments.PaymentDetailScreen
import com.nexora.desktop.ui.payments.PaymentListScreen
import com.nexora.desktop.ui.payments.RecordPaymentDialog
import com.nexora.desktop.ui.orders.OrderDetailScreen
import com.nexora.desktop.ui.orders.OrderFormPanel
import com.nexora.desktop.ui.orders.OrderListScreen
import com.nexora.desktop.ui.inventory.ExternalWipScreen
import com.nexora.desktop.ui.inventory.MovementHistoryScreen
import com.nexora.desktop.ui.inventory.StockSummaryScreen
import com.nexora.desktop.ui.purchases.NewPurchasePanel
import com.nexora.desktop.ui.purchases.PurchaseDetailScreen
import com.nexora.desktop.ui.purchases.PurchaseListScreen
import com.nexora.desktop.ui.purchases.ReceiveSilkPickerPanel
import com.nexora.desktop.ui.purchases.SupplierPayablesScreen
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen
import com.nexora.desktop.viewmodel.shell.Section

/** Draws the screen of a navigation entry. */
@Composable
fun ScreenContent(screen: Screen, nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    when (screen) {
        is Screen.Root -> RootScreen(screen, nav, reloadTick, searchTick)
        is Screen.PartyDetail -> PartyDetailScreen(nav, screen.partyId, reloadTick)
        is Screen.PurchaseDetail -> PurchaseDetailScreen(nav, screen.purchaseId, reloadTick)
        Screen.SupplierPayables -> SupplierPayablesScreen(nav, reloadTick)
        is Screen.Movements -> MovementHistoryScreen(nav, screen, reloadTick, searchTick)
        Screen.ExternalWip -> ExternalWipScreen(nav, reloadTick)
        is Screen.OrderDetail -> OrderDetailScreen(nav, screen.orderId, reloadTick)
        is Screen.PaymentDetail -> PaymentDetailScreen(nav, screen.paymentId, reloadTick)
        is Screen.LoanDetail -> LoanDetailScreen(nav, screen.loanId, reloadTick)
        is Screen.Report -> ReportViewerScreen(nav, screen.reportKey, reloadTick)
        Screen.Notifications -> NotificationsScreen(nav, reloadTick, searchTick)
        is Screen.BatchDetail -> BatchDetailScreen(nav, screen.batchId, reloadTick)
        is Screen.JobDetail -> JobDetailScreen(nav, screen.jobId, reloadTick)
        is Screen.ChitDetail -> ChitDetailScreen(nav, screen.chitId, reloadTick)
        is Screen.AccountTransactions -> TransactionsScreen(nav, screen.accountId, screen.accountName, emptyMap(), reloadTick, searchTick)
        is Screen.Transactions -> TransactionsScreen(
            nav, null, null, screen.accountId?.let { mapOf("accountId" to it) } ?: emptyMap(), reloadTick, searchTick,
        )
    }
}

@Composable
private fun RootScreen(screen: Screen.Root, nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    when (screen.section) {
        Section.DASHBOARD -> DashboardScreen(nav, reloadTick)
        Section.PARTIES -> PartyListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.PURCHASES -> PurchaseListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.INVENTORY -> StockSummaryScreen(nav, reloadTick)
        Section.ORDERS -> OrderListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.PAYMENTS -> PaymentListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.MONEY -> AccountsScreen(nav, reloadTick)
        Section.REPORTS -> ReportsHomeScreen(nav)
        Section.DOCUMENTS -> DocumentsScreen(nav, reloadTick, searchTick)
        Section.PRODUCTION -> BatchListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.OUTSOURCING -> JobListScreen(nav, screen.filters, reloadTick, searchTick)
        Section.WORK -> WorkRecordsScreen(nav, screen.filters, reloadTick, searchTick)
        Section.EXPENSES -> ExpensesScreen(nav, reloadTick, searchTick)
        Section.LOANS -> LoansScreen(nav, reloadTick, searchTick)
        Section.CHITS -> ChitsScreen(nav, reloadTick, searchTick)
        Section.SETTINGS -> SettingsScreen(nav)
    }
}

/** Draws the form or dialog of an overlay. */
@Composable
fun OverlayContent(overlay: Overlay, nav: NavigationViewModel) {
    when (overlay) {
        is Overlay.PartyForm -> PartyFormPanel(nav, overlay.partyId)
        Overlay.NewPurchase -> NewPurchasePanel(nav)
        is Overlay.OrderForm -> OrderFormPanel(nav, overlay.orderId)
        Overlay.NewAccount -> NewAccountPanel(nav)
        is Overlay.AttachDocument -> AttachDocumentDialog(overlay.referenceType, overlay.referenceId, onDone = { nav.closeOverlay(); nav.reload() })
        is Overlay.BatchForm -> BatchFormPanel(nav, overlay.batchId)
        Overlay.NewJob -> NewJobPanel(nav)
        is Overlay.RecordWork -> RecordWorkPanel(
            overlay.batchId?.let { PickOption(it, overlay.batchNumber ?: it) },
            onClose = { nav.closeOverlay(); nav.reload() },
        )
        Overlay.RecordExpense -> RecordExpensePanel(nav)
        Overlay.NewLoan -> NewLoanPanel(nav)
        Overlay.NewChit -> NewChitPanel(nav)
        Overlay.Transfer -> TransferDialog(onDone = { nav.closeOverlay(); nav.reload() }, onCancel = nav::closeOverlay)
        is Overlay.RecordPayment -> RecordPaymentDialog(
            overlay.partyId,
            overlay.purpose,
            onDone = { nav.closeOverlay(); nav.reload() },
            onOpenPayment = { nav.closeOverlay(); nav.push(Screen.PaymentDetail(it)) },
        )
        is Overlay.ReceiveSilk -> ReceiveSilkPickerPanel(
            overlay.purchaseId,
            onSaved = { nav.closeOverlay(); nav.push(Screen.PurchaseDetail(it.id)) },
            onCancel = nav::closeOverlay,
        )
        // Drawn by the shell itself as a confirmation dialog.
        Overlay.ConfirmLogout -> Unit
    }
}

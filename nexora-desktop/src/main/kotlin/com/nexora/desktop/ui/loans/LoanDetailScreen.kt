package com.nexora.desktop.ui.loans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.LoanPaymentLine
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.MoneyEntryDialog
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.loans.LoanDetailViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel

/** S-LON-03 Loan detail with repayment, interest, reversal and close. */
@Composable
fun LoanDetailScreen(nav: NavigationViewModel, loanId: String, reloadTick: Int) {
    val vm: LoanDetailViewModel = screenViewModel(loanId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    val entry by vm.entry.collectAsState()
    vm.accounts.collectAsState()
    var reversing by remember { mutableStateOf<LoanPaymentLine?>(null) }
    ReloadOn(reloadTick, vm::reload)
    val loan = data.value
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(loan?.loanNumber ?: s("common.loading"), onBack = nav::back) {
                if (loan != null && loan.status == "ACTIVE") {
                    Button(onClick = { vm.openEntry("principal") }) { Text(s("loan.repay")) }
                    OutlinedButton(onClick = { vm.openEntry("interest") }) { Text(s("loan.payInterest")) }
                    if (vm.canClose(loan)) OutlinedButton(onClick = { vm.ask("close") }) { Text(s("loan.close")) }
                }
            }
            LoadBanner(data.error, data.lastUpdated, vm::reload)
            action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
            if (loan == null) return@Column
            FlowRow {
                Column(Modifier.padding(end = 24.dp)) { StatusChip("LoanStatus", loan.status) }
                LabeledValue(s("loan.lender"), loan.lender.name)
                LabeledValue(s("loan.type"), enumLabel("LoanType", loan.loanType))
                LabeledValue(s("loan.principal"), money(loan.principalAmount))
                LabeledValue(s("loan.outstanding"), money(loan.outstandingPrincipal))
                LabeledValue(s("loan.interestPaid"), money(loan.interestPaid))
                LabeledValue(s("loan.startDate"), Dates.formatIso(loan.startDate))
                loan.interestRatePercent?.let { LabeledValue(s("loan.interestRate"), s("loan.percent", it.toPlainString())) }
                loan.interestNotes?.let { LabeledValue(s("loan.interestNotes"), it) }
            }
            Section(s("loan.history")) {
                val paymentList = loan.payments.orEmpty()
                StaticTable(
                    listOf(s("common.date"), s("loan.kind"), s("common.amount"), s("payment.account"), s("common.status")),
                    paymentList.map {
                        listOf(
                            Dates.formatIso(it.date), enumLabel("TransactionType", it.transactionType), money(it.amount),
                            it.account?.accountName.orEmpty(), if (it.reversed) enumLabel("RecordStatus", "REVERSED") else enumLabel("RecordStatus", "RECORDED"),
                        )
                    },
                    numeric = setOf(2),
                    trailing = { i ->
                        val line = paymentList[i]
                        if (!line.reversed) TextButton(onClick = { reversing = line }) { Text(s("loan.reverse")) }
                    },
                )
            }
        }
        if (entry.kind != null && loan != null) {
            MoneyEntryDialog(
                title = if (entry.kind == "principal") s("loan.repay") else s("loan.payInterest"),
                entry = entry,
                accounts = vm::offeredAccounts,
                onEdit = vm::editEntry,
                onSave = vm::submitEntry,
                onCancel = vm::closeEntry,
                info = if (entry.kind == "principal") s("loan.outstandingInfo", money(loan.outstandingPrincipal)) else null,
                noteLabel = if (entry.kind == "interest") s("loan.periodNote") else null,
            )
        }
        reversing?.let { line ->
            ConfirmDialog(
                s("loan.reverse"), "${Dates.formatIso(line.date)} · ${money(line.amount)}", s("loan.reverseEffect"), s("loan.reverse"),
                onConfirm = { reason -> vm.reversePayment(line.transactionId, reason.orEmpty()); reversing = null },
                onCancel = { reversing = null }, reasonRequired = true,
            )
        }
        if (loan != null && action.confirming == "close") {
            ConfirmDialog(s("loan.close"), loan.loanNumber, s("loan.closeEffect"), s("loan.close"), { vm.close() }, vm::dismiss, busy = action.busy)
        }
    }
}

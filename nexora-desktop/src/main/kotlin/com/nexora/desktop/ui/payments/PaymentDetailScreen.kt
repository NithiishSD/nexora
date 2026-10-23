package com.nexora.desktop.ui.payments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.LoadBanner
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
import com.nexora.desktop.ui.documents.DocumentsSection
import com.nexora.desktop.viewmodel.payments.PaymentDetailViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay

/** S-PAY-03 Payment detail: header, allocations (released after reversal), Reverse with a reason. */
@Composable
fun PaymentDetailScreen(nav: NavigationViewModel, paymentId: String, reloadTick: Int) {
    val vm: PaymentDetailViewModel = screenViewModel(paymentId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    val p = data.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(p?.paymentNumber ?: s("common.loading"), onBack = nav::back) {
            if (p != null && vm.canClearCheque(p) && nav.can("PAYMENT_CREATE")) {
                OutlinedButton(onClick = { vm.ask("clearCheque") }) { Text(s("payment.clearCheque")) }
            }
            if (p != null && vm.canBounceCheque(p) && nav.can("PAYMENT_REVERSE")) {
                OutlinedButton(onClick = { vm.ask("bounceCheque") }) { Text(s("payment.bounceCheque")) }
            }
            if (p != null && vm.canRefund(p) && nav.can("PAYMENT_CREATE")) {
                OutlinedButton(onClick = { vm.ask("refund") }) { Text(s("payment.refund")) }
            }
            if (p != null && vm.canReverse(p) && nav.can("PAYMENT_REVERSE")) {
                OutlinedButton(onClick = { vm.ask("reverse") }) { Text(s("payment.reverse")) }
            }
            if (p != null && nav.can("DOCUMENT_MANAGE")) OutlinedButton(onClick = { nav.show(Overlay.AttachDocument("PAYMENT", p.id)) }) { Text(s("document.attach")) }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
        if (p == null) return@Column
        FlowRow {
            Column(Modifier.padding(end = 24.dp)) { StatusChip("RecordStatus", p.status) }
            LabeledValue(s("payment.party"), p.party.name)
            LabeledValue(s("payment.direction"), enumLabel("PaymentDirection", p.direction))
            LabeledValue(s("payment.method"), enumLabel("PaymentMethod", p.paymentMethod))
            LabeledValue(s("payment.account"), p.account.accountName)
            LabeledValue(s("payment.date"), Dates.formatIso(p.paymentDate))
            LabeledValue(s("payment.amount"), money(p.amount))
            LabeledValue(s("payment.allocatedTotal"), money(p.allocatedAmount))
            LabeledValue(s("payment.unallocated"), money(p.unallocatedAmount))
            p.referenceNumber?.let { LabeledValue(s("payment.reference"), it) }
            p.refundOfPaymentId?.let { LabeledValue(s("payment.refundOfPayment"), it) }
            p.reversalReason?.let { LabeledValue(s("payment.reversalReason"), it) }
            p.notes?.let { LabeledValue(s("common.notes"), it) }
        }
        Section(s("payment.allocations")) {
            StaticTable(
                listOf(s("payment.obligation"), s("common.date"), s("common.amount"), s("payment.released")),
                p.allocations.map {
                    listOf(it.targetLabel, Dates.formatIso(it.obligationDate), money(it.amount), if (it.released) s("common.yes") else s("common.no"))
                },
                numeric = setOf(2),
            )
        }
        if (nav.can("DOCUMENT_MANAGE")) DocumentsSection(nav, "PAYMENT", p.id, reloadTick)
    }
    if (p != null && action.confirming == "reverse") {
        ConfirmDialog(
            s("payment.reverse"), "${p.paymentNumber} · ${money(p.amount)}", s("payment.reverseEffect"), s("payment.reverse"),
            onConfirm = vm::reverse, onCancel = vm::dismiss, reasonRequired = true, busy = action.busy,
        )
    }
    if (p != null && action.confirming == "clearCheque") {
        ConfirmDialog(
            s("payment.clearCheque"), "${p.paymentNumber} · ${money(p.amount)}", s("payment.clearChequeEffect"), s("payment.clearCheque"),
            onConfirm = { _ -> vm.clearCheque() }, onCancel = vm::dismiss, reasonRequired = false, busy = action.busy,
        )
    }
    if (p != null && action.confirming == "bounceCheque") {
        ConfirmDialog(
            s("payment.bounceCheque"), "${p.paymentNumber} · ${money(p.amount)}", s("payment.bounceChequeEffect"), s("payment.bounceCheque"),
            onConfirm = { reason -> vm.bounceCheque(notes = reason) }, onCancel = vm::dismiss, reasonRequired = true, busy = action.busy,
        )
    }
    if (p != null && action.confirming == "refund") {
        ConfirmDialog(
            s("payment.refund"), "${p.paymentNumber} · ${s("payment.unallocated")}: ${money(p.unallocatedAmount)}", s("payment.refundEffect"), s("payment.refund"),
            onConfirm = { notes -> vm.refundAdvance(notes = notes) }, onCancel = vm::dismiss, reasonRequired = false, busy = action.busy,
        )
    }
}

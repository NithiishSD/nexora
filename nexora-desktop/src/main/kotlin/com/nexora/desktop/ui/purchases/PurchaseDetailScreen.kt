package com.nexora.desktop.ui.purchases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FlagChip
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.IndianFormat.rate
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.ui.documents.DocumentsSection
import com.nexora.desktop.viewmodel.purchases.PurchaseDetailViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-PUR-03 Purchase detail with the status-dependent actions. */
@Composable
fun PurchaseDetailScreen(nav: NavigationViewModel, purchaseId: String, reloadTick: Int) {
    val vm: PurchaseDetailViewModel = screenViewModel(purchaseId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    val p = data.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(p?.purchaseNumber ?: s("common.loading"), onBack = nav::back) {
            if (p != null) {
                val actions = vm.actionsFor(p)
                val canEdit = nav.can("PURCHASE_CREATE")
                if (canEdit && actions.receive) Button(onClick = { vm.ask("receive") }) { Text(s("purchase.receive")) }
                if (canEdit && actions.returnToSupplier) OutlinedButton(onClick = { vm.ask("return") }) { Text(s("purchase.return")) }
                if (canEdit) OutlinedButton(onClick = { vm.ask("notes") }) { Text(s("purchase.editNotes")) }
                if (canEdit && actions.cancel) OutlinedButton(onClick = { vm.ask("cancel") }) { Text(s("purchase.cancel")) }
                if (nav.can("PAYMENT_CREATE") && actions.pay) {
                    OutlinedButton(onClick = { nav.show(Overlay.RecordPayment(p.supplier.id, "SUPPLIER")) }) { Text(s("purchase.pay")) }
                }
                if (nav.can("DOCUMENT_MANAGE")) {
                    OutlinedButton(onClick = { nav.show(Overlay.AttachDocument("PURCHASE", p.id)) }) { Text(s("document.attach")) }
                }
            }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
        if (p != null) {
            PurchaseBody(p, nav)
            if (nav.can("DOCUMENT_MANAGE")) DocumentsSection(nav, "PURCHASE", p.id, reloadTick)
        }
    }
    if (p == null) return
    when (action.confirming) {
        "receive" -> ReceiveSilkDialog(p.id, onSaved = { vm.showUpdated(it); vm.dismiss() }, onCancel = vm::dismiss)
        "return" -> ReturnDialog(p, onSaved = { vm.showUpdated(it); vm.dismiss() }, onCancel = vm::dismiss)
        "cancel" -> ConfirmDialog(
            s("purchase.cancel"), p.purchaseNumber, s("purchase.cancelEffect"), s("purchase.cancel"),
            onConfirm = { reason -> vm.cancel(reason.orEmpty()) }, onCancel = vm::dismiss, reasonRequired = true, busy = action.busy,
        )
        "notes" -> NotesDialog(p, onSave = vm::saveNotes, onCancel = vm::dismiss, busy = action.busy)
    }
}

@Composable
private fun PurchaseBody(p: PurchaseResponse, nav: NavigationViewModel) {
    FlowRow {
        Column(Modifier.padding(end = 24.dp)) {
            StatusChip("PurchaseStatus", p.status)
            StatusChip("PaymentStatusView", p.paymentStatus, Modifier.padding(top = 4.dp))
            if (p.overdue) FlagChip(s("flag.overdue", p.overdueDays), Modifier.padding(top = 4.dp))
        }
        LabeledValue(s("purchase.supplier"), p.supplier.name)
        LabeledValue(s("purchase.product"), enumLabel("ProductType", p.productType))
        LabeledValue(s("purchase.date"), Dates.formatIso(p.purchaseDate))
        LabeledValue(s("purchase.orderedKg"), weight(p.orderedWeightKg))
        LabeledValue(s("purchase.rate"), rate(p.ratePerKg, s("unit.kg")))
        LabeledValue(s("purchase.value"), money(p.orderedValue))
        LabeledValue(s("purchase.paymentType"), enumLabel("PurchasePaymentType", p.paymentType))
        LabeledValue(s("purchase.dueDate"), Dates.formatIso(p.dueDate))
        LabeledValue(s("purchase.receivedKg"), weight(p.receivedWeightKg))
        LabeledValue(s("purchase.acceptedKg"), weight(p.acceptedWeightKg))
        LabeledValue(s("purchase.rejectedKg"), weight(p.rejectedWeightKg))
        LabeledValue(s("purchase.returnedKg"), weight(p.returnedWeightKg))
        LabeledValue(s("purchase.remainingKg"), weight(p.remainingWeightKg))
        LabeledValue(s("purchase.payable"), money(p.payableAmount))
        LabeledValue(s("common.allocated"), money(p.allocatedAmount))
        LabeledValue(s("common.outstanding"), money(p.outstandingAmount))
        p.billReference?.let { LabeledValue(s("purchase.billNumber"), it) }
        p.notes?.let { LabeledValue(s("common.notes"), it) }
    }
    Section(s("purchase.receipts")) {
        StaticTable(
            listOf(s("common.number"), s("common.date"), s("purchase.receivedKg"), s("purchase.acceptedKg"), s("purchase.rejectedKg")),
            p.receipts.map { listOf(it.receiptNumber, Dates.formatIso(it.receiptDate), weight(it.receivedWeightKg), weight(it.acceptedWeightKg), weight(it.rejectedWeightKg)) },
            numeric = setOf(2, 3, 4),
        )
    }
    Section(s("purchase.returns")) {
        StaticTable(
            listOf(s("common.number"), s("common.date"), s("purchase.returnedKg"), s("common.reason")),
            p.returns.map { listOf(it.returnNumber, Dates.formatIso(it.returnDate), weight(it.returnedWeightKg), it.reason) },
            numeric = setOf(2),
        )
    }
    Section(s("purchase.payments")) {
        OutlinedButton(onClick = { nav.push(Screen.Root(com.nexora.desktop.viewmodel.shell.Section.PAYMENTS, mapOf("partyId" to p.supplier.id))) }) {
            Text(s("purchase.paymentsOfSupplier"))
        }
    }
}

@Composable
private fun NotesDialog(p: PurchaseResponse, onSave: (String, String) -> Unit, onCancel: () -> Unit, busy: Boolean) {
    var notes by remember { mutableStateOf(p.notes.orEmpty()) }
    var bill by remember { mutableStateOf(p.billReference.orEmpty()) }
    FormDialog(s("purchase.editNotes"), s("common.save"), busy, null, { onSave(notes, bill) }, onCancel) {
        TextInput(s("purchase.billNumber"), bill, { bill = it }, maxLength = 100)
        TextInput(s("common.notes"), notes, { notes = it }, singleLine = false, maxLength = 1000)
    }
}

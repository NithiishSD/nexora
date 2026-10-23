package com.nexora.desktop.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PaymentResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FieldError
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.ResultPanel
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.payments.RecordPaymentViewModel

/**
 * S-PAY-02 Record payment. After saving, the panel switches to the server's result: payment number,
 * allocations as the server made them, and the unallocated advance (NFR-USE-02).
 */
@Composable
fun RecordPaymentDialog(partyId: String?, purpose: String?, onDone: () -> Unit, onOpenPayment: (String) -> Unit) {
    val vm: RecordPaymentViewModel = screenViewModel(partyId, purpose)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val openItems by vm.openItems.collectAsState()
    val errors = status.fieldErrors
    status.result?.let { payment ->
        PaymentResult(payment, onDone = onDone, onAnother = vm::recordAnother, onOpen = { onOpenPayment(payment.id) })
        return
    }
    FormPanel(
        title = s("overlay.RecordPayment"),
        saveLabel = s("common.save"),
        submitting = status.submitting,
        dirty = status.dirty,
        error = status.error,
        onSave = vm::save,
        onCancel = onDone,
    ) {
        Segmented(
            s("payment.direction"),
            listOf("CUSTOMER" to s("payment.received"), "SUPPLIER" to s("payment.paidSupplier"), "MANUFACTURER" to s("payment.paidManufacturer"), "WORKER" to s("payment.paidWorker")),
            form.purpose,
            vm::setPurpose,
        )
        PartyPicker(s("payment.party"), form.purpose, form.party, vm::pickParty, error = errors["partyId"], tag = "payment.party")
        MoneyField(s("payment.amount"), form.amount, { v -> vm.edit { it.copy(amount = v) } }, error = errors["amount"], tag = "payment.amount")
        Dropdown(s("payment.method"), AccountRules.METHODS.map { it to enumLabel("PaymentMethod", it) }, form.method, vm::setMethod, error = errors["paymentMethod"], tag = "payment.method")
        Dropdown(
            s("payment.account"),
            vm.offeredAccounts(form.method).map { it.id to it.accountName },
            form.accountId,
            { v -> vm.edit { it.copy(accountId = v) } },
            error = errors["financialAccountId"],
            tag = "payment.account",
        )
        DateField(s("payment.date"), form.paymentDate, { v -> vm.edit { it.copy(paymentDate = v) } }, error = errors["paymentDate"])
        TextInput(s("payment.reference"), form.referenceNumber, { v -> vm.edit { it.copy(referenceNumber = v) } }, maxLength = 100)
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 500)
        if (accounts.isEmpty()) Text(s("payment.noAccounts"), color = MaterialTheme.colorScheme.error)
        Section(s("payment.openItems")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = form.manualAllocation,
                    onCheckedChange = { on -> vm.edit { it.copy(manualAllocation = on) } },
                    modifier = Modifier.semantics { contentDescription = s("payment.allocateManually") },
                )
                Text(s("payment.allocateManually"))
            }
            if (!form.manualAllocation) Text(s("payment.oldestFirst"))
            if (openItems.isEmpty() && form.party != null) Text(s("payment.noOpenItems"))
            openItems.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(220.dp)) {
                        Text(item.label)
                        Text(s("payment.outstandingOn", money(item.outstanding), Dates.formatIso(item.date.ifEmpty { null })), style = MaterialTheme.typography.bodySmall)
                    }
                    if (form.manualAllocation) {
                        MoneyField(s("common.amount"), form.allocations[item.targetId].orEmpty(), { v -> vm.setAllocation(item.targetId, v) },
                            Modifier.width(200.dp), errors["allocations.${item.targetId}"])
                    }
                }
            }
            errors["allocations"]?.let { FieldError(it) }
        }
    }
}

@Composable
private fun PaymentResult(p: PaymentResponse, onDone: () -> Unit, onAnother: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s("common.saved", p.paymentNumber), style = MaterialTheme.typography.titleLarge)
        ResultPanel(
            s("payment.serverAllocated"),
            p.allocations.map { it.targetLabel to money(it.amount) } +
                listOf(s("payment.allocatedTotal") to money(p.allocatedAmount), s("payment.unallocated") to money(p.unallocatedAmount)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDone) { Text(s("common.done")) }
            OutlinedButton(onClick = onAnother) { Text(s("payment.recordAnother")) }
            OutlinedButton(onClick = onOpen) { Text(s("payment.open")) }
        }
    }
}

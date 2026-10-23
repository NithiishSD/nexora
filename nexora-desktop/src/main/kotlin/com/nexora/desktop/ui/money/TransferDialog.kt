package com.nexora.desktop.ui.money

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.money.TransferViewModel

/** S-FIN-05 Transfer between own accounts. */
@Composable
fun TransferDialog(onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: TransferViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { if (status.result != null) onDone() }
    FormDialog(s("money.transfer"), s("common.save"), status.submitting, status.error, vm::save, onCancel) {
        Dropdown(s("transfer.from"), accounts.map { it.id to it.accountName }, form.fromId, { v -> vm.edit { it.copy(fromId = v) } }, error = errors["sourceAccountId"])
        // Information only: the current balance as the server reported it in the accounts list.
        accounts.firstOrNull { it.id == form.fromId }?.let { Text(s("transfer.balance", money(it.balance))) }
        Dropdown(s("transfer.to"), accounts.map { it.id to it.accountName }, form.toId, { v -> vm.edit { it.copy(toId = v) } }, error = errors["destinationAccountId"])
        MoneyField(s("payment.amount"), form.amount, { v -> vm.edit { it.copy(amount = v) } }, error = errors["amount"])
        DateField(s("common.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["transferDate"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 500)
    }
}

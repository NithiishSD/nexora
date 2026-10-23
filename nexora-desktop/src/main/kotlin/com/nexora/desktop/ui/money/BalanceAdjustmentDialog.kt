package com.nexora.desktop.ui.money

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.money.BalanceAdjustmentViewModel

/** S-FIN-06 Balance adjustment, only after counting cash or checking the bank statement. */
@Composable
fun BalanceAdjustmentDialog(account: AccountResponse, onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: BalanceAdjustmentViewModel = screenViewModel(account.id)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { if (status.result != null) onDone() }
    FormDialog(s("money.adjustTitle", account.accountName), s("common.save"), status.submitting, status.error, vm::save, onCancel) {
        Text(s("money.adjustWarning"), color = MaterialTheme.colorScheme.error)
        Segmented(s("money.direction"), listOf("IN" to s("money.increase"), "OUT" to s("money.decrease")), form.direction, { v -> vm.edit { it.copy(direction = v) } })
        MoneyField(s("payment.amount"), form.amount, { v -> vm.edit { it.copy(amount = v) } }, error = errors["amount"])
        DateField(s("common.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["transactionDate"])
        TextInput(s("common.reason"), form.reason, { v -> vm.edit { it.copy(reason = v) } }, error = errors["reason"], maxLength = 255)
    }
}

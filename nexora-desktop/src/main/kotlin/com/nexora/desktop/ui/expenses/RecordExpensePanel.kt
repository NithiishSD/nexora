package com.nexora.desktop.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.expenses.RecordExpenseViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay

/** S-EXP-02 Record expense. After saving, a receipt can be attached to the new expense. */
@Composable
fun RecordExpensePanel(nav: NavigationViewModel) {
    val vm: RecordExpenseViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val errors = status.fieldErrors
    status.result?.let { expense ->
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s("common.saved", expense.expenseNumber), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { nav.closeOverlay(); nav.reload() }) { Text(s("common.done")) }
                if (nav.can("DOCUMENT_MANAGE")) {
                    OutlinedButton(onClick = { nav.show(Overlay.AttachDocument("EXPENSE", expense.id)) }) { Text(s("expense.attachReceipt")) }
                }
                OutlinedButton(onClick = vm::clearResult) { Text(s("payment.recordAnother")) }
            }
        }
        return
    }
    val categoryOptions = if (categories.isNotEmpty()) categories else EXPENSE_CATEGORIES
    FormPanel(s("overlay.RecordExpense"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        Dropdown(s("expense.category"), categoryOptions.map { it to enumLabel("ExpenseCategory", it) }, form.category, { v -> vm.edit { it.copy(category = v) } }, error = errors["category"])
        MoneyField(s("payment.amount"), form.amount, { v -> vm.edit { it.copy(amount = v) } }, error = errors["amount"], tag = "expense.amount")
        Dropdown(s("payment.account"), accounts.map { it.id to it.accountName }, form.accountId, vm::setAccount, error = errors["financialAccountId"])
        Dropdown(s("payment.method"), vm.methodsForAccount(form.accountId).map { it to enumLabel("PaymentMethod", it) }, form.method, { v ->
            vm.edit { it.copy(method = v) }
        }, error = errors["paymentMethod"])
        DateField(s("expense.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["expenseDate"])
        TextInput(s("expense.description"), form.description, { v -> vm.edit { it.copy(description = v) } }, error = errors["description"], maxLength = 500, tag = "expense.description")
        Segmented(s("expense.kind"), listOf("BUSINESS", "PERSONAL").map { it to enumLabel("ExpenseKind", it) }, form.kind, { v -> vm.edit { it.copy(kind = v) } })
        Segmented(s("expense.recurrence"), listOf("ONE_TIME", "MONTHLY").map { it to enumLabel("Recurrence", it) }, form.recurrence, { v -> vm.edit { it.copy(recurrence = v) } })
    }
}

package com.nexora.desktop.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.common.MoneyEntry

/**
 * Dialog for one money entry on a detail screen (loan repayment, interest, chit contribution ...).
 * With [cashOnly] the method is fixed to Cash and only cash accounts are offered (chits).
 */
@Composable
fun MoneyEntryDialog(
    title: String,
    entry: MoneyEntry,
    accounts: (String?) -> List<AccountResponse>,
    onEdit: ((MoneyEntry) -> MoneyEntry) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    cashOnly: Boolean = false,
    info: String? = null,
    noteLabel: String? = null,
) {
    val errors = entry.fieldErrors
    FormDialog(title, s("common.save"), entry.submitting, entry.error, onSave, onCancel) {
        info?.let { Text(it) }
        MoneyField(s("payment.amount"), entry.amount, { v -> onEdit { it.copy(amount = v) } }, error = errors["amount"], tag = "entry.amount")
        if (cashOnly) {
            Text(s("chit.cashOnlyNote"))
        } else {
            Dropdown(s("payment.method"), AccountRules.METHODS.map { it to enumLabel("PaymentMethod", it) }, entry.method, { v ->
                onEdit { e -> e.copy(method = v, accountId = e.accountId?.takeIf { id -> accounts(v).any { it.id == id } }) }
            }, error = errors["paymentMethod"])
        }
        Dropdown(
            s("payment.account"),
            accounts(entry.method).map { it.id to it.accountName },
            entry.accountId,
            { v -> onEdit { it.copy(accountId = v) } },
            error = errors["financialAccountId"],
            tag = "entry.account",
        )
        DateField(s("common.date"), entry.date, { v -> onEdit { it.copy(date = v) } }, error = errors["paymentDate"] ?: errors["entryDate"])
        if (noteLabel != null) TextInput(noteLabel, entry.note, { v -> onEdit { it.copy(note = v) } }, maxLength = 100)
    }
}

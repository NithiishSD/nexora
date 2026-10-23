package com.nexora.desktop.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.money.NewAccountViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel

/** S-FIN-02 New account. Only the last 4 digits of a bank account number are asked for. */
@Composable
fun NewAccountPanel(nav: NavigationViewModel) {
    val vm: NewAccountViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { if (status.result != null) { nav.closeOverlay(); nav.reload() } }
    FormPanel(s("money.newAccount"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        TextInput(s("account.name"), form.name, { v -> vm.edit { it.copy(name = v) } }, error = errors["accountName"], maxLength = 100)
        Segmented(s("account.type"), listOf("CASH", "BANK").map { it to enumLabel("AccountType", it) }, form.type, vm::setType)
        if (form.type == "BANK") {
            TextInput(s("account.bankName"), form.bankName, { v -> vm.edit { it.copy(bankName = v) } }, error = errors["bankName"], maxLength = 100)
            TextInput(s("account.last4"), form.last4, vm::setLast4, error = errors["accountNumberLast4"], maxLength = 4)
        }
        MoneyField(s("account.openingBalance"), form.openingBalance, { v -> vm.edit { it.copy(openingBalance = v) } }, error = errors["openingBalance"])
        DateField(s("account.openingDate"), form.openingDate, { v -> vm.edit { it.copy(openingDate = v) } }, error = errors["openingDate"])
    }
}

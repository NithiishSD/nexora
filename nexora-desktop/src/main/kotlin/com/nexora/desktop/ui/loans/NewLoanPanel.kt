package com.nexora.desktop.ui.loans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.PercentField
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.loans.NewLoanViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

/** S-LON-02 New loan. */
@Composable
fun NewLoanPanel(nav: NavigationViewModel) {
    val vm: NewLoanViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    vm.accounts.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let { nav.closeOverlay(); nav.push(Screen.LoanDetail(it.id)) } }
    FormPanel(s("loan.new"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        PartyPicker(s("loan.lender"), "LENDER", form.lender, { v -> vm.edit { it.copy(lender = v) } }, error = errors["lenderId"])
        Segmented(s("loan.type"), listOf("BANK", "PRIVATE").map { it to enumLabel("LoanType", it) }, form.loanType, { v -> vm.edit { it.copy(loanType = v) } })
        MoneyField(s("loan.principal"), form.principal, { v -> vm.edit { it.copy(principal = v) } }, error = errors["principalAmount"])
        DateField(s("loan.startDate"), form.startDate, { v -> vm.edit { it.copy(startDate = v) } }, error = errors["startDate"])
        Dropdown(s("payment.method"), AccountRules.METHODS.map { it to enumLabel("PaymentMethod", it) }, form.method, { v ->
            vm.edit { f -> f.copy(method = v, accountId = f.accountId?.takeIf { id -> vm.offeredAccounts(v).any { it.id == id } }) }
        }, error = errors["paymentMethod"])
        Dropdown(s("loan.receivingAccount"), vm.offeredAccounts(form.method).map { it.id to it.accountName }, form.accountId, { v ->
            vm.edit { it.copy(accountId = v) }
        }, error = errors["financialAccountId"])
        // Informational only: interest is never calculated by the app (FR-LOAN-10 is POST-MVP).
        PercentField(s("loan.interestRate"), form.interestRate, { v -> vm.edit { it.copy(interestRate = v) } }, error = errors["interestRatePercent"])
        TextInput(s("loan.interestNotes"), form.interestNotes, { v -> vm.edit { it.copy(interestNotes = v) } }, maxLength = 500)
    }
}

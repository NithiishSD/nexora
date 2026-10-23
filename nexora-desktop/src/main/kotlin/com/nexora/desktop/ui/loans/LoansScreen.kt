package com.nexora.desktop.ui.loans

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexora.desktop.api.dto.LoanResponse
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.loans.LoanListViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-LON-01 Loans. */
@Composable
fun LoansScreen(nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    val vm: LoanListViewModel = screenViewModel()
    ListScreen(
        title = s("nav.loans"),
        vm = vm,
        columns = listOf(
            textColumn<LoanResponse>(s("common.number"), 1.2f) { it.loanNumber },
            textColumn(s("loan.lender"), 1.5f) { it.lender.name },
            textColumn(s("loan.type"), 0.8f) { enumLabel("LoanType", it.loanType) },
            textColumn(s("loan.startDate"), 1f, "startDate") { Dates.formatIso(it.startDate) },
            textColumn(s("loan.principal"), 1.2f, numeric = true) { money(it.principalAmount) },
            textColumn(s("loan.outstanding"), 1.2f, "outstandingPrincipal", numeric = true) { money(it.outstandingPrincipal) },
            textColumn(s("loan.interestPaid"), 1.2f, numeric = true) { money(it.interestPaid) },
            TableColumn(s("common.status"), 0.8f) { l -> Row(Modifier.weight(0.8f)) { StatusChip("LoanStatus", l.status) } },
        ),
        emptyMessage = s("loan.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.LoanDetail(it.id)) },
        newLabel = s("loan.new"),
        onNew = { nav.show(Overlay.NewLoan) },
    )
}

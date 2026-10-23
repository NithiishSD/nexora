package com.nexora.desktop.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.money.AccountsViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-FIN-01 Accounts and cash position. */
@Composable
fun AccountsScreen(nav: NavigationViewModel, reloadTick: Int) {
    val vm: AccountsViewModel = screenViewModel()
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    val position by vm.position.collectAsState()
    var adjusting by remember { mutableStateOf<AccountResponse?>(null) }
    var closing by remember { mutableStateOf<AccountResponse?>(null) }
    ReloadOn(reloadTick, vm::refresh)
    val manage = nav.can("FINANCE_MANAGE")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(s("nav.money")) {
            OutlinedButton(onClick = { nav.push(Screen.Transactions()) }) { Text(s("money.allTransactions")) }
            if (manage) {
                OutlinedButton(onClick = { nav.show(Overlay.Transfer) }) { Text(s("money.transfer")) }
                Button(onClick = { nav.show(Overlay.NewAccount) }) { Text(s("money.newAccount")) }
            }
        }
        LoadBanner(data.error, data.lastUpdated, vm::refresh)
        action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
        position?.let { p ->
            FlowRow {
                FigureTile(s("money.physicalCash"), money(p.totalCash))
                p.banks.forEach { FigureTile(it.account.accountName, money(it.balance)) }
                FigureTile(s("dashboard.totalAvailable"), money(p.totalAvailable))
            }
        }
        data.value?.accounts?.forEach { account ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(account.accountName, Modifier.weight(1.5f))
                Text(enumLabel("AccountType", account.accountType), Modifier.weight(0.7f))
                Text(account.bankName.orEmpty(), Modifier.weight(1.2f))
                Text(account.maskedAccountNumber.orEmpty(), Modifier.weight(1f))
                Text(money(account.balance), Modifier.weight(1f))
                Row(Modifier.weight(0.8f)) { StatusChip("AccountStatus", account.status) }
                TextButton(onClick = { nav.push(Screen.AccountTransactions(account.id, account.accountName)) }) { Text(s("money.transactions")) }
                if (manage && account.status == "ACTIVE") TextButton(onClick = { adjusting = account }) { Text(s("money.adjust")) }
                if (manage && vm.canClose(account)) TextButton(onClick = { closing = account }) { Text(s("money.close")) }
            }
            HorizontalDivider()
        }
    }
    adjusting?.let { account ->
        BalanceAdjustmentDialog(account, onDone = { adjusting = null; vm.refresh() }, onCancel = { adjusting = null })
    }
    closing?.let { account ->
        ConfirmDialog(
            s("money.close"), account.accountName, s("money.closeEffect"), s("money.close"),
            onConfirm = { vm.close(account.id); closing = null }, onCancel = { closing = null }, busy = action.busy,
        )
    }
}

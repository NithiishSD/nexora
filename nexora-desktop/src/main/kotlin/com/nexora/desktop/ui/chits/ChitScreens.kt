package com.nexora.desktop.ui.chits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.ChitEntryLine
import com.nexora.desktop.api.dto.ChitResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.MoneyEntryDialog
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.chits.ChitDetailViewModel
import com.nexora.desktop.viewmodel.chits.ChitListViewModel
import com.nexora.desktop.viewmodel.chits.NewChitViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-CHT-01 Chits. */
@Composable
fun ChitsScreen(nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    val vm: ChitListViewModel = screenViewModel()
    ListScreen(
        title = s("nav.chits"),
        vm = vm,
        columns = listOf(
            textColumn<ChitResponse>(s("common.number"), 1.1f) { it.chitNumber },
            textColumn(s("chit.name"), 1.4f) { it.chitName },
            textColumn(s("chit.total"), 1.1f, numeric = true) { money(it.totalAmount) },
            textColumn(s("chit.monthly"), 1.1f, numeric = true) { money(it.monthlyAmount) },
            textColumn(s("chit.months"), 0.7f, numeric = true) { it.durationMonths.toString() },
            textColumn(s("chit.contributions"), 0.9f, numeric = true) { it.contributionsCount.toString() },
            textColumn(s("chit.remaining"), 0.9f, numeric = true) { it.remainingExpectedContributions.toString() },
            textColumn(s("chit.payout"), 1.1f, numeric = true) { money(it.payoutReceived) },
            TableColumn(s("common.status"), 0.8f) { c -> Row(Modifier.weight(0.8f)) { StatusChip("ChitStatus", c.status) } },
        ),
        emptyMessage = s("chit.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.ChitDetail(it.id)) },
        newLabel = s("chit.new"),
        onNew = { nav.show(Overlay.NewChit) },
    )
}

/** S-CHT-02 New chit. */
@Composable
fun NewChitPanel(nav: NavigationViewModel) {
    val vm: NewChitViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let { nav.closeOverlay(); nav.push(Screen.ChitDetail(it.id)) } }
    FormPanel(s("chit.new"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        TextInput(s("chit.name"), form.name, { v -> vm.edit { it.copy(name = v) } }, error = errors["chitName"], maxLength = 100)
        MoneyField(s("chit.total"), form.total, { v -> vm.edit { it.copy(total = v) } }, error = errors["totalAmount"])
        MoneyField(s("chit.monthly"), form.monthly, { v -> vm.edit { it.copy(monthly = v) } }, error = errors["monthlyAmount"])
        TextInput(s("chit.months"), form.months, { v -> vm.edit { it.copy(months = v.filter(Char::isDigit)) } }, error = errors["durationMonths"], maxLength = 3)
        DateField(s("chit.startDate"), form.startDate, { v -> vm.edit { it.copy(startDate = v) } }, error = errors["startDate"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, singleLine = false, maxLength = 1000)
    }
}

/** S-CHT-03 Chit detail: cash-only entries, reversal, close. */
@Composable
fun ChitDetailScreen(nav: NavigationViewModel, chitId: String, reloadTick: Int) {
    val vm: ChitDetailViewModel = screenViewModel(chitId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    val entry by vm.entry.collectAsState()
    val cashAccounts by vm.cashAccounts.collectAsState()
    var reversing by remember { mutableStateOf<ChitEntryLine?>(null) }
    ReloadOn(reloadTick, vm::reload)
    val chit = data.value
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(chit?.chitName ?: s("common.loading"), onBack = nav::back) {
                if (chit != null && chit.status != "CLOSED") {
                    Button(onClick = { vm.openEntry("contributions") }) { Text(s("chit.contribute")) }
                    if (chit.status == "ACTIVE") OutlinedButton(onClick = { vm.openEntry("payout") }) { Text(s("chit.recordPayout")) }
                    OutlinedButton(onClick = { vm.openEntry("organiser-payments") }) { Text(s("chit.organiser")) }
                    OutlinedButton(onClick = { vm.ask("close") }) { Text(s("chit.close")) }
                }
            }
            LoadBanner(data.error, data.lastUpdated, vm::reload)
            action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
            if (chit == null) return@Column
            FlowRow {
                Column(Modifier.padding(end = 24.dp)) { StatusChip("ChitStatus", chit.status) }
                LabeledValue(s("common.number"), chit.chitNumber)
                LabeledValue(s("chit.total"), money(chit.totalAmount))
                LabeledValue(s("chit.monthly"), money(chit.monthlyAmount))
                LabeledValue(s("chit.months"), chit.durationMonths.toString())
                LabeledValue(s("chit.contributions"), chit.contributionsCount.toString())
                LabeledValue(s("chit.totalContributed"), money(chit.totalContributed))
                LabeledValue(s("chit.remaining"), chit.remainingExpectedContributions.toString())
                LabeledValue(s("chit.organiserPaid"), money(chit.organiserPayments))
                LabeledValue(s("chit.payout"), money(chit.payoutReceived))
            }
            Section(s("loan.history")) {
                val entries = chit.entries.orEmpty()
                StaticTable(
                    listOf(s("common.date"), s("loan.kind"), s("common.amount"), s("common.status")),
                    entries.map {
                        listOf(Dates.formatIso(it.date), enumLabel("TransactionType", it.transactionType), money(it.amount),
                            enumLabel("RecordStatus", if (it.reversed) "REVERSED" else "RECORDED"))
                    },
                    numeric = setOf(2),
                    trailing = { i -> if (!entries[i].reversed) TextButton(onClick = { reversing = entries[i] }) { Text(s("loan.reverse")) } },
                )
            }
        }
        if (entry.kind != null && chit != null) {
            MoneyEntryDialog(
                title = s("chit.entry.${entry.kind}"),
                entry = entry,
                accounts = { cashAccounts },
                onEdit = vm::editEntry,
                onSave = vm::submitEntry,
                onCancel = vm::closeEntry,
                cashOnly = true,
                info = if (entry.kind == "payout") s("chit.payoutEffect") else null,
            )
        }
        reversing?.let { line ->
            ConfirmDialog(
                s("loan.reverse"), "${Dates.formatIso(line.date)} · ${money(line.amount)}", s("chit.reverseEffect"), s("loan.reverse"),
                onConfirm = { reason -> vm.reverseEntry(line.transactionId, reason.orEmpty()); reversing = null },
                onCancel = { reversing = null }, reasonRequired = true,
            )
        }
        if (chit != null && action.confirming == "close") {
            ConfirmDialog(s("chit.close"), chit.chitName, s("chit.closeEffect"), s("chit.close"), { vm.close() }, vm::dismiss, busy = action.busy)
        }
    }
}

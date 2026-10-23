package com.nexora.desktop.ui.expenses

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.api.dto.ExpenseResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.expenses.ExpensesViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay

val EXPENSE_CATEGORIES = listOf("TRANSPORT", "ELECTRICITY", "RENT", "WORKER", "MAINTENANCE", "PACKING", "OTHER")

/** S-EXP-01 Expenses: summary (business and personal drawings shown apart), list, reversal. */
@Composable
fun ExpensesScreen(nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    val vm: ExpensesViewModel = screenViewModel()
    val state by vm.state.collectAsState()
    val summary by vm.summary.collectAsState()
    val reverseError by vm.reverseError.collectAsState()
    var reversing by remember { mutableStateOf<ExpenseResponse?>(null) }
    // F5 reloads the list (inside ListScreen) and the summary.
    ReloadOn(reloadTick, vm::loadSummary)
    ListScreen(
        title = s("nav.expenses"),
        vm = vm,
        columns = listOf(
            textColumn<ExpenseResponse>(s("common.number"), 1.2f) { it.expenseNumber },
            textColumn(s("common.date"), 1f, "expenseDate") { Dates.formatIso(it.expenseDate) },
            textColumn(s("expense.category"), 1f) { enumLabel("ExpenseCategory", it.category) },
            textColumn(s("expense.kind"), 1.1f) { enumLabel("ExpenseKind", it.expenseKind) },
            textColumn(s("expense.recurrence"), 0.9f) { enumLabel("Recurrence", it.recurrence) },
            textColumn(s("common.amount"), 1f, "amount", numeric = true) { money(it.amount) },
            textColumn(s("payment.account"), 1f) { it.account.accountName },
            textColumn(s("expense.description"), 1.6f) { it.description.orEmpty() },
            TableColumn(s("common.status"), 1.4f) { e ->
                Row(Modifier.weight(1.4f)) {
                    StatusChip("RecordStatus", e.status)
                    if (e.status == "RECORDED" && nav.can("EXPENSE_CREATE")) TextButton(onClick = { reversing = e }) { Text(s("expense.reverse")) }
                }
            },
        ),
        emptyMessage = s("expense.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        newLabel = s("overlay.RecordExpense"),
        onNew = { nav.show(Overlay.RecordExpense) },
        csvName = "expenses.csv",
        exportCsv = vm::csv,
        header = {
            reverseError?.let { ErrorBanner(it) }
            summary?.let { sm ->
                FlowRow {
                    // Two separate totals: business expenses and personal drawings are never added together (FR-FIN-12).
                    FigureTile(s("expense.businessTotal"), money(sm.businessTotal))
                    FigureTile(s("expense.personalTotal"), money(sm.personalDrawingsTotal))
                }
                FlowRow { sm.byCategory.forEach { LabeledValue(enumLabel("ExpenseCategory", it.category), money(it.total)) } }
                FlowRow { sm.byMonth.forEach { LabeledValue(it.month, s("expense.monthLine", money(it.businessTotal), money(it.personalTotal))) } }
            }
        },
        filters = {
            FilterDropdown(s("expense.category"), EXPENSE_CATEGORIES.map { it to enumLabel("ExpenseCategory", it) }, state.query.filters["category"]) {
                vm.setFilter("category", it)
            }
            FilterDropdown(s("expense.kind"), listOf("BUSINESS", "PERSONAL").map { it to enumLabel("ExpenseKind", it) }, state.query.filters["expenseKind"]) {
                vm.setFilter("expenseKind", it)
            }
            DateRangeFilter(state.query.filters, { key, value -> vm.setFilter(key, value); vm.loadSummary() })
        },
    )
    reversing?.let { e ->
        ConfirmDialog(
            s("expense.reverse"), "${e.expenseNumber} · ${money(e.amount)}", s("expense.reverseEffect"), s("expense.reverse"),
            onConfirm = { reason -> vm.reverse(e.id, reason.orEmpty()); reversing = null }, onCancel = { reversing = null }, reasonRequired = true,
        )
    }
}

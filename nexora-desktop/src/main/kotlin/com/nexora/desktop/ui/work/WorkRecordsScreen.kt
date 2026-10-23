package com.nexora.desktop.ui.work

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.api.dto.WorkRecordResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen
import com.nexora.desktop.viewmodel.work.WorkRecordsViewModel

/** S-WRK-01 Work records for the payroll week, with the server's total and reversal. */
@Composable
fun WorkRecordsScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: WorkRecordsViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    val total by vm.totalAmount.collectAsState()
    val reverseError by vm.reverseError.collectAsState()
    var reversing by remember { mutableStateOf<WorkRecordResponse?>(null) }
    var worker by remember { mutableStateOf<PickOption?>(null) }
    ListScreen(
        title = s("nav.work"),
        vm = vm,
        columns = listOf(
            textColumn<WorkRecordResponse>(s("common.date"), 1f, "workDate") { Dates.formatIso(it.workDate) },
            textColumn(s("work.worker"), 1.4f) { it.worker.name },
            textColumn(s("work.type"), 0.9f) { enumLabel("WorkType", it.workType) },
            textColumn(s("work.measure"), 1f, numeric = true) { r -> r.hours?.let(IndianFormat::hours) ?: IndianFormat.weight(r.quantityKg) },
            textColumn(s("common.rate"), 1f, numeric = true) { money(it.rate) },
            textColumn(s("common.amount"), 1f, "amount", numeric = true) { money(it.amount) },
            textColumn(s("work.batch"), 1f) { it.productionBatch?.batchNumber.orEmpty() },
            textColumn(s("order.payment"), 1f) { enumLabel("PaymentStatusView", it.paymentStatus) },
            TableColumn(s("common.status"), 1.4f) { r ->
                Row(Modifier.weight(1.4f)) {
                    StatusChip("RecordStatus", r.status)
                    if (vm.canReverse(r)) TextButton(onClick = { reversing = r }) { Text(s("expense.reverse")) }
                }
            },
        ),
        emptyMessage = s("work.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { r -> r.productionBatch?.let { nav.push(Screen.BatchDetail(it.id)) } },
        onBack = nav.backIfNested(),
        newLabel = s("overlay.RecordWork"),
        onNew = { nav.show(Overlay.RecordWork()) },
        extraActions = {
            val workerId = state.query.filters["workerId"]
            if (workerId != null && nav.can("PAYMENT_CREATE")) {
                OutlinedButton(onClick = { nav.show(Overlay.RecordPayment(workerId, "WORKER")) }) { Text(s("work.payWorker")) }
            }
        },
        header = {
            reverseError?.let { ErrorBanner(it) }
            total?.let { FigureTile(s("work.total"), money(it)) }
        },
        filters = {
            PartyPicker(s("work.worker"), "WORKER", worker, { worker = it; vm.setFilter("workerId", it.id) })
            FilterDropdown(s("work.type"), listOf("ROLLING", "WARPING").map { it to enumLabel("WorkType", it) }, state.query.filters["workType"]) {
                vm.setFilter("workType", it)
            }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
    reversing?.let { r ->
        ConfirmDialog(
            s("expense.reverse"), "${r.worker.name} · ${Dates.formatIso(r.workDate)} · ${money(r.amount)}", s("work.reverseEffect"), s("expense.reverse"),
            onConfirm = { reason -> vm.reverse(r.id, reason.orEmpty()); reversing = null }, onCancel = { reversing = null }, reasonRequired = true,
        )
    }
}

package com.nexora.desktop.ui.production

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.production.BatchDetailViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

/** S-PRD-03 Batch detail: start (confirmed), complete (reconciled), cancel, record work. */
@Composable
fun BatchDetailScreen(nav: NavigationViewModel, batchId: String, reloadTick: Int) {
    val vm: BatchDetailViewModel = screenViewModel(batchId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    val b = data.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(b?.batchNumber ?: s("common.loading"), onBack = nav::back) {
            if (b != null && nav.can("PRODUCTION_MANAGE")) {
                if (vm.canStart(b)) Button(onClick = { vm.ask("start") }) { Text(s("batch.start")) }
                if (vm.canComplete(b)) Button(onClick = { vm.ask("complete") }) { Text(s("batch.complete")) }
                if (vm.canStart(b)) OutlinedButton(onClick = { nav.show(Overlay.BatchForm(b.id)) }) { Text(s("common.edit")) }
                if (vm.canCancel(b)) OutlinedButton(onClick = { vm.ask("cancel") }) { Text(s("batch.cancel")) }
            }
            if (b != null && vm.canRecordWork(b) && nav.can("WORK_RECORD_MANAGE")) {
                OutlinedButton(onClick = { nav.show(Overlay.RecordWork(b.id, b.batchNumber)) }) { Text(s("batch.recordWork")) }
            }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
        if (b == null) return@Column
        FlowRow {
            Column(Modifier.padding(end = 24.dp)) { StatusChip("BatchStatus", b.status) }
            LabeledValue(s("batch.warpType"), enumLabel("ProductType", b.productType))
            LabeledValue(s("batch.date"), Dates.formatIso(b.productionDate))
            LabeledValue(s("batch.inputShort"), weight(b.inputWeightKg))
            LabeledValue(s("batch.output"), weight(b.outputWeightKg))
            LabeledValue(s("batch.wastage"), weight(b.wastageWeightKg))
            LabeledValue(s("batch.discrepancy"), weight(b.discrepancyWeightKg))
            b.wastagePercent?.let { LabeledValue(s("batch.wastagePercent"), s("loan.percent", it.toPlainString())) }
            b.consumptionMovementNumber?.let { LabeledValue(s("batch.movement"), it) }
            b.notes?.let { LabeledValue(s("common.notes"), it) }
        }
        Section(s("batch.orderLinks")) {
            val orderItems = b.orderItems.orEmpty()
            StaticTable(
                listOf(s("order.number"), s("batch.allocated")),
                orderItems.map { listOf(s("payment.orderLine", it.orderNumber.orEmpty(), it.lineNo ?: 0), weight(it.allocatedWeightKg)) },
                numeric = setOf(1),
                onRowClick = { i -> orderItems.getOrNull(i)?.orderId?.let { nav.push(Screen.OrderDetail(it)) } },
            )
        }
        Section(s("batch.workRecords")) {
            StaticTable(
                listOf(s("work.worker"), s("work.type"), s("common.amount")),
                b.workRecords.orEmpty().map { listOf(it.workerName, enumLabel("WorkType", it.workType), money(it.amount)) },
                numeric = setOf(2),
            )
        }
    }
    if (b == null) return
    when (action.confirming) {
        "start" -> ConfirmDialog(
            s("batch.start"), b.batchNumber, s("batch.startEffect", weight(b.inputWeightKg)), s("batch.start"),
            onConfirm = { vm.start() }, onCancel = vm::dismiss, busy = action.busy,
        )
        "cancel" -> ConfirmDialog(
            s("batch.cancel"), b.batchNumber, s("batch.cancelEffect"), s("batch.cancel"),
            onConfirm = { vm.cancel(it.orEmpty()) }, onCancel = vm::dismiss, reasonRequired = true, busy = action.busy,
        )
        "complete" -> CompleteBatchDialog(b, onSaved = { vm.showUpdated(it); vm.dismiss() }, onCancel = vm::dismiss)
    }
}

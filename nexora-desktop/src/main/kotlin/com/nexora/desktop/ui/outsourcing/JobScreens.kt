package com.nexora.desktop.ui.outsourcing

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.JobResponse
import com.nexora.desktop.ui.components.ConfirmDialog
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.ui.production.OrderLinksEditor
import com.nexora.desktop.ui.production.WARP_TYPES
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.IndianFormat.rate
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.outsourcing.JobDetailViewModel
import com.nexora.desktop.viewmodel.outsourcing.JobListViewModel
import com.nexora.desktop.viewmodel.outsourcing.NewJobViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen
import org.koin.compose.koinInject
import java.time.Clock

/** S-OUT-01 Outsourcing jobs. */
@Composable
fun JobListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: JobListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.outsourcing"),
        vm = vm,
        columns = listOf(
            textColumn<JobResponse>(s("common.number"), 1.1f, "jobNumber") { it.jobNumber },
            textColumn(s("job.manufacturer"), 1.4f) { it.manufacturer.name },
            textColumn(s("batch.warpType"), 1f) { enumLabel("ProductType", it.productType) },
            textColumn(s("job.issued"), 1f, numeric = true) { weight(it.issuedWeightKg) },
            textColumn(s("job.receivedKg"), 1f, numeric = true) { weight(it.receivedWeightKg) },
            textColumn(s("job.wastage"), 1f, numeric = true) { weight(it.wastageWeightKg) },
            textColumn(s("job.outside"), 1f, numeric = true) { weight(it.outsideWeightKg) },
            textColumn(s("common.rate"), 1f, numeric = true) { money(it.ratePerKg) },
            textColumn(s("purchase.payable"), 1.1f, numeric = true) { money(it.payableAmount) },
            TableColumn(s("common.status"), 1.2f) { j -> Row(Modifier.weight(1.2f)) { StatusChip("JobStatus", j.status) } },
        ),
        emptyMessage = s("job.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.JobDetail(it.id)) },
        onBack = nav.backIfNested(),
        newLabel = s("job.new"),
        onNew = { nav.show(Overlay.NewJob) },
        filters = {
            FilterDropdown(
                s("common.status"),
                listOf("CREATED", "MATERIAL_ISSUED", "PARTIALLY_RECEIVED", "COMPLETED", "CANCELLED").map { it to enumLabel("JobStatus", it) },
                state.query.filters["status"],
            ) { vm.setFilter("status", it) }
            DateRangeFilter(state.query.filters, vm::setFilter)
        },
    )
}

/** S-OUT-02 New job. */
@Composable
fun NewJobPanel(nav: NavigationViewModel) {
    val vm: NewJobViewModel = screenViewModel()
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let { nav.closeOverlay(); nav.push(Screen.JobDetail(it.id)) } }
    FormPanel(s("job.new"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        PartyPicker(s("job.manufacturer"), "MANUFACTURER", form.manufacturer, { v -> vm.edit { it.copy(manufacturer = v) } }, error = errors["manufacturerId"])
        Segmented(s("job.warpExpected"), WARP_TYPES.map { it to enumLabel("ProductType", it) }, form.productType, { v -> vm.edit { it.copy(productType = v, links = emptyList()) } })
        WeightField(s("job.silkToIssue"), form.issued, { v -> vm.edit { it.copy(issued = v) } }, error = errors["issuedWeightKg"])
        MoneyField(s("purchase.rate"), form.rate, { v -> vm.edit { it.copy(rate = v) } }, error = errors["ratePerKg"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, singleLine = false, maxLength = 1000)
        OrderLinksEditor(form.productType, form.links, errors, vm::addLink, vm::removeLink, vm::updateLink)
    }
}

/** S-OUT-03 Job detail: issue (confirmed), receive warp, cancel, pay. */
@Composable
fun JobDetailScreen(nav: NavigationViewModel, jobId: String, reloadTick: Int) {
    val vm: JobDetailViewModel = screenViewModel(jobId)
    val data by vm.data.collectAsState()
    val action by vm.action.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    val j = data.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(j?.jobNumber ?: s("common.loading"), onBack = nav::back) {
            if (j != null) {
                if (vm.canIssue(j)) Button(onClick = { vm.ask("issue") }) { Text(s("job.issue")) }
                if (vm.canReceive(j)) Button(onClick = { vm.ask("receive") }) { Text(s("job.receive")) }
                if (vm.canCancel(j)) OutlinedButton(onClick = { vm.ask("cancel") }) { Text(s("job.cancel")) }
                if (vm.canPay(j) && nav.can("PAYMENT_CREATE")) {
                    OutlinedButton(onClick = { nav.show(Overlay.RecordPayment(j.manufacturer.id, "MANUFACTURER")) }) { Text(s("job.pay")) }
                }
            }
        }
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        action.error?.let { ErrorBanner(it, onDismiss = vm::dismiss) }
        if (j == null) return@Column
        FlowRow {
            Column(Modifier.padding(end = 24.dp)) { StatusChip("JobStatus", j.status) }
            LabeledValue(s("job.manufacturer"), j.manufacturer.name)
            LabeledValue(s("job.warpExpected"), enumLabel("ProductType", j.productType))
            LabeledValue(s("job.issued"), weight(j.issuedWeightKg))
            LabeledValue(s("job.receivedKg"), weight(j.receivedWeightKg))
            LabeledValue(s("job.wastage"), weight(j.wastageWeightKg))
            LabeledValue(s("job.outside"), weight(j.outsideWeightKg))
            LabeledValue(s("common.rate"), rate(j.ratePerKg, s("unit.kg")))
            LabeledValue(s("purchase.payable"), money(j.payableAmount))
            LabeledValue(s("common.allocated"), money(j.allocatedAmount))
            LabeledValue(s("common.outstanding"), money(j.outstandingAmount))
            LabeledValue(s("job.issueDate"), Dates.formatIso(j.issueDate))
        }
        Section(s("job.receipts")) {
            StaticTable(
                listOf(s("common.date"), s("job.receivedKg"), s("job.wastage")),
                j.receipts.orEmpty().map { listOf(Dates.formatIso(it.receiptDate), weight(it.receivedWeightKg), weight(it.wastageWeightKg)) },
                numeric = setOf(1, 2),
            )
        }
    }
    if (j == null) return
    when (action.confirming) {
        "issue" -> {
            val clock: Clock = koinInject()
            var date by remember { mutableStateOf(Dates.format(Dates.today(clock))) }
            ConfirmDialog(
                s("job.issue"), j.jobNumber, s("job.issueEffect", weight(j.issuedWeightKg), j.manufacturer.name), s("job.issue"),
                onConfirm = { vm.issue(date) }, onCancel = vm::dismiss, busy = action.busy,
                extra = { DateField(s("job.issueDate"), date, { date = it }) },
            )
        }
        "cancel" -> ConfirmDialog(
            s("job.cancel"), j.jobNumber, s("job.cancelEffect"), s("job.cancel"),
            onConfirm = { vm.cancel(it.orEmpty()) }, onCancel = vm::dismiss, reasonRequired = true, busy = action.busy,
        )
        "receive" -> ReceiveWarpDialog(j, onSaved = { vm.showUpdated(it); vm.dismiss() }, onCancel = vm::dismiss)
    }
}

package com.nexora.desktop.ui.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.EntityPicker
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.production.BatchFormViewModel
import com.nexora.desktop.viewmodel.production.LinkRow
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen
import org.koin.compose.koinInject

val WARP_TYPES = listOf("SILK_WARP", "KORA_WARP")

/** S-PRD-02 Batch form (new, or edit while Planned). */
@Composable
fun BatchFormPanel(nav: NavigationViewModel, batchId: String?) {
    val vm: BatchFormViewModel = screenViewModel(batchId)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val rawSilk by vm.rawSilkAvailable.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) {
        status.result?.let { nav.closeOverlay(); if (vm.isEdit) nav.reload() else nav.push(Screen.BatchDetail(it.id)) }
    }
    FormPanel(if (vm.isEdit) s("batch.editTitle") else s("batch.new"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, nav::closeOverlay) {
        Segmented(s("batch.warpType"), WARP_TYPES.map { it to enumLabel("ProductType", it) }, form.productType, { v -> vm.edit { it.copy(productType = v, links = emptyList()) } }, enabled = !vm.isEdit)
        DateField(s("batch.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["productionDate"])
        WeightField(s("batch.input"), form.input, { v -> vm.edit { it.copy(input = v) } }, error = errors["inputWeightKg"])
        // Information only, from the stock summary.
        rawSilk?.let { Text(s("batch.rawSilkAvailable", weight(it))) }
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, singleLine = false, maxLength = 1000)
        OrderLinksEditor(form.productType, form.links, errors, vm::addLink, vm::removeLink, vm::updateLink)
    }
}

/** Links of a batch or job to order items (item picker + allocated kg); used by batches and outsourcing jobs. */
@Composable
fun OrderLinksEditor(
    productType: String,
    links: List<LinkRow>,
    errors: Map<String, String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onUpdate: (Int, (LinkRow) -> LinkRow) -> Unit,
) {
    val orders: OrderRepository = koinInject()
    Section(s("batch.orderLinks")) {
        links.forEachIndexed { index, link ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                EntityPicker(
                    s("batch.orderItem"), link.item, { text -> orders.pickItems(productType, text) }, { option -> onUpdate(index) { it.copy(item = option) } },
                    Modifier.width(260.dp), errors["orderItems[$index].orderItemId"],
                )
                WeightField(s("batch.allocated"), link.weight, { v -> onUpdate(index) { it.copy(weight = v) } }, Modifier.width(150.dp), errors["orderItems[$index].allocatedWeightKg"])
                TextButton(onClick = { onRemove(index) }) { Text(s("common.remove")) }
            }
        }
        TextButton(onClick = onAdd) { Text(s("batch.addLink")) }
    }
}

package com.nexora.desktop.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.orders.OrderFormViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

private val ORDER_PRODUCTS = listOf("SILK_WARP", "KORA_WARP", "RAW_SILK", "VUDA_WARP")

/** S-ORD-02 Order form. Ctrl+Enter adds an item line, Ctrl+Delete removes the focused one. */
@Composable
fun OrderFormPanel(nav: NavigationViewModel, orderId: String?) {
    val vm: OrderFormViewModel = screenViewModel(orderId)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    val readOnly = form.readOnlyReason != null
    LaunchedEffect(status.result) {
        status.result?.let {
            nav.closeOverlay()
            if (vm.isEdit) nav.reload() else nav.push(Screen.OrderDetail(it.id))
        }
    }
    FormPanel(
        title = if (vm.isEdit) s("order.editTitle") else s("order.new"),
        saveLabel = s("common.save"),
        submitting = status.submitting,
        dirty = status.dirty,
        error = status.error,
        onSave = vm::save,
        onCancel = nav::closeOverlay,
        footerNote = s("order.totalLine", vm.totalText(form)),
    ) {
        form.readOnlyReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PartyPicker(s("order.customer"), "CUSTOMER", form.customer, { v -> vm.edit { it.copy(customer = v) } }, error = errors["customerId"], enabled = !vm.isEdit && !readOnly)
        DateField(s("order.date"), form.orderDate, { v -> if (!vm.isEdit) vm.edit { it.copy(orderDate = v) } }, error = errors["orderDate"])
        DateField(s("order.expectedDate"), form.requiredDate, { v -> if (!readOnly) vm.edit { it.copy(requiredDate = v) } }, error = errors["requiredDate"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, error = errors["notes"], singleLine = false, maxLength = 1000, enabled = !readOnly)
        Section(s("order.items")) {
            form.items.forEachIndexed { index, row ->
                Column(
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed || readOnly) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter -> { vm.addItem(); true }
                            Key.Delete -> { vm.removeItem(index); true }
                            else -> false
                        }
                    },
                ) {
                    Text(s("order.line", index + 1), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Dropdown(s("purchase.product"), ORDER_PRODUCTS.map { it to enumLabel("ProductType", it) }, row.productType, { v ->
                            vm.updateItem(index) { it.copy(productType = v) }
                        }, Modifier.width(150.dp))
                        WeightField(s("order.requiredWeight"), row.weight, { v -> vm.updateItem(index) { it.copy(weight = v) } },
                            Modifier.width(160.dp), errors["items[$index].requiredWeightKg"], enabled = !readOnly)
                        MoneyField(s("purchase.rate"), row.rate, { v -> vm.updateItem(index) { it.copy(rate = v) } },
                            Modifier.width(150.dp), errors["items[$index].ratePerKg"], enabled = !readOnly)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextInput(s("order.warpSpec"), row.warpSpecification, { v -> vm.updateItem(index) { it.copy(warpSpecification = v) } },
                            Modifier.width(300.dp), errors["items[$index].warpSpecification"], maxLength = 500, enabled = !readOnly)
                        // Amount is read-only: the server's figure, or "calculated on save".
                        Text(s("order.amount", vm.amountText(row)))
                        if (!readOnly && form.items.size > 1) TextButton(onClick = { vm.removeItem(index) }) { Text(s("common.remove")) }
                    }
                }
            }
            if (!readOnly) TextButton(onClick = vm::addItem) { Text(s("order.addItem")) }
            errors["items"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

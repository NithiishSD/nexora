package com.nexora.desktop.ui.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.CustomerReturnResponse
import com.nexora.desktop.api.dto.OrderResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.orders.RecordCustomerReturnViewModel

/** Dialog to record a customer return against delivered order items. */
@Composable
fun RecordCustomerReturnDialog(
    order: OrderResponse,
    onSaved: (CustomerReturnResponse) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: RecordCustomerReturnViewModel = screenViewModel(order.id)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors

    LaunchedEffect(order.items) {
        if (form.orderItemId.isEmpty() && order.items.isNotEmpty()) {
            vm.edit { it.copy(orderItemId = order.items.first().id) }
        }
    }

    LaunchedEffect(status.result) {
        status.result?.let(onSaved)
    }

    val itemOptions = order.items.map { item ->
        item.id to "${item.lineNo}. ${enumLabel("ProductType", item.productType)} (${weight(item.requiredWeightKg)})"
    }

    FormDialog(
        title = s("order.returnTitle", order.orderNumber),
        saveLabel = s("common.save"),
        submitting = status.submitting,
        error = status.error,
        onSave = vm::save,
        onCancel = onCancel,
    ) {
        Dropdown(
            label = s("order.returnItem"),
            options = itemOptions,
            selected = form.orderItemId,
            onSelect = { id -> vm.edit { it.copy(orderItemId = id) } },
            error = errors["orderItemId"],
        )
        DateField(
            label = s("order.returnDate"),
            value = form.returnDate,
            onValueChange = { v -> vm.edit { it.copy(returnDate = v) } },
            error = errors["returnDate"],
        )
        WeightField(
            label = s("order.returnWeight"),
            value = form.weight,
            onValueChange = { v -> vm.edit { it.copy(weight = v) } },
            error = errors["returnedWeightKg"],
        )
        Segmented(
            label = s("order.stockRouting"),
            options = listOf(
                true to s("order.usable"),
                false to s("order.damaged"),
            ),
            selected = form.usable,
            onSelect = { v -> vm.edit { it.copy(usable = v) } },
        )
        TextInput(
            label = s("common.reason"),
            value = form.reason,
            onValueChange = { v -> vm.edit { it.copy(reason = v) } },
            error = errors["reason"],
            maxLength = 255,
        )
        TextInput(
            label = s("common.notes"),
            value = form.notes,
            onValueChange = { v -> vm.edit { it.copy(notes = v) } },
            error = errors["notes"],
            maxLength = 1000,
        )
    }
}

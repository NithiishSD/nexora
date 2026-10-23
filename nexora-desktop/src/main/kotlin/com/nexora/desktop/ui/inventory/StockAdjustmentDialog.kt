package com.nexora.desktop.ui.inventory

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.inventory.StockAdjustmentViewModel

private val PRODUCTS = listOf("RAW_SILK", "SILK_WARP", "KORA_WARP", "VUDA_WARP")

/**
 * S-INV-04 Record wastage and S-INV-05 Stock adjustment share one dialog; [wastage] picks which.
 * The adjustment shows the warning of 06: use only after a physical count, stock never goes below zero.
 */
@Composable
fun StockChangeDialog(wastage: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: StockAdjustmentViewModel = screenViewModel(wastage)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { if (status.result != null) onDone() }
    FormDialog(
        if (wastage) s("inventory.recordWastage") else s("inventory.adjust"),
        s("common.save"), status.submitting, status.error, vm::save, onCancel,
    ) {
        if (!wastage) Text(s("inventory.adjustWarning"), color = MaterialTheme.colorScheme.error)
        Dropdown(s("purchase.product"), PRODUCTS.map { it to enumLabel("ProductType", it) }, form.productType, vm::setProduct)
        Dropdown(
            if (wastage) s("inventory.source") else s("inventory.from"),
            vm.sourcesFor(form.productType).map { it to enumLabel("StockLocation", it) },
            form.fromLocation,
            { v -> vm.edit { it.copy(fromLocation = v) } },
        )
        if (!wastage) {
            Dropdown(
                s("inventory.to"),
                StockAdjustmentViewModel.ALL_LOCATIONS.map { it to enumLabel("StockLocation", it) },
                form.toLocation.ifEmpty { null },
                { v -> vm.edit { it.copy(toLocation = v) } },
                error = errors["toLocation"],
            )
        }
        WeightField(s("inventory.quantity"), form.weight, { v -> vm.edit { it.copy(weight = v) } }, error = errors["quantityKg"])
        DateField(s("common.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["movementDate"])
        TextInput(s("common.reason"), form.reason, { v -> vm.edit { it.copy(reason = v) } }, error = errors["reason"], maxLength = 255)
        if (!wastage) TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 1000)
    }
}

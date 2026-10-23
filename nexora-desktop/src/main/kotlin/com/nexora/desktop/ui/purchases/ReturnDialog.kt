package com.nexora.desktop.ui.purchases

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.purchases.ReturnDialogViewModel

/** S-PUR-05 Return to supplier. Shows accepted and already-returned kg as the server reported them. */
@Composable
fun ReturnDialog(purchase: PurchaseResponse, onSaved: (PurchaseResponse) -> Unit, onCancel: () -> Unit) {
    val vm: ReturnDialogViewModel = screenViewModel(purchase.id)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let(onSaved) }
    FormDialog(s("purchase.returnTitle", purchase.purchaseNumber), s("common.save"), status.submitting, status.error, vm::save, onCancel) {
        Text(s("purchase.acceptedReturned", weight(purchase.acceptedWeightKg), weight(purchase.returnedWeightKg)))
        DateField(s("purchase.returnDate"), form.returnDate, { v -> vm.edit { it.copy(returnDate = v) } }, error = errors["returnDate"])
        WeightField(s("purchase.returnWeight"), form.weight, { v -> vm.edit { it.copy(weight = v) } }, error = errors["returnedWeightKg"])
        TextInput(s("common.reason"), form.reason, { v -> vm.edit { it.copy(reason = v) } }, error = errors["reason"], maxLength = 255)
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, error = errors["notes"], maxLength = 1000)
    }
}

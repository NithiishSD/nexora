package com.nexora.desktop.ui.purchases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.EntityPicker
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.purchases.ReceiveSilkViewModel
import org.koin.compose.koinInject

/**
 * S-PUR-04 Receive silk dialog. Keyboard-only: Tab through the fields, Enter saves (Ctrl+S too), Esc cancels.
 * [onSaved] gets the purchase as the server returned it after the receipt.
 */
@Composable
fun ReceiveSilkDialog(purchaseId: String, onSaved: (PurchaseResponse) -> Unit, onCancel: () -> Unit) {
    val vm: ReceiveSilkViewModel = screenViewModel(purchaseId)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val purchase by vm.purchase.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let(onSaved) }
    FormDialog(s("purchase.receiveTitle", purchase?.purchaseNumber.orEmpty()), s("common.save"), status.submitting, status.error, vm::save, onCancel) {
        // The remaining weight is the server's figure (remainingWeightKg), not computed here.
        purchase?.let { Text(s("purchase.remainingToReceive", weight(it.remainingWeightKg)), style = MaterialTheme.typography.titleMedium) }
        DateField(s("purchase.receiptDate"), form.receiptDate, { v -> vm.edit { it.copy(receiptDate = v) } }, error = errors["receiptDate"], tag = "receipt.date")
        WeightField(s("purchase.receivedWeight"), form.received, vm::setReceived, error = errors["receivedWeightKg"], tag = "receipt.received")
        WeightField(s("purchase.acceptedWeight"), form.accepted, vm::setAccepted, error = errors["acceptedWeightKg"], tag = "receipt.accepted")
        WeightField(s("purchase.rejectedWeight"), form.rejected, vm::setRejected, error = errors["rejectedWeightKg"], tag = "receipt.rejected")
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, error = errors["notes"], maxLength = 1000)
    }
}

/** Ctrl+Shift+R: pick an open purchase first, then receive against it. */
@Composable
fun ReceiveSilkPickerPanel(initialPurchaseId: String?, onSaved: (PurchaseResponse) -> Unit, onCancel: () -> Unit) {
    val purchases: PurchaseRepository = koinInject()
    var picked by remember { mutableStateOf<PickOption?>(null) }
    val purchaseId = initialPurchaseId ?: picked?.id
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(s("overlay.ReceiveSilk"), style = MaterialTheme.typography.titleLarge)
        EntityPicker(s("purchase.pickOpen"), picked, { purchases.pickOpen(it) }, { picked = it }, tag = "receipt.purchase")
    }
    if (purchaseId != null) ReceiveSilkDialog(purchaseId, onSaved, onCancel = { if (initialPurchaseId == null) picked = null else onCancel() })
}

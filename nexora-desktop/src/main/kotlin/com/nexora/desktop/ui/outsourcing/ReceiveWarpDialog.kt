package com.nexora.desktop.ui.outsourcing

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.JobResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.outsourcing.ReceiveWarpViewModel

/** S-OUT-03 Receive warp dialog; "Still outside" is the server's figure. */
@Composable
fun ReceiveWarpDialog(job: JobResponse, onSaved: (JobResponse) -> Unit, onCancel: () -> Unit) {
    val vm: ReceiveWarpViewModel = screenViewModel(job.id, job.outsideWeightKg)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let(onSaved) }
    FormDialog(s("job.receive"), s("common.save"), status.submitting, status.error, vm::save, onCancel) {
        Text(s("job.stillOutside", weight(job.outsideWeightKg)))
        DateField(s("purchase.receiptDate"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["receiptDate"])
        WeightField(s("job.receivedWeight"), form.received, { v -> vm.edit { it.copy(received = v) } }, error = errors["receivedWeightKg"])
        WeightField(s("job.wastageWeight"), form.wastage, { v -> vm.edit { it.copy(wastage = v) } }, error = errors["wastageWeightKg"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 1000)
    }
}

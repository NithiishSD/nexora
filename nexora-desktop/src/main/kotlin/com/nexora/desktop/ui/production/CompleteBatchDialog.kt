package com.nexora.desktop.ui.production

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.FieldError
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.production.CompleteBatchViewModel

/** S-PRD-03 Complete dialog: "Input x kg = output + wastage + discrepancy". */
@Composable
fun CompleteBatchDialog(batch: BatchResponse, onSaved: (BatchResponse) -> Unit, onCancel: () -> Unit) {
    val vm: CompleteBatchViewModel = screenViewModel(batch.id, batch.inputWeightKg)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val errors = status.fieldErrors
    LaunchedEffect(status.result) { status.result?.let(onSaved) }
    FormDialog(s("batch.complete"), s("common.save"), status.submitting, status.error, { if (vm.saveEnabled(form)) vm.save() }, onCancel) {
        Text(s("batch.reconcileRule", weight(batch.inputWeightKg)))
        WeightField(s("batch.output"), form.output, { v -> vm.edit { it.copy(output = v) } }, error = errors["outputWeightKg"], tag = "complete.output")
        WeightField(s("batch.wastage"), form.wastage, { v -> vm.edit { it.copy(wastage = v) } }, error = errors["wastageWeightKg"], tag = "complete.wastage")
        WeightField(s("batch.discrepancy"), form.discrepancy, { v -> vm.edit { it.copy(discrepancy = v) } }, error = errors["discrepancyWeightKg"], tag = "complete.discrepancy")
        DateField(s("common.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["completionDate"])
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 1000)
        if (!vm.saveEnabled(form)) FieldError(s("batch.notReconciled"))
    }
}

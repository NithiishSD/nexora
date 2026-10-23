package com.nexora.desktop.ui.work

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.ui.components.DateField
import com.nexora.desktop.ui.components.EntityPicker
import com.nexora.desktop.ui.components.FormPanel
import com.nexora.desktop.ui.components.HoursField
import com.nexora.desktop.ui.components.MoneyField
import com.nexora.desktop.ui.components.PartyPicker
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.money
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.work.RecordWorkViewModel
import org.koin.compose.koinInject

/**
 * S-WRK-02 Record work. After each save the form stays open with the worker kept and the measure cleared,
 * and a line shows what the server saved (amount computed by the server).
 */
@Composable
fun RecordWorkPanel(batch: PickOption?, onClose: () -> Unit) {
    val vm: RecordWorkViewModel = screenViewModel(batch)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val lastSaved by vm.lastSaved.collectAsState()
    vm.worker.collectAsState()
    val batches: ProductionRepository = koinInject()
    val errors = status.fieldErrors
    FormPanel(s("overlay.RecordWork"), s("common.save"), status.submitting, status.dirty, status.error, vm::save, onClose) {
        lastSaved?.let {
            Text(s("work.savedLine", it.worker.name, money(it.amount)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        PartyPicker(s("work.worker"), "WORKER", form.worker, vm::pickWorker, error = errors["workerId"], tag = "work.worker")
        Segmented(s("work.type"), listOf("ROLLING", "WARPING").map { it to enumLabel("WorkType", it) }, form.workType, vm::setWorkType)
        if (vm.showsHours(form)) {
            HoursField(s("work.hours"), form.hours, { v -> vm.edit { it.copy(hours = v) } }, error = errors["hours"], tag = "work.hours")
        } else {
            WeightField(s("work.quantity"), form.quantityKg, { v -> vm.edit { it.copy(quantityKg = v) } }, error = errors["quantityKg"], tag = "work.kg")
        }
        MoneyField(s("work.rate"), form.rate, { v -> vm.edit { it.copy(rate = v) } }, error = errors["rate"])
        vm.defaultRateHint()?.let { Text(s("work.defaultRate", it), style = MaterialTheme.typography.bodySmall) }
        DateField(s("work.date"), form.date, { v -> vm.edit { it.copy(date = v) } }, error = errors["workDate"])
        EntityPicker(s("work.batch"), form.batch, { batches.pickForWork(it) }, { v -> vm.edit { it.copy(batch = v) } })
        TextInput(s("common.notes"), form.notes, { v -> vm.edit { it.copy(notes = v) } }, maxLength = 500)
    }
}

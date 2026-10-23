package com.nexora.desktop.viewmodel.work

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.PartyResponse
import com.nexora.desktop.api.dto.WorkRecordRequest
import com.nexora.desktop.api.dto.WorkRecordResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.WorkRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

data class WorkForm(
    val worker: PickOption? = null,
    val workType: String = "ROLLING",
    val hours: String = "",
    val quantityKg: String = "",
    val rate: String = "",
    val date: String = "",
    val batch: PickOption? = null,
    val notes: String = "",
)

/**
 * S-WRK-02 Record work (FR-WORK-01..-03, -08, NFR-USE-01). Rolling asks for hours, warping for kg - never both.
 * An empty rate means "use the worker's default rate"; the amount is the server's (round(units x rate, 2)).
 * After a save the worker stays selected and the measure is cleared, for quick weekly entry.
 */
class RecordWorkViewModel(
    private val work: WorkRepository,
    private val parties: PartyRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    prefillBatch: PickOption? = null,
) : FormViewModel<WorkForm, WorkRecordResponse>(WorkForm()) {

    private val _worker = MutableStateFlow<PartyResponse?>(null)

    /** The selected worker's record, for showing the default rates as a hint. */
    val worker: StateFlow<PartyResponse?> = _worker.asStateFlow()

    /** The last record saved in this session ("Saved" confirmation while the form stays open). */
    private val _lastSaved = MutableStateFlow<WorkRecordResponse?>(null)
    val lastSaved: StateFlow<WorkRecordResponse?> = _lastSaved.asStateFlow()

    init {
        preset { it.copy(date = Dates.format(Dates.today(clock)), batch = prefillBatch) }
        // Worker defaults to the last one used on this computer.
        settings.lastUsed("work.worker")?.let { id ->
            viewModelScope.launch { parties.get(id).getOrNull()?.takeIf { it.status == "ACTIVE" }?.let { selectWorker(it) } }
        }
    }

    fun pickWorker(option: PickOption) {
        edit { it.copy(worker = option) }
        viewModelScope.launch { parties.get(option.id).getOrNull()?.let(::selectWorker) }
    }

    private fun selectWorker(p: PartyResponse) {
        _worker.value = p
        // Work type defaults to the one last used for this worker.
        val type = settings.lastUsed("work.type.${p.id}") ?: form.value.workType
        preset { it.copy(worker = PickOption(p.id, p.name, p.partyCode), workType = type) }
    }

    /** Switching type clears the other measure: hours are only for rolling, kg only for warping. */
    fun setWorkType(type: String) = edit { it.copy(workType = type, hours = "", quantityKg = "") }

    /** Which measure field the form shows. */
    fun showsHours(form: WorkForm) = form.workType == "ROLLING"

    fun defaultRateHint(): String? {
        val p = _worker.value ?: return null
        val rate = if (form.value.workType == "ROLLING") p.defaultRollingRatePerHour else p.defaultWarpingRatePerKg
        return rate?.let { com.nexora.desktop.util.IndianFormat.rate(it, if (form.value.workType == "ROLLING") s("unit.hour") else s("unit.kg")) }
    }

    override fun validate(form: WorkForm): Map<String, String> = Checks.collect(
        "workerId" to if (form.worker == null) s("validation.required") else null,
        "hours" to if (showsHours(form)) Checks.positive(form.hours) else null,
        "quantityKg" to if (!showsHours(form)) Checks.positive(form.quantityKg) else null,
        "rate" to form.rate.takeIf { it.isNotBlank() }?.let { Checks.zeroOrMore(it) },
        "workDate" to Checks.date(form.date, clock),
    )

    override suspend fun send(form: WorkForm, idempotencyKey: String): ApiResult<WorkRecordResponse> = work.record(
        WorkRecordRequest(
            workerId = form.worker!!.id,
            workDate = Dates.toIso(form.date, clock)!!,
            workType = form.workType,
            hours = if (showsHours(form)) form.hours.toDecimalOrNull() else null,
            quantityKg = if (!showsHours(form)) form.quantityKg.toDecimalOrNull() else null,
            rate = form.rate.toDecimalOrNull(),
            productionBatchId = form.batch?.id,
            notes = form.notes.trim().ifEmpty { null },
        ),
    )

    override fun afterSuccess(result: WorkRecordResponse) {
        settings.remember("work.worker", result.worker.id)
        settings.remember("work.type.${result.worker.id}", result.workType)
        _lastSaved.value = result
        // "Record another": keep worker, type, date and batch; clear the measure and notes.
        clearResult()
        preset { it.copy(hours = "", quantityKg = "", notes = "") }
    }
}

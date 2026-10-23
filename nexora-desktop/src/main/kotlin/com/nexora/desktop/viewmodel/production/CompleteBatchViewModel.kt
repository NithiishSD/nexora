package com.nexora.desktop.viewmodel.production

import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.api.dto.CompleteBatchRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.math.BigDecimal
import java.time.Clock

data class CompleteForm(val output: String = "", val wastage: String = "", val discrepancy: String = "", val date: String = "", val notes: String = "")

/**
 * S-PRD-03 Complete dialog (FR-PROD-03). Save is enabled only when output + wastage + discrepancy equals the input
 * weight exactly. This is an input-consistency check equal to the API rule PRODUCTION_NOT_RECONCILED (06 section 11
 * #4): nothing is calculated for display, and the API checks it again.
 */
class CompleteBatchViewModel(
    private val repository: ProductionRepository,
    private val clock: Clock,
    val batchId: String,
    private val inputWeightKg: BigDecimal,
) : FormViewModel<CompleteForm, BatchResponse>(CompleteForm(date = Dates.format(Dates.today(clock)))) {

    fun saveEnabled(form: CompleteForm): Boolean {
        val parts = listOf(form.output, form.wastage, form.discrepancy).map { it.toDecimalOrNull() ?: return false }
        // compareTo, not equals: 100.0 and 100.000 are the same weight.
        return parts.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(inputWeightKg) == 0
    }

    override fun validate(form: CompleteForm): Map<String, String> = Checks.collect(
        "outputWeightKg" to Checks.zeroOrMore(form.output),
        "wastageWeightKg" to Checks.zeroOrMore(form.wastage),
        "discrepancyWeightKg" to Checks.zeroOrMore(form.discrepancy),
        "completionDate" to Checks.date(form.date, clock),
        "form" to if (saveEnabled(form)) null else com.nexora.desktop.util.s("error.PRODUCTION_NOT_RECONCILED"),
    )

    override suspend fun send(form: CompleteForm, idempotencyKey: String): ApiResult<BatchResponse> = repository.complete(
        batchId,
        CompleteBatchRequest(form.output.decimal(), form.wastage.decimal(), form.discrepancy.decimal(), Dates.toIso(form.date, clock)!!, form.notes.trim().ifEmpty { null }),
    )
}

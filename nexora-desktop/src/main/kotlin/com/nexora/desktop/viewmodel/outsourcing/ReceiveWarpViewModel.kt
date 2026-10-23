package com.nexora.desktop.viewmodel.outsourcing

import com.nexora.desktop.api.dto.JobReceiveRequest
import com.nexora.desktop.api.dto.JobResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.OutsourcingRepository
import com.nexora.desktop.ui.components.ErrorMessages
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.math.BigDecimal
import java.time.Clock

data class ReceiveWarpForm(val date: String = "", val received: String = "", val wastage: String = "0.000", val notes: String = "")

/**
 * S-OUT-03 Receive warp dialog (FR-OUT-03); idempotent. Received + wastage may not exceed the silk still outside
 * (the job's `outsideWeightKg` from the API). That is an input check; the server's OUTSOURCE_RECEIPT_EXCEEDED is
 * shown at the same field.
 */
class ReceiveWarpViewModel(
    private val repository: OutsourcingRepository,
    private val clock: Clock,
    val jobId: String,
    private val outsideKg: BigDecimal,
) : FormViewModel<ReceiveWarpForm, JobResponse>(ReceiveWarpForm(date = Dates.format(Dates.today(clock)))) {

    override fun validate(form: ReceiveWarpForm): Map<String, String> {
        val tooMuch = run {
            val received = form.received.toDecimalOrNull() ?: return@run null
            val wastage = form.wastage.toDecimalOrNull() ?: return@run null
            if ((received + wastage) > outsideKg) s("error.OUTSOURCE_RECEIPT_EXCEEDED") else null
        }
        return Checks.collect(
            "receiptDate" to Checks.date(form.date, clock),
            "receivedWeightKg" to (Checks.positive(form.received) ?: tooMuch),
            "wastageWeightKg" to Checks.zeroOrMore(form.wastage),
        )
    }

    override suspend fun send(form: ReceiveWarpForm, idempotencyKey: String): ApiResult<JobResponse> {
        val result = repository.receive(
            jobId,
            JobReceiveRequest(Dates.toIso(form.date, clock)!!, form.received.decimal(), form.wastage.decimal(), form.notes.trim().ifEmpty { null }),
            idempotencyKey,
        )
        if (result is ApiResult.Failure && result.error.code == "OUTSOURCE_RECEIPT_EXCEEDED") {
            return ApiResult.Failure(result.error.copy(fieldErrors = result.error.fieldErrors + ("receivedWeightKg" to ErrorMessages.message(result.error))))
        }
        return result
    }
}

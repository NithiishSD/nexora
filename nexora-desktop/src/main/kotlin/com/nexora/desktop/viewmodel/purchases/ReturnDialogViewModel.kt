package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.api.dto.ReturnRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.time.Clock

data class ReturnForm(val returnDate: String = "", val weight: String = "", val reason: String = "", val notes: String = "")

/**
 * S-PUR-05 Return to supplier (FR-PROC-13); idempotent. The server decides how much can be returned and answers
 * INSUFFICIENT_INVENTORY otherwise; that message is shown with the server's detail.
 */
class ReturnDialogViewModel(
    private val repository: PurchaseRepository,
    private val clock: Clock,
    val purchaseId: String,
) : FormViewModel<ReturnForm, PurchaseResponse>(ReturnForm(returnDate = Dates.format(Dates.today(clock)))) {

    override fun validate(form: ReturnForm): Map<String, String> = Checks.collect(
        "returnDate" to Checks.date(form.returnDate, clock),
        "returnedWeightKg" to Checks.positive(form.weight),
        "reason" to Checks.required(form.reason),
    )

    override suspend fun send(form: ReturnForm, idempotencyKey: String): ApiResult<PurchaseResponse> = repository.returnToSupplier(
        purchaseId,
        ReturnRequest(Dates.toIso(form.returnDate, clock)!!, form.weight.decimal(), form.reason.trim(), form.notes.trim().ifEmpty { null }),
        idempotencyKey,
    )
}

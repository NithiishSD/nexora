package com.nexora.desktop.viewmodel.orders

import com.nexora.desktop.api.dto.CustomerReturnResponse
import com.nexora.desktop.api.dto.RecordCustomerReturnRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.time.Clock

data class RecordCustomerReturnForm(
    val orderItemId: String = "",
    val returnDate: String = "",
    val weight: String = "",
    val usable: Boolean = true,
    val reason: String = "",
    val notes: String = "",
)

/**
 * Record customer returns and route to finished/raw stock or wastage (FR-ORD-20, FR-INV-17).
 * Validates item selection, business date, positive weight, and reason.
 */
class RecordCustomerReturnViewModel(
    private val repository: OrderRepository,
    private val clock: Clock,
    val orderId: String,
) : FormViewModel<RecordCustomerReturnForm, CustomerReturnResponse>(
    RecordCustomerReturnForm(returnDate = Dates.format(Dates.today(clock))),
) {

    override fun validate(form: RecordCustomerReturnForm): Map<String, String> = Checks.collect(
        "orderItemId" to Checks.required(form.orderItemId),
        "returnDate" to Checks.date(form.returnDate, clock),
        "returnedWeightKg" to Checks.positive(form.weight),
        "reason" to Checks.required(form.reason),
    )

    override suspend fun send(form: RecordCustomerReturnForm, idempotencyKey: String): ApiResult<CustomerReturnResponse> =
        repository.recordReturn(
            orderId,
            RecordCustomerReturnRequest(
                orderItemId = form.orderItemId,
                returnDate = Dates.toIso(form.returnDate, clock)!!,
                returnedWeightKg = form.weight.decimal(),
                usable = form.usable,
                reason = form.reason.trim(),
                notes = form.notes.trim().ifEmpty { null },
            ),
            idempotencyKey,
        )
}

package com.nexora.desktop.viewmodel.purchases

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.api.dto.ReceiptRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.ui.components.ErrorMessages
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class ReceiptForm(
    val receiptDate: String = "",
    val received: String = "",
    val accepted: String = "",
    val rejected: String = "0.000",
    val notes: String = "",
    /** Until the user types an accepted weight it follows the received weight (06 S-PUR-04 default). */
    val acceptedTouched: Boolean = false,
)

/** S-PUR-04 Receive silk dialog (FR-PROC-08, -09, -10, -19); idempotent submit. */
class ReceiveSilkViewModel(
    private val repository: PurchaseRepository,
    private val clock: Clock,
    val purchaseId: String,
) : FormViewModel<ReceiptForm, PurchaseResponse>(ReceiptForm(receiptDate = Dates.format(Dates.today(clock)))) {

    private val _purchase = MutableStateFlow<PurchaseResponse?>(null)

    /** The purchase as last returned by the API: remaining kg and purchase date come from here. */
    val purchase: StateFlow<PurchaseResponse?> = _purchase.asStateFlow()

    init {
        loadPurchase(presetDefaults = true)
    }

    private fun loadPurchase(presetDefaults: Boolean) {
        viewModelScope.launch {
            val p = repository.get(purchaseId).getOrNull() ?: return@launch
            _purchase.value = p
            if (presetDefaults) {
                // Received defaults to the remaining weight, accepted to the received weight.
                val remaining = p.remainingWeightKg.toPlainString()
                preset { it.copy(received = remaining, accepted = remaining) }
            }
        }
    }

    fun setReceived(text: String) = edit { if (it.acceptedTouched) it.copy(received = text) else it.copy(received = text, accepted = text) }

    fun setAccepted(text: String) = edit { it.copy(accepted = text, acceptedTouched = true) }

    fun setRejected(text: String) = edit { it.copy(rejected = text) }

    override fun validate(form: ReceiptForm): Map<String, String> {
        val p = _purchase.value
        val split = run {
            // Input consistency only (the API checks it too): accepted + rejected may not exceed received.
            val received = form.received.toDecimalOrNull() ?: return@run null
            val accepted = form.accepted.toDecimalOrNull() ?: return@run null
            val rejected = form.rejected.toDecimalOrNull() ?: return@run null
            if (accepted + rejected > received) s("purchase.splitTooBig") else null
        }
        return Checks.collect(
            "receiptDate" to Checks.date(form.receiptDate, clock, notBefore = p?.purchaseDate?.let(LocalDate::parse)),
            "receivedWeightKg" to (Checks.positive(form.received) ?: Checks.atMost(form.received, p?.remainingWeightKg, s("error.PURCHASE_RECEIPT_EXCEEDED"))),
            "acceptedWeightKg" to (Checks.zeroOrMore(form.accepted) ?: split),
            "rejectedWeightKg" to Checks.zeroOrMore(form.rejected),
        )
    }

    override suspend fun send(form: ReceiptForm, idempotencyKey: String): ApiResult<PurchaseResponse> {
        val result = repository.receive(
            purchaseId,
            ReceiptRequest(
                receiptDate = Dates.toIso(form.receiptDate, clock)!!,
                receivedWeightKg = form.received.decimal(),
                acceptedWeightKg = form.accepted.decimal(),
                rejectedWeightKg = form.rejected.decimal(),
                notes = form.notes.trim().ifEmpty { null },
            ),
            idempotencyKey,
        )
        if (result is ApiResult.Failure) {
            when (result.error.code) {
                // Shown at the received field, where the mistake is (06 S-PUR-04).
                "PURCHASE_RECEIPT_EXCEEDED" -> return ApiResult.Failure(
                    result.error.copy(fieldErrors = result.error.fieldErrors + ("receivedWeightKg" to ErrorMessages.message(result.error))),
                )
                // Someone else changed the purchase: reload its figures, keep what the user typed.
                "CONCURRENT_MODIFICATION" -> loadPurchase(presetDefaults = false)
            }
        }
        return result
    }
}

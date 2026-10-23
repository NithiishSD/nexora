package com.nexora.desktop.viewmodel.payments

import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.dto.PaymentResponse
import com.nexora.desktop.repository.PaymentRepository
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.DetailViewModel
import java.time.Clock

/** S-PAY-03 Payment detail with reversal (FR-PAY-14, -15, -23). */
class PaymentDetailViewModel(
    private val repository: PaymentRepository,
    clock: Clock,
    val paymentId: String,
) : DetailViewModel<PaymentResponse>(clock, { repository.get(paymentId) }) {

    fun canReverse(p: PaymentResponse): Boolean = p.status == "RECORDED"
    fun canClearCheque(p: PaymentResponse): Boolean = p.paymentMethod == "CHEQUE" && p.status == "PENDING"
    fun canBounceCheque(p: PaymentResponse): Boolean = p.paymentMethod == "CHEQUE" && (p.status == "PENDING" || p.status == "RECORDED")
    fun canRefund(p: PaymentResponse): Boolean = p.purpose == "CUSTOMER" && p.direction == "RECEIVED" && p.status == "RECORDED" && p.unallocatedAmount > java.math.BigDecimal.ZERO

    /**
     * Reversal adds a compensating entry and releases the allocations; the original stays visible. A reason is
     * required (FR-PAY-15), so a blank one never reaches the server. Idempotent: a retry never reverses twice.
     */
    fun reverse(reason: String?) {
        val text = reason?.trim().orEmpty()
        if (text.isEmpty()) {
            showError(ApiError(400, "VALIDATION_FAILED", s("payment.reasonRequired"), mapOf("reason" to s("validation.required"))))
            return
        }
        runAction({ key -> repository.reverse(paymentId, text.take(255), key) })
    }

    fun clearCheque(date: String? = null, notes: String? = null) {
        runAction({ _ -> repository.clearCheque(paymentId, com.nexora.desktop.api.dto.ChequeActionRequest(date, notes?.trim())) })
    }

    fun bounceCheque(date: String? = null, notes: String? = null) {
        val text = notes?.trim().orEmpty()
        if (text.isEmpty()) {
            showError(ApiError(400, "VALIDATION_FAILED", s("cheque.reasonRequired"), mapOf("notes" to s("validation.required"))))
            return
        }
        runAction({ _ -> repository.bounceCheque(paymentId, com.nexora.desktop.api.dto.ChequeActionRequest(date, text.take(255))) })
    }

    fun refundAdvance(amount: java.math.BigDecimal? = null, notes: String? = null) {
        val current = data.value.value ?: return
        val refundAmount = amount ?: current.unallocatedAmount
        runAction({ key ->
            repository.refund(
                paymentId,
                com.nexora.desktop.api.dto.RefundPaymentRequest(
                    amount = refundAmount,
                    financialAccountId = current.account.id,
                    paymentMethod = if (current.paymentMethod == "CASH") "CASH" else "BANK_TRANSFER",
                    notes = notes?.trim()
                ),
                key
            )
        })
    }
}

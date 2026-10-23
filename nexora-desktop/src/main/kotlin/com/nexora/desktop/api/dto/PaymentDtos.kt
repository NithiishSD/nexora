package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 9 (payments and settlement).

@Serializable
data class AllocationResponse(
    val id: String? = null,
    val targetType: String,
    val targetId: String? = null,
    val targetLabel: String,
    val obligationDate: String? = null,
    val amount: Decimal,
    val released: Boolean = false,
)

@Serializable
data class PaymentResponse(
    val id: String,
    val paymentNumber: String,
    val purpose: String,
    val direction: String,
    val party: PartyRef,
    val amount: Decimal,
    val allocatedAmount: Decimal,
    val unallocatedAmount: Decimal,
    val paymentMethod: String,
    val account: AccountRef,
    val paymentDate: String,
    val referenceNumber: String? = null,
    val status: String,
    val reversedAt: String? = null,
    val reversalReason: String? = null,
    val refundOfPaymentId: String? = null,
    val allocations: List<AllocationResponse> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class PaymentSummaryResponse(
    val id: String,
    val paymentNumber: String,
    val purpose: String,
    val direction: String,
    val party: PartyRef,
    val amount: Decimal,
    val unallocatedAmount: Decimal,
    val paymentMethod: String,
    val account: AccountRef,
    val paymentDate: String,
    val status: String,
)

@Serializable
data class AllocationRequest(val targetType: String, val targetId: String, val amount: Decimal)

@Serializable
data class PaymentRequest(
    val purpose: String,
    val partyId: String,
    val amount: Decimal,
    val paymentMethod: String,
    val financialAccountId: String,
    val paymentDate: String,
    val referenceNumber: String? = null,
    val notes: String? = null,
    /** Left out (null) = the server allocates oldest-first (FR-PAY-02). */
    val allocations: List<AllocationRequest>? = null,
    val pendingCheque: Boolean? = null,
)

@Serializable
data class RefundPaymentRequest(
    val amount: Decimal,
    val financialAccountId: String,
    val paymentMethod: String = "BANK_TRANSFER",
    val paymentDate: String? = null,
    val referenceNumber: String? = null,
    val notes: String? = null,
)

@Serializable
data class ChequeActionRequest(
    val date: String? = null,
    val notes: String? = null,
)

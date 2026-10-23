package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 6 (procurement) and E-PAY-08 (all supplier payables).

@Serializable
data class ReceiptResponse(
    val id: String,
    val receiptNumber: String,
    val receiptDate: String,
    val receivedWeightKg: Decimal,
    val acceptedWeightKg: Decimal,
    val rejectedWeightKg: Decimal,
    val movementNumber: String? = null,
    val notes: String? = null,
)

@Serializable
data class SupplierReturnResponse(
    val id: String,
    val returnNumber: String,
    val returnDate: String,
    val returnedWeightKg: Decimal,
    val reason: String,
    val movementNumber: String? = null,
    val notes: String? = null,
)

@Serializable
data class PurchaseResponse(
    val id: String,
    val purchaseNumber: String,
    val supplier: PartyRef,
    val productType: String,
    val purchaseDate: String,
    val orderedWeightKg: Decimal,
    val ratePerKg: Decimal,
    val orderedValue: Decimal,
    val paymentType: String,
    val creditDays: Int,
    val dueDate: String,
    val billReference: String? = null,
    val status: String,
    val receivedWeightKg: Decimal,
    val acceptedWeightKg: Decimal,
    val rejectedWeightKg: Decimal,
    val returnedWeightKg: Decimal,
    val remainingWeightKg: Decimal,
    val payableAmount: Decimal,
    val allocatedAmount: Decimal,
    val outstandingAmount: Decimal,
    val paymentStatus: String,
    val overdue: Boolean = false,
    val overdueDays: Int = 0,
    val receipts: List<ReceiptResponse> = emptyList(),
    val returns: List<SupplierReturnResponse> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class PurchaseSummaryResponse(
    val id: String,
    val purchaseNumber: String,
    val supplier: PartyRef,
    val productType: String,
    val purchaseDate: String,
    val orderedWeightKg: Decimal,
    val receivedWeightKg: Decimal,
    val ratePerKg: Decimal,
    val orderedValue: Decimal,
    val status: String,
    val paymentStatus: String,
    val dueDate: String,
    val outstandingAmount: Decimal,
    val overdue: Boolean = false,
)

@Serializable
data class NewPurchaseRequest(
    val supplierId: String,
    val productType: String,
    val purchaseDate: String,
    val orderedWeightKg: Decimal,
    val ratePerKg: Decimal,
    val paymentType: String,
    val creditDays: Int? = null,
    val billReference: String? = null,
    val notes: String? = null,
)

@Serializable
data class PurchaseNotesRequest(val notes: String? = null, val billReference: String? = null)

@Serializable
data class ReceiptRequest(
    val receiptDate: String,
    val receivedWeightKg: Decimal,
    val acceptedWeightKg: Decimal,
    val rejectedWeightKg: Decimal,
    val notes: String? = null,
)

@Serializable
data class ReturnRequest(val returnDate: String, val returnedWeightKg: Decimal, val reason: String, val notes: String? = null)

@Serializable
data class SupplierPayableSummary(val supplier: PartyRef, val outstanding: Decimal, val overdue: Decimal, val oldestDueDate: String? = null)

@Serializable
data class AllSupplierPayablesResponse(
    val totalPayable: Decimal,
    val totalOverdue: Decimal,
    val dueWithin7Days: Decimal,
    val suppliers: List<SupplierPayableSummary> = emptyList(),
)

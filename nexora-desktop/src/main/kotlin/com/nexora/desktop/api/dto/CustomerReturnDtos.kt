package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

@Serializable
data class RecordCustomerReturnRequest(
    val orderItemId: String,
    val returnDate: String,
    val returnedWeightKg: Decimal,
    val usable: Boolean,
    val reason: String,
    val notes: String? = null,
)

@Serializable
data class CustomerReturnResponse(
    val id: String,
    val returnNumber: String,
    val orderId: String,
    val orderItemId: String,
    val lineNo: Int,
    val productType: String,
    val returnDate: String,
    val returnedWeightKg: Decimal,
    val ratePerKg: Decimal,
    val creditedAmount: Decimal,
    val usable: Boolean,
    val movementNumber: String? = null,
    val reason: String,
    val notes: String? = null,
    val createdAt: String,
)

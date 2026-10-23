package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 8 (customer orders).

@Serializable
data class OrderItemResponse(
    val id: String,
    val lineNo: Int,
    val productType: String,
    val requiredWeightKg: Decimal,
    val ratePerKg: Decimal,
    /** Computed and frozen by the server (round(weight x rate, 2)); the client only displays it. */
    val amount: Decimal,
    val warpSpecification: String? = null,
    val status: String,
    val allocatedAmount: Decimal? = null,
    val outstandingAmount: Decimal? = null,
    val paymentStatus: String? = null,
    val notes: String? = null,
)

@Serializable
data class LinkedWork(val id: String, val batchNumber: String? = null, val jobNumber: String? = null, val status: String, val allocatedWeightKg: Decimal)

@Serializable
data class OrderResponse(
    val id: String,
    val orderNumber: String,
    val customer: PartyRef,
    val orderDate: String,
    val requiredDate: String? = null,
    val deliveredOn: String? = null,
    val status: String,
    val locked: Boolean = false,
    val modifiable: Boolean = false,
    val late: Boolean = false,
    val items: List<OrderItemResponse> = emptyList(),
    val totalAmount: Decimal,
    val allocatedAmount: Decimal,
    val outstandingAmount: Decimal,
    val paymentStatus: String,
    val productionBatches: List<LinkedWork> = emptyList(),
    val outsourcingJobs: List<LinkedWork> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class OrderSummaryResponse(
    val id: String,
    val orderNumber: String,
    val customer: PartyRef,
    val orderDate: String,
    val requiredDate: String? = null,
    val status: String,
    val totalAmount: Decimal,
    val outstandingAmount: Decimal,
    val paymentStatus: String,
    val late: Boolean = false,
    val itemCount: Int = 0,
)

@Serializable
data class OrderItemRequest(
    val productType: String,
    val requiredWeightKg: Decimal,
    val ratePerKg: Decimal,
    val warpSpecification: String? = null,
)

/** POST /orders (with customerId and orderDate) and PATCH /orders/{id} (those two left null, not sent). */
@Serializable
data class OrderRequest(
    val customerId: String? = null,
    val orderDate: String? = null,
    val requiredDate: String? = null,
    val notes: String? = null,
    val items: List<OrderItemRequest>,
)

@Serializable
data class DeliverRequest(val deliveryDate: String, val notes: String? = null)

@Serializable
data class DeliverItemRequest(val deliveryDate: String, val quantityKg: Decimal, val notes: String? = null)

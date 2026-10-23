package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 7 (inventory).

@Serializable
data class BalanceLine(
    val productType: String,
    val location: String,
    val quantityKg: Decimal,
    val minThresholdKg: Decimal? = null,
    val isLowStock: Boolean = false,
)

@Serializable
data class SetThresholdRequest(val minThresholdKg: Decimal)

@Serializable
data class InventoryThresholdDto(
    val id: String,
    val productType: String,
    val location: String,
    val minThresholdKg: Decimal,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class LowStockItemDto(
    val productType: String,
    val location: String,
    val currentQuantityKg: Decimal,
    val minThresholdKg: Decimal,
    val deficitKg: Decimal,
)

@Serializable
data class WastageLine(val productType: String, val quantityKg: Decimal)

@Serializable
data class StockSummaryResponse(
    val balances: List<BalanceLine> = emptyList(),
    val rawSilkAvailableKg: Decimal,
    val internalWipKg: Decimal,
    val externalWipKg: Decimal,
    val finishedSilkWarpKg: Decimal,
    val finishedKoraWarpKg: Decimal,
    val vudaWarpKg: Decimal,
    val wastageToDateKg: List<WastageLine> = emptyList(),
    val unit: String = "kg",
    val asOf: String? = null,
)

@Serializable
data class MovementResponse(
    val id: String,
    val movementNumber: String,
    val movementType: String,
    val productType: String,
    val toProductType: String? = null,
    val quantityKg: Decimal,
    val fromLocation: String? = null,
    val toLocation: String,
    val movementDate: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val referenceNumber: String? = null,
    val reason: String? = null,
    val notes: String? = null,
    val createdBy: String? = null,
)

@Serializable
data class ExternalWipJob(
    val jobId: String,
    val jobNumber: String,
    val productType: String,
    val status: String,
    val issueDate: String? = null,
    val issuedKg: Decimal,
    val receivedKg: Decimal,
    val wastageKg: Decimal,
    val outsideKg: Decimal,
)

@Serializable
data class ExternalWipManufacturer(
    val manufacturer: PartyRef,
    val issuedKg: Decimal,
    val receivedKg: Decimal,
    val wastageKg: Decimal,
    val outsideKg: Decimal,
    val jobs: List<ExternalWipJob> = emptyList(),
)

@Serializable
data class ExternalWipResponse(val totalOutsideKg: Decimal, val manufacturers: List<ExternalWipManufacturer> = emptyList())

@Serializable
data class WastageRequest(val productType: String, val fromLocation: String, val quantityKg: Decimal, val movementDate: String, val reason: String)

@Serializable
data class StockAdjustmentRequest(
    val productType: String,
    val fromLocation: String,
    val toLocation: String,
    val quantityKg: Decimal,
    val movementDate: String,
    val reason: String,
    val notes: String? = null,
)

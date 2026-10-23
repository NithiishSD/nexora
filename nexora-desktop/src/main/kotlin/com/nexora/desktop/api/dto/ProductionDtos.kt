package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC sections 13 (production), 14 (outsourcing) and 15 (work records).

@Serializable
data class BatchOrderItem(val orderItemId: String, val orderId: String? = null, val orderNumber: String? = null, val lineNo: Int? = null, val allocatedWeightKg: Decimal)

@Serializable
data class BatchWorkRecord(val id: String, val workerName: String, val workType: String, val amount: Decimal)

@Serializable
data class BatchResponse(
    val id: String,
    val batchNumber: String,
    val productType: String,
    val productionDate: String,
    val inputWeightKg: Decimal,
    val outputWeightKg: Decimal? = null,
    val wastageWeightKg: Decimal? = null,
    val discrepancyWeightKg: Decimal? = null,
    val wastagePercent: Decimal? = null,
    val status: String,
    val orderItems: List<BatchOrderItem>? = null,
    val workRecords: List<BatchWorkRecord>? = null,
    val consumptionMovementNumber: String? = null,
    val notes: String? = null,
)

@Serializable
data class BatchLinkRequest(val orderItemId: String, val allocatedWeightKg: Decimal)

@Serializable
data class BatchRequest(
    val productType: String? = null,
    val productionDate: String,
    val inputWeightKg: Decimal,
    val notes: String? = null,
    val orderItems: List<BatchLinkRequest> = emptyList(),
)

@Serializable
data class StartBatchRequest(val startDate: String)

@Serializable
data class CompleteBatchRequest(
    val outputWeightKg: Decimal,
    val wastageWeightKg: Decimal,
    val discrepancyWeightKg: Decimal,
    val completionDate: String,
    val notes: String? = null,
)

@Serializable
data class JobReceipt(
    val receiptDate: String,
    val receivedWeightKg: Decimal,
    val wastageWeightKg: Decimal,
    val receiptMovementNumber: String? = null,
    val wastageMovementNumber: String? = null,
)

@Serializable
data class JobResponse(
    val id: String,
    val jobNumber: String,
    val manufacturer: PartyRef,
    val productType: String,
    val issuedWeightKg: Decimal,
    val receivedWeightKg: Decimal,
    val wastageWeightKg: Decimal,
    val outsideWeightKg: Decimal,
    val ratePerKg: Decimal,
    val payableAmount: Decimal,
    val allocatedAmount: Decimal? = null,
    val outstandingAmount: Decimal? = null,
    val paymentStatus: String? = null,
    val issueDate: String? = null,
    val receivedDate: String? = null,
    val status: String,
    val orderItems: List<BatchOrderItem>? = null,
    val receipts: List<JobReceipt>? = null,
    val notes: String? = null,
)

@Serializable
data class NewJobRequest(
    val manufacturerId: String,
    val productType: String,
    val issuedWeightKg: Decimal,
    val ratePerKg: Decimal,
    val notes: String? = null,
    val orderItems: List<BatchLinkRequest> = emptyList(),
)

@Serializable
data class IssueRequest(val issueDate: String)

@Serializable
data class JobReceiveRequest(val receiptDate: String, val receivedWeightKg: Decimal, val wastageWeightKg: Decimal, val notes: String? = null)

@Serializable
data class BatchRef(val id: String, val batchNumber: String)

@Serializable
data class WorkRecordResponse(
    val id: String,
    val worker: PartyRef,
    val workDate: String,
    val workType: String,
    val hours: Decimal? = null,
    val quantityKg: Decimal? = null,
    val rate: Decimal,
    val amount: Decimal,
    val allocatedAmount: Decimal? = null,
    val pendingAmount: Decimal? = null,
    val paymentStatus: String? = null,
    val status: String,
    val productionBatch: BatchRef? = null,
    val notes: String? = null,
)

@Serializable
data class WorkListResponse(val page: PageResponse<WorkRecordResponse>, val totalAmount: Decimal)

@Serializable
data class WorkRecordRequest(
    val workerId: String,
    val workDate: String,
    val workType: String,
    val hours: Decimal? = null,
    val quantityKg: Decimal? = null,
    val rate: Decimal? = null,
    val productionBatchId: String? = null,
    val notes: String? = null,
)

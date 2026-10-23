package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 5 (parties).

@Serializable
data class PhoneDto(val id: String? = null, val phoneNumber: String, val label: String, val primary: Boolean = false)

@Serializable
data class PartyResponse(
    val id: String,
    val partyCode: String,
    val name: String,
    val roles: List<String> = emptyList(),
    val status: String,
    val addressLine: String? = null,
    val area: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val note: String? = null,
    val phones: List<PhoneDto> = emptyList(),
    val defaultCreditDays: Int? = null,
    val customerCreditDays: Int? = null,
    val defaultRollingRatePerHour: Decimal? = null,
    val defaultWarpingRatePerKg: Decimal? = null,
)

@Serializable
data class PartySummaryResponse(
    val id: String,
    val partyCode: String,
    val name: String,
    val roles: List<String> = emptyList(),
    val status: String,
    val primaryPhone: String? = null,
    val city: String? = null,
)

/**
 * Body of POST /parties (with [roles]) and PATCH /parties/{id} (roles null, not sent). Null fields are left out of
 * the JSON (explicitNulls = false), so a PATCH changes only what is present.
 */
@Serializable
data class PartyRequest(
    val name: String,
    val roles: List<String>? = null,
    val addressLine: String? = null,
    val area: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val note: String? = null,
    val phones: List<PhoneDto> = emptyList(),
    val defaultCreditDays: Int? = null,
    val customerCreditDays: Int? = null,
    val defaultRollingRatePerHour: Decimal? = null,
    val defaultWarpingRatePerKg: Decimal? = null,
)

@Serializable
data class CustomerTermsRequest(val customerCreditDays: Int)

@Serializable
data class AddRoleRequest(val role: String)

@Serializable
data class AgeingDto(val days0to30: Decimal, val days31to60: Decimal, val days61to90: Decimal, val over90: Decimal)

@Serializable
data class OutstandingOrderDto(
    val orderId: String,
    val orderNumber: String,
    val orderDate: String,
    val status: String,
    val amount: Decimal,
    val allocated: Decimal,
    val outstanding: Decimal,
    val ageDays: Int,
    val dueDate: String? = null,
    val overdue: Boolean? = null,
)

@Serializable
data class OpeningObligationLine(
    val id: String,
    val obligationDate: String,
    val referenceNote: String? = null,
    val originalAmount: Decimal,
    val allocated: Decimal,
    val outstanding: Decimal,
    val ageDays: Int? = null,
)

@Serializable
data class CustomerOutstandingResponse(
    val customer: PartyRef,
    val creditDays: Int? = null,
    val totalOutstanding: Decimal,
    val advance: Decimal,
    val asOf: String? = null,
    val ageing: AgeingDto,
    val orders: List<OutstandingOrderDto> = emptyList(),
    val openingObligations: List<OpeningObligationLine> = emptyList(),
)

@Serializable
data class StatementLine(
    val date: String,
    val kind: String,
    val number: String? = null,
    val debit: Decimal,
    val credit: Decimal,
    val runningBalance: Decimal,
)

@Serializable
data class StatementResponse(
    val customer: PartyRef,
    val openingBalance: Decimal,
    val lines: List<StatementLine> = emptyList(),
    val closingBalance: Decimal,
)

@Serializable
data class SupplierPayableLine(
    val purchaseId: String,
    val purchaseNumber: String,
    val purchaseDate: String,
    val dueDate: String,
    val payable: Decimal,
    val allocated: Decimal,
    val outstanding: Decimal,
    val paymentStatus: String,
    val overdue: Boolean,
    val overdueDays: Int,
)

@Serializable
data class SupplierPayablesResponse(
    val supplier: PartyRef,
    val totalPayable: Decimal,
    val totalOverdue: Decimal,
    val dueWithin7Days: Decimal,
    val purchases: List<SupplierPayableLine> = emptyList(),
    val openingObligations: List<OpeningObligationLine> = emptyList(),
)

@Serializable
data class ManufacturerJobLine(
    val jobId: String,
    val jobNumber: String,
    val receivedKg: Decimal,
    val ratePerKg: Decimal,
    val payable: Decimal,
    val allocated: Decimal,
    val outstanding: Decimal,
    val paymentStatus: String,
)

@Serializable
data class ManufacturerPayableResponse(
    val manufacturer: PartyRef,
    val totalPayable: Decimal,
    val jobs: List<ManufacturerJobLine> = emptyList(),
    val openingObligations: List<OpeningObligationLine> = emptyList(),
)

@Serializable
data class WorkerRecordLine(
    val workRecordId: String,
    val workDate: String,
    val workType: String,
    val amount: Decimal,
    val allocated: Decimal,
    val outstanding: Decimal,
    val paymentStatus: String,
)

@Serializable
data class WorkerPayableResponse(
    val worker: PartyRef,
    val from: String? = null,
    val to: String? = null,
    val workValue: Decimal,
    val paid: Decimal,
    val pending: Decimal,
    val records: List<WorkerRecordLine> = emptyList(),
    val openingObligations: List<OpeningObligationLine> = emptyList(),
)

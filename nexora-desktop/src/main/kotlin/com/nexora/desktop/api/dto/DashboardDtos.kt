package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shape of 05-API-SPEC E-RPT-01 `GET /dashboard/summary`.

@Serializable
data class StockFigures(
    val rawSilkKg: Decimal,
    val internalWipKg: Decimal,
    val externalWipKg: Decimal,
    val finishedSilkWarpKg: Decimal,
    val finishedKoraWarpKg: Decimal,
    val vudaWarpKg: Decimal,
)

@Serializable
data class SupplierDue(
    val purchaseId: String,
    val purchaseNumber: String,
    val supplier: PartyRef,
    val dueDate: String,
    val outstanding: Decimal,
    val overdue: Boolean,
    val overdueDays: Int = 0,
)

@Serializable
data class DashboardResponse(
    val asOf: String,
    val cash: Decimal,
    val banks: List<AccountBalance> = emptyList(),
    val totalAvailable: Decimal,
    val customerReceivables: Decimal,
    val customerAdvances: Decimal,
    val supplierPayables: Decimal,
    val supplierOverdue: Decimal,
    val manufacturerPayable: Decimal,
    val workerPayable: Decimal,
    val loanOutstandingPrincipal: Decimal,
    val chitMonthlyCommitments: Decimal,
    val stock: StockFigures,
    val ordersByStatus: Map<String, Int> = emptyMap(),
    val lateOrders: Int = 0,
    val productionBatches: Map<String, Int> = emptyMap(),
    val openOutsourcingJobs: Int = 0,
    val upcomingSupplierDues: List<SupplierDue> = emptyList(),
    val recentTransactions: List<TransactionResponse> = emptyList(),
)

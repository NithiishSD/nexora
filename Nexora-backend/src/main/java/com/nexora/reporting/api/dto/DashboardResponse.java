package com.nexora.reporting.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.nexora.finance.api.dto.CashPositionResponse.AccountBalance;
import com.nexora.finance.api.dto.TransactionResponse;
import com.nexora.procurement.api.dto.SupplierDueResponse;

// GET /api/v1/dashboard/summary (05-API-SPEC E-RPT-01). Available money and obligations are separate figures: no
// receivable, payable, loan or chit amount is ever added to or taken from totalAvailable (FR-RPT-02).
public record DashboardResponse(
        Instant asOf,
        BigDecimal cash,                      // all CASH accounts
        List<AccountBalance> banks,           // each active bank account
        BigDecimal totalAvailable,            // cash + banks, nothing else
        BigDecimal customerReceivables,
        BigDecimal customerAdvances,
        BigDecimal supplierPayables,
        BigDecimal supplierOverdue,
        BigDecimal manufacturerPayable,
        BigDecimal workerPayable,
        BigDecimal loanOutstandingPrincipal,
        BigDecimal chitMonthlyCommitments,    // monthly amount of ACTIVE chits
        Stock stock,
        Map<String, Long> ordersByStatus,     // PLACED, CONFIRMED, IN_PROGRESS, READY
        long lateOrders,
        Map<String, Long> productionBatches,  // PLANNED, IN_PROGRESS
        long openOutsourcingJobs,
        List<SupplierDueResponse> upcomingSupplierDues,
        List<TransactionResponse> recentTransactions) {

    public record Stock(BigDecimal rawSilkKg, BigDecimal internalWipKg, BigDecimal externalWipKg,
                        BigDecimal finishedSilkWarpKg, BigDecimal finishedKoraWarpKg, BigDecimal vudaWarpKg) {
    }
}

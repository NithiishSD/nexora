package com.nexora.procurement.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;

// GET /api/v1/suppliers/{id}/payables (E-PAY-07). Totals are sums of outstanding amounts.
public record SupplierPayableResponse(
        PartyRef supplier,
        BigDecimal totalPayable,
        BigDecimal totalOverdue,
        BigDecimal dueWithin7Days,              // not yet overdue, due today .. today + 7
        List<PurchasePayable> purchases,        // open purchases (outstanding not 0), by due date
        List<OpeningPayable> openingObligations,
        LocalDate asOf) {

    public record PurchasePayable(UUID purchaseId, String purchaseNumber, LocalDate purchaseDate, LocalDate dueDate,
                                  BigDecimal payable, BigDecimal allocated, BigDecimal outstanding,
                                  PaymentStatus paymentStatus, boolean overdue, int overdueDays) {
    }

    public record OpeningPayable(UUID id, LocalDate obligationDate, LocalDate dueDate, String referenceNote,
                                 BigDecimal originalAmount, BigDecimal allocated, BigDecimal outstanding, int ageDays) {
    }
}

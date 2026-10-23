package com.nexora.procurement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.shared.domain.PaymentStatus;

// The payment side of one purchase, always derived (FR-PROC-15/16): payable - allocations of active payments.
// Negative outstanding = credit with the supplier (a return after payment).
public record PurchasePayment(BigDecimal payable, BigDecimal allocated, BigDecimal outstanding,
                              PaymentStatus paymentStatus, boolean overdue, int overdueDays) {

    // Overdue = something still to pay and the due date before today; never for a cancelled purchase.
    public static PurchasePayment of(BigDecimal payable, BigDecimal allocated, LocalDate dueDate, PurchaseStatus status,
                                     LocalDate today) {
        BigDecimal outstanding = payable.subtract(allocated);
        boolean overdue = status != PurchaseStatus.CANCELLED && outstanding.signum() > 0 && dueDate.isBefore(today);
        return new PurchasePayment(payable, allocated, outstanding, PaymentStatus.of(allocated, outstanding), overdue,
                overdue ? (int) ChronoUnit.DAYS.between(dueDate, today) : 0);
    }
}

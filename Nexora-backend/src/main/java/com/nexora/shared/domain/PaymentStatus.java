package com.nexora.shared.domain;

import java.math.BigDecimal;

// Derived payment status of an obligation (order, order item, purchase ...): never stored, never sent by a client
// (FR-ORD-14, FR-PROC-15). Shared because orders, procurement and finance all show it the same way.
public enum PaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID;

    // Nothing allocated -> UNPAID; nothing left to pay -> PAID; otherwise PARTIALLY_PAID.
    public static PaymentStatus of(BigDecimal allocated, BigDecimal outstanding) {
        if (allocated.signum() == 0) {
            return UNPAID;
        }
        return outstanding.signum() <= 0 ? PAID : PARTIALLY_PAID;
    }
}

package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// The arithmetic of settlement (DOMAIN_RULES Payments & allocation), with no database: the service hands in the
// party's open obligations, already in settlement order and read under the party lock.
public final class AllocationPlanner {

    private AllocationPlanner() {
    }

    // FR-PAY-02: fill the oldest obligation completely before the next one gets anything. What is left over is
    // not allocated (a customer's advance).
    public static List<PlannedAllocation> oldestFirst(BigDecimal amount, List<Obligation> open) {
        List<PlannedAllocation> plan = new ArrayList<>();
        BigDecimal left = amount;
        for (Obligation o : open) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal part = left.min(o.outstanding());
            plan.add(new PlannedAllocation(o.targetType(), o.targetId(), Money.round(part)));
            left = left.subtract(part);
        }
        return plan;
    }

    // FR-PAY-03: allocations chosen by the client are checked again here; each must target an open obligation of
    // this party (the service has already turned other parties' targets into 422) and stay within its outstanding,
    // and together they cannot exceed the payment.
    public static List<PlannedAllocation> explicit(BigDecimal amount, List<PlannedAllocation> requested,
                                                   Map<UUID, Obligation> openByTarget) {
        BigDecimal total = Money.zero();
        for (PlannedAllocation a : requested) {
            Obligation o = Objects.requireNonNull(openByTarget.get(a.targetId()), "checked by the service");
            if (a.amount().compareTo(o.outstanding()) > 0) {
                throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED, "Only "
                        + o.outstanding().toPlainString() + " is outstanding on " + o.label() + "; cannot allocate "
                        + a.amount().toPlainString() + ".");
            }
            total = total.add(a.amount());
        }
        if (total.compareTo(amount) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED, "The allocations add up to "
                    + total.toPlainString() + ", more than the payment of " + amount.toPlainString() + ".");
        }
        return requested;
    }
}

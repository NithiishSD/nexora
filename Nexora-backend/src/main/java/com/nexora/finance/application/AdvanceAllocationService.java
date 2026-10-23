package com.nexora.finance.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.domain.AllocationPlanner;
import com.nexora.finance.domain.Obligation;
import com.nexora.finance.domain.Payment;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.PlannedAllocation;
import com.nexora.finance.infrastructure.ObligationQueryRepository;
import com.nexora.finance.infrastructure.PaymentRepository;
import com.nexora.shared.domain.Money;

// FR-PAY-05 (assumption Q2): when an order becomes owed, the customer's advance pays it at once. Called by the orders
// module from inside its confirm use case; no money moves (the cash arrived with the original payments), only
// allocation rows are added to those payments.
@Service
public class AdvanceAllocationService {

    private static final Logger log = LoggerFactory.getLogger(AdvanceAllocationService.class);

    private final PaymentRepository payments;
    private final ObligationQueryRepository obligations;

    public AdvanceAllocationService(PaymentRepository payments, ObligationQueryRepository obligations) {
        this.payments = payments;
        this.obligations = obligations;
    }

    // MANDATORY: the caller holds the customer's party row lock (ADR-027), so no payment of this customer can run
    // in between. The order must already be CONFIRMED and flushed, so the SQL sees its items as owed.
    // Oldest payment first, each item filled completely before the next; reversed payments have no advance.
    // Returns the amount applied.
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal applyAdvance(UUID customerId, Collection<UUID> orderItemIds) {
        List<Obligation> open = new ArrayList<>(obligations.openObligations(PaymentPurpose.CUSTOMER, customerId).stream()
                .filter(o -> orderItemIds.contains(o.targetId())).toList());
        BigDecimal applied = Money.zero();
        for (ObligationQueryRepository.Advance advance : obligations.customerAdvances(customerId)) {
            if (open.isEmpty()) {
                break;
            }
            List<PlannedAllocation> plan = AllocationPlanner.oldestFirst(advance.unallocated(), open);
            Payment payment = payments.findById(advance.paymentId()).orElseThrow();
            payment.allocate(plan);
            for (PlannedAllocation a : plan) {
                applied = applied.add(a.amount());
                open.replaceAll(o -> o.targetId().equals(a.targetId())
                        ? new Obligation(o.targetType(), o.targetId(), o.obligationDate(), o.label(),
                                o.outstanding().subtract(a.amount()))
                        : o);
            }
            open.removeIf(o -> o.outstanding().signum() <= 0);
        }
        payments.flush();                                    // later SQL reads (order view) see the new rows
        if (applied.signum() > 0) {
            log.info("event=advance_applied customer={} amount={}", customerId, applied);
        }
        return applied;
    }
}

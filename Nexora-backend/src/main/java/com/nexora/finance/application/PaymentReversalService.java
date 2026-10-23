package com.nexora.finance.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.PaymentResponse;
import com.nexora.finance.domain.Payment;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.finance.infrastructure.PaymentRepository;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.error.ResourceNotFoundException;

// E-PAY-05: a wrong payment is never edited or deleted. It becomes REVERSED, a REVERSAL ledger row undoes its money
// on the same account, and its allocations stay but no longer count (the obligations are open again).
@Service
public class PaymentReversalService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReversalService.class);

    private final PaymentRepository payments;
    private final FinancialTransactionRepository transactions;
    private final FinanceService finance;
    private final PartyService parties;
    private final AuditService audit;
    private final PaymentViews views;
    private final Clock clock;

    public PaymentReversalService(PaymentRepository payments, FinancialTransactionRepository transactions,
                                  FinanceService finance, PartyService parties, AuditService audit, PaymentViews views,
                                  Clock clock) {
        this.payments = payments;
        this.transactions = transactions;
        this.finance = finance;
        this.parties = parties;
        this.audit = audit;
        this.views = views;
        this.clock = clock;
    }

    // Lock order (ADR-027/028): party -> payment -> account (inside FinanceService). Reversals of the same party's
    // payments, and payments allocating to its obligations, run one after the other; a second reversal of the same
    // payment then finds it REVERSED (409 INVALID_STATE_TRANSITION).
    @Transactional
    public PaymentResponse reverse(UUID id, String reason) {
        UUID partyId = payments.findPartyId(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        parties.lockForSettlement(partyId);
        Payment payment = payments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        payment.reverse(reason, clock.instant());                          // 409 if already reversed
        UUID original = transactions.originalOf(id, TransactionType.REVERSAL).orElseThrow();
        // money back out of (or into) the same account; a customer payment needs the balance (409 INSUFFICIENT_FUNDS)
        finance.reverse(original, LocalDate.now(clock), "Reversal of " + payment.getPaymentNumber() + ": " + reason);
        audit.record(AuditAction.PAYMENT_REVERSED, "PAYMENT", payment.getId(), reason, Map.of(
                "paymentNumber", payment.getPaymentNumber(), "amount", payment.getAmount().toPlainString(),
                "allocatedAmount", payment.allocatedAmount().toPlainString()));
        log.info("event=payment_reversed number={} amount={}", payment.getPaymentNumber(), payment.getAmount());
        return views.of(payments.saveAndFlush(payment));
    }
}

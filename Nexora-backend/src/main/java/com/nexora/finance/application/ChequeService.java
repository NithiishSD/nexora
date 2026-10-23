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
import com.nexora.finance.domain.RecordStatus;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.finance.infrastructure.PaymentRepository;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.error.ResourceNotFoundException;

// E-PAY-14: Cheque clearance and bounce lifecycle (FR-PAY-25).
@Service
public class ChequeService {

    private static final Logger log = LoggerFactory.getLogger(ChequeService.class);

    private final PaymentRepository payments;
    private final FinancialTransactionRepository transactions;
    private final FinanceService finance;
    private final PartyService parties;
    private final AuditService audit;
    private final PaymentViews views;
    private final Clock clock;

    public ChequeService(PaymentRepository payments, FinancialTransactionRepository transactions,
                         FinanceService finance, PartyService parties, AuditService audit,
                         PaymentViews views, Clock clock) {
        this.payments = payments;
        this.transactions = transactions;
        this.finance = finance;
        this.parties = parties;
        this.audit = audit;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse clear(UUID id, LocalDate date, String notes) {
        UUID partyId = payments.findPartyId(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        parties.lockForSettlement(partyId);
        Payment payment = payments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        payment.clearCheque(date, notes, clock.instant()); // 409 if not CHEQUE or not PENDING
        LocalDate txDate = date != null ? date : LocalDate.now(clock);
        finance.recordTransaction(new TransactionCommand(
                payment.getAccountId(),
                payment.getPurpose().ledgerDirection(),
                payment.getAmount(),
                payment.getPurpose().transactionType(),
                txDate,
                "Cheque cleared: " + payment.getPaymentNumber() + (notes != null && !notes.isBlank() ? " - " + notes.trim() : ""),
                payment.getPaymentNumber(),
                TransactionReferenceType.PAYMENT,
                payment.getId()));

        audit.record(AuditAction.CHEQUE_CLEARED, "PAYMENT", payment.getId(), notes != null ? notes.trim() : null, Map.of(
                "paymentNumber", payment.getPaymentNumber(),
                "amount", payment.getAmount().toPlainString()));

        log.info("event=cheque_cleared number={} amount={}", payment.getPaymentNumber(), payment.getAmount());
        return views.of(payments.saveAndFlush(payment));
    }

    @Transactional
    public PaymentResponse bounce(UUID id, LocalDate date, String notes) {
        UUID partyId = payments.findPartyId(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        parties.lockForSettlement(partyId);
        Payment payment = payments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        RecordStatus prevStatus = payment.getStatus();
        payment.bounceCheque(date, notes, clock.instant()); // 409 if not CHEQUE or already BOUNCED/REVERSED

        if (prevStatus == RecordStatus.RECORDED) {
            LocalDate txDate = date != null ? date : LocalDate.now(clock);
            UUID original = transactions.originalOf(id, TransactionType.REVERSAL).orElseThrow();
            finance.reverse(original, txDate, "Cheque bounced: " + payment.getPaymentNumber() + (notes != null && !notes.isBlank() ? " - " + notes.trim() : ""));
        }

        audit.record(AuditAction.CHEQUE_BOUNCED, "PAYMENT", payment.getId(), notes != null ? notes.trim() : "Cheque bounced", Map.of(
                "paymentNumber", payment.getPaymentNumber(),
                "amount", payment.getAmount().toPlainString(),
                "previousStatus", prevStatus.name()));

        log.info("event=cheque_bounced number={} amount={} previousStatus={}", payment.getPaymentNumber(), payment.getAmount(), prevStatus);
        return views.of(payments.saveAndFlush(payment));
    }
}

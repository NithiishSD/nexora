package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.AllocationRequest;
import com.nexora.finance.api.dto.AllocationResponse;
import com.nexora.finance.api.dto.PaymentResponse;
import com.nexora.finance.api.dto.PaymentSummaryResponse;
import com.nexora.finance.api.dto.RecordPaymentRequest;
import com.nexora.finance.domain.AllocationPlanner;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.Obligation;
import com.nexora.finance.domain.Payment;
import com.nexora.finance.domain.PaymentDirection;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.PlannedAllocation;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.ObligationQueryRepository;
import com.nexora.finance.infrastructure.ObligationQueryRepository.TargetState;
import com.nexora.finance.infrastructure.PaymentRepository;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// E-PAY-01 and E-PAY-03. Payment, allocations and ledger row commit together (FR-PAY-06), inside the transaction the
// idempotency filter opened for the request.
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Set<String> SORTABLE = Set.of("paymentDate", "paymentNumber", "amount", "createdAt");

    private final PaymentRepository payments;
    private final FinancialAccountRepository accounts;
    private final ObligationQueryRepository obligations;
    private final FinanceService finance;
    private final PartyService parties;
    private final BusinessNumberGenerator numbers;
    private final AuditService audit;
    private final PaymentViews views;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, FinancialAccountRepository accounts,
                          ObligationQueryRepository obligations, FinanceService finance, PartyService parties,
                          BusinessNumberGenerator numbers, AuditService audit, PaymentViews views,
                          ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.accounts = accounts;
        this.obligations = obligations;
        this.finance = finance;
        this.parties = parties;
        this.numbers = numbers;
        this.audit = audit;
        this.views = views;
        this.events = events;
        this.clock = clock;
    }

    // Lock order (ADR-028): idempotency key (filter) -> party -> obligation rows (ascending id) -> account -> PAY number. Customers pay IN (no balance
    // check), suppliers, manufacturers and workers are paid OUT (FinanceService refuses more than the account holds, nothing is stored). The open obligations are read
    // while the party is locked, so a parallel payment of the same party cannot allocate the same outstanding.
    @Transactional
    public PaymentResponse record(RecordPaymentRequest r) {
        PaymentPurpose purpose = r.purpose();
        parties.requireActiveWithRole(r.partyId(), purpose.role());     // 404, 422 INVALID_PARTY_ROLE / PARTY_INACTIVE
        parties.lockForSettlement(r.partyId());
        obligations.lockObligations(purpose, r.partyId());              // ascending id, before reading outstanding
        FinancialAccount account = accounts.findByIdForUpdate(r.financialAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", r.financialAccountId()));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Account " + account.getAccountName() + " is closed.");
        }
        if (!r.paymentMethod().fits(account.getAccountType())) {         // FR-PAY-12
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, r.paymentMethod() + " needs a "
                    + (r.paymentMethod() == PaymentMethod.CASH ? "cash" : "bank")
                    + " account; " + account.getAccountName() + " is " + account.getAccountType() + ".");
        }
        boolean pending = Boolean.TRUE.equals(r.pendingCheque());
        if (pending && r.paymentMethod() != PaymentMethod.CHEQUE) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD,
                    "Only cheques can be recorded in pending state.");
        }
        BigDecimal amount = Money.round(r.amount());
        List<Obligation> open = obligations.openObligations(purpose, r.partyId());
        List<PlannedAllocation> plan = r.allocations() == null
                ? AllocationPlanner.oldestFirst(amount, open)
                : explicit(purpose, r.partyId(), amount, r.allocations(), open);
        // Only a customer may pay ahead (advance); a supplier, manufacturer or worker is never paid more
        // than is owed
        // (their advances are post-MVP), so every rupee of an OUT payment must land on an obligation.
        BigDecimal unallocated = amount.subtract(plan.stream().map(PlannedAllocation::amount)
                .reduce(Money.zero(), BigDecimal::add));
        if (purpose != PaymentPurpose.CUSTOMER && unallocated.signum() > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED, "Only "
                    + amount.subtract(unallocated).toPlainString() + " of " + amount.toPlainString()
                    + " can be allocated; paying a " + purpose.name().toLowerCase() + " in advance is not supported.");
        }

        LocalDate date = r.paymentDate() != null ? r.paymentDate() : LocalDate.now(clock);
        String number = numbers.next("PAY");
        Payment payment = payments.save(Payment.record(number, r.partyId(), purpose, amount, r.paymentMethod(),
                account.getId(), date, blankToNull(r.referenceNumber()), blankToNull(r.notes()), pending));
        payment.allocate(plan);
        payments.flush();                                               // the ledger row needs the payment id
        if (!pending) {
            finance.recordTransaction(new TransactionCommand(account.getId(), purpose.ledgerDirection(), amount,
                    purpose.transactionType(), date, "Payment " + number, number, TransactionReferenceType.PAYMENT,
                    payment.getId()));
        }
        log.info("event=payment_recorded number={} purpose={} amount={} allocated={} pending={}", number, purpose, amount,
                payment.allocatedAmount(), pending);
        events.publishEvent(new PaymentRecordedEvent(payment.getId(), purpose, r.partyId()));
        return views.of(payment);
    }

    // E-PAY-13: Refund unallocated advance money back to customer.
    @Transactional
    public PaymentResponse refund(UUID paymentId, com.nexora.finance.api.dto.RefundPaymentRequest r) {
        Payment original = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (original.getPurpose() != PaymentPurpose.CUSTOMER || original.getStatus() != RecordStatus.RECORDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payment " + original.getPaymentNumber() + " is not an active customer payment.");
        }
        if (original.getRefundOfPayment() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payment " + original.getPaymentNumber() + " is already a refund.");
        }

        parties.requireActiveWithRole(original.getPartyId(), com.nexora.parties.domain.PartyRole.CUSTOMER);
        parties.lockForSettlement(original.getPartyId());

        BigDecimal unallocated = original.unallocatedAmount();
        BigDecimal amount = Money.round(r.amount());
        if (amount.compareTo(unallocated) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED,
                    "Only " + unallocated.toPlainString() + " of " + amount.toPlainString()
                            + " is available as unallocated advance for refund.");
        }

        FinancialAccount account = accounts.findByIdForUpdate(r.financialAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", r.financialAccountId()));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Account " + account.getAccountName() + " is closed.");
        }
        if (!r.paymentMethod().fits(account.getAccountType())) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, r.paymentMethod() + " needs a "
                    + (r.paymentMethod() == PaymentMethod.CASH ? "cash" : "bank")
                    + " account; " + account.getAccountName() + " is " + account.getAccountType() + ".");
        }

        LocalDate date = r.refundDate() != null ? r.refundDate() : LocalDate.now(clock);
        String number = numbers.next("PAY");
        Payment refund = payments.save(Payment.refund(number, original, amount, r.paymentMethod(),
                account.getId(), date, r.reason().trim()));
        payments.flush();

        finance.recordTransaction(new TransactionCommand(account.getId(), com.nexora.finance.domain.Direction.OUT,
                amount, com.nexora.finance.domain.TransactionType.CUSTOMER_REFUND, date,
                "Refund " + number + " for " + original.getPaymentNumber() + ": " + r.reason().trim(),
                number, TransactionReferenceType.PAYMENT, refund.getId()));

        audit.record(com.nexora.shared.audit.AuditAction.PAYMENT_REFUNDED, "PAYMENT", refund.getId(), r.reason().trim(),
                Map.of("refundNumber", number, "originalPaymentNumber", original.getPaymentNumber(),
                        "amount", amount.toPlainString()));

        log.info("event=payment_refunded refundNumber={} originalNumber={} amount={}", number,
                original.getPaymentNumber(), amount);
        return views.of(refund);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        return views.of(find(id));
    }

    // E-PAY-04: released allocations stay listed (FR-PAY-23).
    @Transactional(readOnly = true)
    public List<AllocationResponse> allocations(UUID id) {
        return views.allocations(find(id));
    }

    // E-PAY-02.
    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse> list(UUID partyId, PaymentPurpose purpose, PaymentDirection direction,
                                                     PaymentMethod method, UUID accountId, RecordStatus status,
                                                     LocalDate from, LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<Payment> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Payment> part : Arrays.asList(equal("partyId", partyId), equal("purpose", purpose),
                equal("direction", direction), equal("paymentMethod", method), equal("accountId", accountId),
                equal("status", status),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("paymentDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("paymentDate"), to))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return views.page(payments.findAll(filter, pageable));
    }

    private Payment find(UUID id) {
        return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    private static Specification<Payment> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    // FR-PAY-03: the client's split is trusted for nothing. Unknown target 404; a target of another kind, another
    // party or not owed (PLACED/CANCELLED order) 422; above its outstanding or above the payment 409.
    private List<PlannedAllocation> explicit(PaymentPurpose purpose, UUID partyId, BigDecimal amount,
                                             List<AllocationRequest> requested, List<Obligation> open) {
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Obligation> openByTarget = open.stream()
                .collect(Collectors.toMap(Obligation::targetId, Function.identity()));
        for (int i = 0; i < requested.size(); i++) {
            AllocationRequest a = requested.get(i);
            if (!seen.add(a.targetId())) {
                throw new FieldValidationException("allocations[" + i + "].targetId", "The same target is listed twice.");
            }
            if (!purpose.allows(a.targetType())) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_ALLOCATION,
                        "A " + purpose + " payment cannot pay a " + a.targetType() + ".");
            }
            TargetState target = obligations.target(purpose, a.targetType(), a.targetId())
                    .orElseThrow(() -> new ResourceNotFoundException(a.targetType().name(), a.targetId()));
            if (!target.partyId().equals(partyId) || !target.payable()) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_ALLOCATION, target.label()
                        + (target.partyId().equals(partyId) ? " is not owed yet." : " belongs to another party."));
            }
            if (!openByTarget.containsKey(a.targetId())) {                 // owed, but already fully paid
                throw new BusinessException(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED, target.label() + " is fully paid.");
            }
        }
        return AllocationPlanner.explicit(amount, requested.stream()
                .map(a -> new PlannedAllocation(a.targetType(), a.targetId(), Money.round(a.amount()))).toList(),
                openByTarget);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

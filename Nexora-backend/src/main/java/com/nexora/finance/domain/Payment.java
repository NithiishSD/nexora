package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// The business settlement: who paid whom, how much, how (DOMAIN_RULES rule 3). The money movement is a separate
// financial_transactions row and the split over obligations is its allocations. Amounts never change; a mistake
// is corrected by a reversal (status REVERSED + a compensating ledger row).
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    @Column(name = "payment_number", nullable = false, length = 30, updatable = false)
    private String paymentNumber;

    @Column(name = "party_id", nullable = false, updatable = false)
    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15, updatable = false)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PaymentDirection direction;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 15, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "financial_account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(name = "reference_number", length = 100, updatable = false)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordStatus status;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    // Allocations are created with the payment and never removed (no orphanRemoval, no REMOVE cascade).
    @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST)
    private List<CustomerPaymentAllocation> customerAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST)
    private List<SupplierPaymentAllocation> supplierAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST)
    private List<ManufacturerPaymentAllocation> manufacturerAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST)
    private List<WorkerPaymentAllocation> workerAllocations = new ArrayList<>();

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "refund_of_payment_id", updatable = false)
    private Payment refundOfPayment;

    @OneToMany(mappedBy = "refundOfPayment", cascade = CascadeType.PERSIST)
    private List<Payment> refunds = new ArrayList<>();

    protected Payment() {
        // for JPA only
    }

    public static Payment record(String number, UUID partyId, PaymentPurpose purpose, BigDecimal amount,
                                 PaymentMethod method, UUID accountId, LocalDate date, String referenceNumber,
                                 String notes) {
        return record(number, partyId, purpose, amount, method, accountId, date, referenceNumber, notes, false);
    }

    public static Payment record(String number, UUID partyId, PaymentPurpose purpose, BigDecimal amount,
                                 PaymentMethod method, UUID accountId, LocalDate date, String referenceNumber,
                                 String notes, boolean pending) {
        Payment p = new Payment();
        p.paymentNumber = number;
        p.partyId = partyId;
        p.purpose = purpose;
        p.direction = purpose.direction();
        p.amount = amount;
        p.paymentMethod = method;
        p.accountId = accountId;
        p.paymentDate = date;
        p.referenceNumber = referenceNumber;
        p.notes = notes;
        p.status = pending ? RecordStatus.PENDING : RecordStatus.RECORDED;
        return p;
    }

    public static Payment refund(String number, Payment original, BigDecimal amount,
                                 PaymentMethod method, UUID accountId, LocalDate date, String reason) {
        Payment p = new Payment();
        p.paymentNumber = number;
        p.partyId = original.getPartyId();
        p.purpose = PaymentPurpose.CUSTOMER;
        p.direction = PaymentDirection.PAID;
        p.amount = amount;
        p.paymentMethod = method;
        p.accountId = accountId;
        p.paymentDate = date;
        p.referenceNumber = "Refund for " + original.getPaymentNumber();
        p.notes = reason;
        p.status = RecordStatus.RECORDED;
        p.refundOfPayment = original;
        return p;
    }

    // Stores the planned split; the planner has checked every amount against outstanding and the payment.
    public void allocate(List<PlannedAllocation> plan) {
        switch (purpose) {
            case CUSTOMER -> plan.forEach(a -> customerAllocations.add(CustomerPaymentAllocation.of(this, a)));
            case SUPPLIER -> plan.forEach(a -> supplierAllocations.add(SupplierPaymentAllocation.of(this, a)));
            case MANUFACTURER ->
                    plan.forEach(a -> manufacturerAllocations.add(ManufacturerPaymentAllocation.of(this, a)));
            case WORKER -> plan.forEach(a -> workerAllocations.add(WorkerPaymentAllocation.of(this, a)));
        }
    }

    // RECORDED -> REVERSED, once (FR-PAY-15). The amounts and allocations stay as they were; the allocations simply
    // stop counting, because every outstanding query reads RECORDED payments only.
    public void reverse(String reason, Instant at) {
        if (status != RecordStatus.RECORDED) {
            throw new InvalidStateTransitionException("Payment " + paymentNumber, status, RecordStatus.REVERSED);
        }
        status = RecordStatus.REVERSED;
        reversedAt = at;
        reversalReason = reason;
    }

    public void clearCheque(LocalDate date, String notes, Instant at) {
        if (paymentMethod != PaymentMethod.CHEQUE || status != RecordStatus.PENDING) {
            throw new InvalidStateTransitionException("Payment " + paymentNumber, status, RecordStatus.RECORDED);
        }
        status = RecordStatus.RECORDED;
        if (notes != null && !notes.isBlank()) {
            this.notes = (this.notes != null ? this.notes + "\n" : "") + "Cleared: " + notes.trim();
        }
    }

    public void bounceCheque(LocalDate date, String notes, Instant at) {
        if (paymentMethod != PaymentMethod.CHEQUE || (status != RecordStatus.PENDING && status != RecordStatus.RECORDED)) {
            throw new InvalidStateTransitionException("Payment " + paymentNumber, status, RecordStatus.BOUNCED);
        }
        status = RecordStatus.BOUNCED;
        reversedAt = at;
        reversalReason = notes != null && !notes.isBlank() ? notes.trim() : "Cheque bounced";
    }

    public BigDecimal allocatedAmount() {
        return Stream.of(customerAllocations.stream().map(CustomerPaymentAllocation::getAllocatedAmount),
                        supplierAllocations.stream().map(SupplierPaymentAllocation::getAllocatedAmount),
                        manufacturerAllocations.stream().map(ManufacturerPaymentAllocation::getAllocatedAmount),
                        workerAllocations.stream().map(WorkerPaymentAllocation::getAllocatedAmount))
                .flatMap(s -> s).reduce(Money.zero(), BigDecimal::add);
    }

    public BigDecimal refundedAmount() {
        return refunds.stream()
                .filter(r -> r.status == RecordStatus.RECORDED)
                .map(Payment::getAmount)
                .reduce(Money.zero(), BigDecimal::add);
    }

    // For a customer: the advance (not revenue, FR-PAY-04) minus refunds.
    public BigDecimal unallocatedAmount() {
        return amount.subtract(allocatedAmount()).subtract(refundedAmount());
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public PaymentDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public String getNotes() {
        return notes;
    }

    public List<CustomerPaymentAllocation> getCustomerAllocations() {
        return List.copyOf(customerAllocations);
    }

    public Payment getRefundOfPayment() {
        return refundOfPayment;
    }

    public List<Payment> getRefunds() {
        return List.copyOf(refunds);
    }
}

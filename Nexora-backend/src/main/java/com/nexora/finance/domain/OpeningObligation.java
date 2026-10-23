package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.error.FieldValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// A receivable or payable that already existed in the paper ledger on the go-live day (FR-PAY-22). It is an
// obligation like an order item or a purchase: payments allocate to it oldest-first. It is not revenue, expense,
// a purchase or stock, and it never changes after entry (append-only).
@Entity
@Table(name = "opening_obligations")
public class OpeningObligation extends AuditableEntity {

    @Column(name = "party_id", nullable = false, updatable = false)
    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15, updatable = false)
    private PaymentPurpose purpose;

    @Column(name = "obligation_date", nullable = false, updatable = false)
    private LocalDate obligationDate;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal originalAmount;

    @Column(name = "reference_note", nullable = false, length = 255, updatable = false)
    private String referenceNote;

    protected OpeningObligation() {
        // for JPA only
    }

    // The obligation existed before go-live, so its date cannot be in the future (AC2); a due date, when known,
    // cannot come before it.
    public static OpeningObligation enter(UUID partyId, PaymentPurpose purpose, LocalDate obligationDate,
                                          LocalDate dueDate, BigDecimal originalAmount, String referenceNote,
                                          LocalDate today) {
        if (obligationDate.isAfter(today)) {
            throw new FieldValidationException("obligationDate", "An opening obligation cannot be dated in the future.");
        }
        if (dueDate != null && dueDate.isBefore(obligationDate)) {
            throw new FieldValidationException("dueDate", "The due date cannot be before the obligation date.");
        }
        OpeningObligation o = new OpeningObligation();
        o.partyId = partyId;
        o.purpose = purpose;
        o.obligationDate = obligationDate;
        o.dueDate = dueDate;
        o.originalAmount = originalAmount;
        o.referenceNote = referenceNote;
        return o;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public LocalDate getObligationDate() {
        return obligationDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public String getReferenceNote() {
        return referenceNote;
    }
}

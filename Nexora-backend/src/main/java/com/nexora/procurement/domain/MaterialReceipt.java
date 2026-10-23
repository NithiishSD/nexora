package com.nexora.procurement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// One physical arrival against a purchase. Append-only (FR-PROC-14): created only by Purchase.recordReceipt,
// every column updatable = false, no method changes it. A mistake is corrected by a supplier return.
@Entity
@Table(name = "material_receipts")
public class MaterialReceipt extends AuditableEntity {

    // Same module, so a JPA relation is fine; LAZY = the purchase is loaded only when it is used.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(name = "receipt_number", nullable = false, length = 30, updatable = false)
    private String receiptNumber;

    @Column(name = "receipt_date", nullable = false, updatable = false)
    private LocalDate receiptDate;

    @Column(name = "received_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal receivedWeightKg;            // what arrived; counts toward the ordered weight

    @Column(name = "accepted_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal acceptedWeightKg;            // what enters RAW_STOCK (and is paid for, assumption Q1)

    @Column(name = "rejected_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal rejectedWeightKg;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected MaterialReceipt() {
        // for JPA only
    }

    // Package-private: only Purchase creates receipts, after checking its own rules.
    static MaterialReceipt of(Purchase purchase, String receiptNumber, LocalDate receiptDate, BigDecimal receivedKg,
                              BigDecimal acceptedKg, BigDecimal rejectedKg, String notes) {
        MaterialReceipt r = new MaterialReceipt();
        r.purchase = purchase;
        r.receiptNumber = receiptNumber;
        r.receiptDate = receiptDate;
        r.receivedWeightKg = Weight.round(receivedKg);
        r.acceptedWeightKg = Weight.round(acceptedKg);
        r.rejectedWeightKg = Weight.round(rejectedKg);
        r.notes = notes;
        return r;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public BigDecimal getReceivedWeightKg() {
        return receivedWeightKg;
    }

    public BigDecimal getAcceptedWeightKg() {
        return acceptedWeightKg;
    }

    public BigDecimal getRejectedWeightKg() {
        return rejectedWeightKg;
    }

    public String getNotes() {
        return notes;
    }
}

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

// Material sent back to the supplier (FR-PROC-13). A separate, append-only record: the receipt it came
// from keeps its figures (FR-PROC-14). Its stock effect is the PURCHASE_RETURN movement that points here.
@Entity
@Table(name = "supplier_returns")
public class SupplierReturn extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(name = "return_number", nullable = false, length = 30, updatable = false)
    private String returnNumber;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Column(name = "returned_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal returnedWeightKg;

    @Column(nullable = false, length = 255, updatable = false)
    private String reason;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected SupplierReturn() {
        // for JPA only
    }

    // Package-private: only Purchase creates returns, after checking what is still returnable.
    static SupplierReturn of(Purchase purchase, String returnNumber, LocalDate returnDate, BigDecimal returnedKg,
                             String reason, String notes) {
        SupplierReturn r = new SupplierReturn();
        r.purchase = purchase;
        r.returnNumber = returnNumber;
        r.returnDate = returnDate;
        r.returnedWeightKg = Weight.round(returnedKg);
        r.reason = reason.trim();
        r.notes = notes;
        return r;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public BigDecimal getReturnedWeightKg() {
        return returnedWeightKg;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }
}

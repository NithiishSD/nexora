package com.nexora.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// One row of the inventory ledger: "this much of this product went from here to there, because of that record".
// Append-only (DOMAIN_RULES rule 5): every column is updatable = false and there is no method that changes
// anything after creation. A mistake is corrected by a new ADJUSTMENT row, never by editing this one.
@Entity
@Table(name = "material_movements")
public class MaterialMovement extends AuditableEntity {

    @Column(name = "movement_number", nullable = false, length = 30, updatable = false)
    private String movementNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;                 // what leaves fromLocation

    @Enumerated(EnumType.STRING)
    @Column(name = "to_product_type", nullable = false, length = 20, updatable = false)
    private ProductType toProductType;               // what arrives at toLocation (differs only for conversions)

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30, updatable = false)
    private MovementType movementType;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantityKg;                   // always positive; direction comes from from/to

    @Enumerated(EnumType.STRING)
    @Column(name = "from_location", length = 20, updatable = false)
    private StockLocation fromLocation;              // null only for OPENING_BALANCE

    @Enumerated(EnumType.STRING)
    @Column(name = "to_location", nullable = false, length = 20, updatable = false)
    private StockLocation toLocation;

    @Column(name = "movement_date", nullable = false, updatable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30, updatable = false)
    private MovementReferenceType referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reference_number", length = 30, updatable = false)
    private String referenceNumber;                  // business number of the source record, copied for history

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected MaterialMovement() {
        // for JPA only
    }

    // Factory. Re-checks every movement rule, so an illegal movement can never be built, whoever calls it.
    public static MaterialMovement record(String movementNumber, MovementType type, ProductType product,
                                          ProductType toProduct, StockLocation from, StockLocation to,
                                          BigDecimal quantityKg, LocalDate movementDate,
                                          MovementReferenceType referenceType, UUID referenceId,
                                          String referenceNumber, String reason, String notes) {
        MovementRules.validate(type, product, toProduct, from, to, quantityKg, reason);
        MaterialMovement m = new MaterialMovement();
        m.movementNumber = movementNumber;
        m.movementType = type;
        m.productType = product;
        m.toProductType = toProduct;
        m.fromLocation = from;
        m.toLocation = to;
        m.quantityKg = Weight.round(quantityKg);     // scale 3, exactly as stored
        m.movementDate = movementDate;
        m.referenceType = referenceType;
        m.referenceId = referenceId;
        m.referenceNumber = referenceNumber;
        m.reason = reason == null || reason.isBlank() ? null : reason.trim();
        m.notes = notes;
        return m;
    }

    public String getMovementNumber() {
        return movementNumber;
    }

    public ProductType getProductType() {
        return productType;
    }

    public ProductType getToProductType() {
        return toProductType;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public StockLocation getFromLocation() {
        return fromLocation;
    }

    public StockLocation getToLocation() {
        return toLocation;
    }

    public LocalDate getMovementDate() {
        return movementDate;
    }

    public MovementReferenceType getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }
}

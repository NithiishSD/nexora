package com.nexora.inventory.domain;

import java.math.BigDecimal;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// Current stock of one product at one internal location: the ONE allowed stored projection
// (DOMAIN_RULES rule 2). Changed only by InventoryService, in the same transaction as the movement,
// while the row is locked. Rows are created by the repository's "insert if absent" query, never by "new".
@Entity
@Table(name = "inventory_balances")
public class InventoryBalance extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockLocation location;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    protected InventoryBalance() {
        // for JPA only
    }

    // Material arrives here.
    public void add(BigDecimal kg) {
        quantityKg = Weight.round(quantityKg.add(kg));
    }

    // Material leaves. The check runs on a row the caller has locked, so nobody can change it in between.
    public void remove(BigDecimal kg) {
        if (quantityKg.compareTo(kg) < 0) {                       // compareTo, never equals, on BigDecimal
            throw new InsufficientInventoryException(productType, location, quantityKg);
        }
        quantityKg = Weight.round(quantityKg.subtract(kg));
    }

    public ProductType getProductType() {
        return productType;
    }

    public StockLocation getLocation() {
        return location;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }
}

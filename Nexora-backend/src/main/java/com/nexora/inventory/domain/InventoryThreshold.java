package com.nexora.inventory.domain;

import java.math.BigDecimal;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.FieldValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// Configurable minimum inventory threshold per legal (product_type, location) pair (FR-INV-18).
// When current stock drops at or below minThresholdKg, low-stock warnings and alerts are raised.
@Entity
@Table(name = "inventory_thresholds")
public class InventoryThreshold extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockLocation location;

    @Column(name = "min_threshold_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal minThresholdKg;

    protected InventoryThreshold() {
        // for JPA only
    }

    public InventoryThreshold(ProductType productType, StockLocation location, BigDecimal minThresholdKg) {
        validatePair(productType, location);
        this.productType = productType;
        this.location = location;
        update(minThresholdKg);
    }

    public static void validatePair(ProductType productType, StockLocation location) {
        if (productType == null) {
            throw new FieldValidationException("productType", "Product type is required.");
        }
        if (location == null) {
            throw new FieldValidationException("location", "Location is required.");
        }
        if (!location.isInternal() || !location.holds(productType)) {
            throw new FieldValidationException("location", location + " cannot hold " + productType + ".");
        }
    }

    public void update(BigDecimal minThresholdKg) {
        if (minThresholdKg == null || minThresholdKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new FieldValidationException("minThresholdKg", "Minimum threshold must be 0 kg or greater.");
        }
        if (minThresholdKg.stripTrailingZeros().scale() > Weight.SCALE) {
            throw new FieldValidationException("minThresholdKg", "Minimum threshold can have at most 3 decimals.");
        }
        this.minThresholdKg = Weight.round(minThresholdKg);
    }

    public ProductType getProductType() {
        return productType;
    }

    public StockLocation getLocation() {
        return location;
    }

    public BigDecimal getMinThresholdKg() {
        return minThresholdKg;
    }
}

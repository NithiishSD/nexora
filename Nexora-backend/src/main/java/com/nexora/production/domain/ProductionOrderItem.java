package com.nexora.production.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// One link of a batch to an order item it serves (production_order_items). Another module's record: by id only.
@Embeddable
public class ProductionOrderItem {

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "allocated_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal allocatedWeightKg;

    protected ProductionOrderItem() {
        // for JPA only
    }

    public ProductionOrderItem(UUID orderItemId, BigDecimal allocatedWeightKg) {
        this.orderItemId = orderItemId;
        this.allocatedWeightKg = allocatedWeightKg;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public BigDecimal getAllocatedWeightKg() {
        return allocatedWeightKg;
    }
}

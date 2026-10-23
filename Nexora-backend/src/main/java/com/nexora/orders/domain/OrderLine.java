package com.nexora.orders.domain;

import java.math.BigDecimal;

import com.nexora.inventory.domain.ProductType;

// What the customer asked for on one line: product, weight and the agreed rate. The amount is not part of it:
// the order computes it (FR-ORD-02), the client can never send it.
public record OrderLine(ProductType productType, BigDecimal requiredWeightKg, BigDecimal ratePerKg,
                        String warpSpecification, String notes) {
}

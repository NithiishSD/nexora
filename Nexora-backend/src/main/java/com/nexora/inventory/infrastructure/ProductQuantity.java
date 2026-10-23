package com.nexora.inventory.infrastructure;

import java.math.BigDecimal;

import com.nexora.inventory.domain.ProductType;

// One row of a GROUP BY product result, filled by a JPQL constructor expression ("select new ...").
public record ProductQuantity(ProductType productType, BigDecimal quantityKg) {
}

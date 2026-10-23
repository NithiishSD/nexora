package com.nexora.inventory.domain;

// Why stock moved. Each type allows only certain (from, to) pairs: see MovementRules.
public enum MovementType {
    OPENING_BALANCE,
    RECEIPT,
    PURCHASE_RETURN,
    DIRECT_SALE,
    CONSUMPTION,
    PRODUCTION_OUTPUT,
    WASTAGE,
    OUTSOURCE_ISSUE,
    OUTSOURCE_RECEIPT,
    DELIVERY,
    CUSTOMER_RETURN,
    ADJUSTMENT
}

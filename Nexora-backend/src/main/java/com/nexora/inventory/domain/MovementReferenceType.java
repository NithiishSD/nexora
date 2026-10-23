package com.nexora.inventory.domain;

// The kind of business record that caused a movement (material_movements.reference_type).
public enum MovementReferenceType {
    PURCHASE,
    MATERIAL_RECEIPT,
    SUPPLIER_RETURN,
    ORDER_ITEM,
    PRODUCTION_BATCH,
    OUTSOURCING_JOB,
    CUSTOMER_RETURN
}

package com.nexora.procurement.domain;

// Receiving status of a purchase (DOMAIN_RULES section 5). Payment status is separate and always derived.
public enum PurchaseStatus {
    ORDERED,
    PARTIALLY_RECEIVED,
    FULLY_RECEIVED,
    CANCELLED
}

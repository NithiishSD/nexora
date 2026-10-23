package com.nexora.outsourcing.domain;

// CREATED -> MATERIAL_ISSUED -> PARTIALLY_RECEIVED -> COMPLETED; CREATED -> CANCELLED (DOMAIN_RULES section 5).
// A single receipt that accounts for everything goes from MATERIAL_ISSUED straight to COMPLETED.
public enum JobStatus {
    CREATED,
    MATERIAL_ISSUED,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED
}

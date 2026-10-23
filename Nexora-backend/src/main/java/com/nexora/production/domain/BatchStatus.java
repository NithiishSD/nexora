package com.nexora.production.domain;

// PLANNED -> IN_PROGRESS -> COMPLETED; PLANNED -> CANCELLED (DOMAIN_RULES section 5).
public enum BatchStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

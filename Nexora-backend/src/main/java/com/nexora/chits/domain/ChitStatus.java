package com.nexora.chits.domain;

// ACTIVE -> MATURED when the payout is received (back to ACTIVE if that payout is reversed); ACTIVE or MATURED ->
// CLOSED by the owner (FR-CHIT-03/06/09).
public enum ChitStatus {
    ACTIVE,
    MATURED,
    CLOSED
}

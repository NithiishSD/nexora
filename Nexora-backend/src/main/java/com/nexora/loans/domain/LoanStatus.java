package com.nexora.loans.domain;

// ACTIVE until the owner closes it at 0.00 principal outstanding (FR-LOAN-06).
public enum LoanStatus {
    ACTIVE,
    CLOSED
}

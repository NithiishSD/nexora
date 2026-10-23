package com.nexora.finance.domain;

// The kind of business record a ledger row belongs to; derived from which reference column is set.
public enum TransactionReferenceType {
    PAYMENT,
    EXPENSE,
    LOAN,
    CHIT,
    TRANSFER,
    NONE
}

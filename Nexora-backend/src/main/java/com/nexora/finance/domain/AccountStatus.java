package com.nexora.finance.domain;

// A CLOSED account takes no new transactions; its history stays visible.
public enum AccountStatus {
    ACTIVE,
    CLOSED
}

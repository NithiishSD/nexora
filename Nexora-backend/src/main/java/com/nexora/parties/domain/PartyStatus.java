package com.nexora.parties.domain;

// ACTIVE <-> INACTIVE. An inactive party keeps its history but cannot be used in new transactions.
public enum PartyStatus {
    ACTIVE,
    INACTIVE
}

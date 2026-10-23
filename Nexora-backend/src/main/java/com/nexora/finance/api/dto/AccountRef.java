package com.nexora.finance.api.dto;

import java.util.UUID;

import com.nexora.finance.domain.AccountType;

// The account as shown inside another resource (05-API-SPEC 3.2).
public record AccountRef(UUID id, String accountName, AccountType accountType) {
}

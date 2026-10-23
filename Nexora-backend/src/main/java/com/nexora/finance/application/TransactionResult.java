package com.nexora.finance.application;

import java.util.UUID;

// The stored ledger row's id, for Location headers and links from the business record.
public record TransactionResult(UUID transactionId) {
}

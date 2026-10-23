package com.nexora.finance.application;

import java.util.UUID;

import com.nexora.finance.domain.PaymentPurpose;

// Published inside the payment's transaction. Listeners run with @TransactionalEventListener: work that belongs with
// the payment runs BEFORE_COMMIT on the same connection (never a second pooled connection while this one is held).
public record PaymentRecordedEvent(UUID paymentId, PaymentPurpose purpose, UUID partyId) {
}

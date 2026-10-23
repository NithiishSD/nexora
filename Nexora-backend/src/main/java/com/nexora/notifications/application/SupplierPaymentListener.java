package com.nexora.notifications.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexora.finance.application.PaymentRecordedEvent;
import com.nexora.finance.domain.PaymentPurpose;

// FR-NTF-03 AC1: when a supplier payment commits, reminders of the purchases it settled are resolved with it: one
// UPDATE in the payment's own transaction, just before the commit, on the same connection. (A REQUIRES_NEW
// transaction after the commit needed a second pooled connection per request while the first was still held, and 20
// parallel payments exhausted the pool, NFR-PERF-05.) The daily job resolves anything missed.
@Component
class SupplierPaymentListener {

    private final NotificationService notifications;

    SupplierPaymentListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void paymentRecorded(PaymentRecordedEvent event) {
        if (event.purpose() == PaymentPurpose.SUPPLIER) {
            notifications.resolvePaidPurchases();
        }
    }
}

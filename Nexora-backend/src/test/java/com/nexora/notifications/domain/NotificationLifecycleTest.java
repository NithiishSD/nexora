package com.nexora.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.notifications.NotificationTestSupport;
import com.nexora.shared.error.BusinessException;

class NotificationLifecycleTest extends NotificationTestSupport {

    @Test
    @DisplayName("FR-NTF-03 paid purchase resolves pending notification")
    void paidPurchase_resolvesPendingNotification() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String supplier = data.supplier().id().toString();
        String purchase = duePurchase(supplier, today().minusDays(10), 7, "100.00");      // 1000.00, 3 days overdue
        job.run(today());
        assertThat(reminders(purchase)).singleElement().satisfies(r -> assertThat(r.get("status")).isEqualTo("PENDING"));

        // a part payment leaves something to pay: still open
        paySupplier(supplier, "400.00", bank);
        assertThat(reminders(purchase).getFirst().get("status")).isEqualTo("PENDING");
        // AC1: the payment that settles the purchase resolves it once committed
        paySupplier(supplier, "600.00", bank);
        assertThat(reminders(purchase).getFirst().get("status")).isEqualTo("RESOLVED");

        // AC2: later runs create nothing for it and leave it resolved
        assertThat(job.run(today().plusDays(1))).isZero();
        assertThat(reminders(purchase)).singleElement().satisfies(r -> assertThat(r.get("status")).isEqualTo("RESOLVED"));
    }

    @Test
    @DisplayName("FR-NTF-03 FR-NTF-04 delivery state moves only from pending")
    void deliveryState_movesOnlyFromPending() {
        Notification sent = reminder();
        sent.markSent(Instant.parse("2026-10-08T02:30:00Z"));
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> sent.markSent(Instant.now())).isInstanceOf(BusinessException.class);

        Notification failing = reminder();
        failing.recordFailure("timeout", 3);
        failing.recordFailure("timeout", 3);
        assertThat(failing.getStatus()).isEqualTo(NotificationStatus.PENDING);
        failing.recordFailure("timeout", 3);
        assertThat(failing.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failing.getAttempts()).isEqualTo(3);
        assertThat(failing.getLastError()).isEqualTo("timeout");

        failing.resolve();
        assertThat(failing.isOpen()).isFalse();
    }

    private static Notification reminder() {
        return Notification.supplierDue(UUID.randomUUID(), UUID.randomUUID(), "PUR-2026-0001", ReminderKind.OVERDUE,
                Priority.HIGH, "Pay", "key", Instant.parse("2026-10-08T02:30:00Z"));
    }
}

package com.nexora.notifications.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.notifications.domain.Notification;
import com.nexora.notifications.domain.NotificationStatus;
import com.nexora.notifications.infrastructure.NotificationRepository;

// Sends PENDING notifications after they were committed (FR-NTF-04): the provider call happens outside any
// transaction, each outcome is stored in its own short transaction, and a failing provider can never roll back
// anything else (NFR-REL-05). Up to 3 attempts per notification, waiting longer before each retry.
@Component
public class NotificationDispatcher {

    static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final TransactionTemplate tx;
    private final NotificationProvider provider;
    private final Clock clock;
    private final long retryDelayMillis;

    @Autowired
    public NotificationDispatcher(NotificationRepository notifications, PlatformTransactionManager transactions,
                                  NotificationProvider provider, Clock clock,
                                  @Value("${nexora.notifications.retry-delay-ms:1000}") long retryDelayMillis) {
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactions);
        this.provider = provider;
        this.clock = clock;
        this.retryDelayMillis = retryDelayMillis;
    }

    // Every pending notification, oldest first. Returns how many were sent.
    public int dispatchPending() {
        List<UUID> pending = tx.execute(s -> notifications.findByStatusOrderByScheduledAt(NotificationStatus.PENDING)
                .stream().map(Notification::getId).toList());
        int sent = 0;
        for (UUID id : pending) {
            if (dispatch(id)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean dispatch(UUID id) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Notification n = tx.execute(s -> notifications.findById(id).orElse(null));
            if (n == null || n.getStatus() != NotificationStatus.PENDING) {
                return false;                                   // resolved or handled meanwhile
            }
            try {
                provider.send(n);
                tx.executeWithoutResult(s -> notifications.findById(id).ifPresent(x -> x.markSent(clock.instant())));
                return true;
            } catch (RuntimeException e) {
                log.warn("event=notification_send_failed id={} attempt={} error={}", id, attempt, e.getMessage());
                tx.executeWithoutResult(s -> notifications.findById(id)
                        .ifPresent(x -> x.recordFailure(e.getMessage(), MAX_ATTEMPTS)));
                pause(attempt);
            }
        }
        return false;
    }

    // 1x, 2x the configured delay before the second and third attempt.
    private void pause(int attempt) {
        if (attempt < MAX_ATTEMPTS && retryDelayMillis > 0) {
            try {
                Thread.sleep(retryDelayMillis * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

package com.nexora.notifications.domain;

import java.time.Instant;
import java.util.UUID;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// One reminder the system created (FR-NTF-03). Its message is fixed when it is created; only the delivery state
// (status, attempts, sent time, last error) changes, through the methods below.
@Entity
@Table(name = "notifications")
public class Notification extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30, updatable = false)
    private NotificationType notificationType;

    @Column(name = "recipient_party_id", updatable = false)
    private UUID recipientPartyId;

    @Column(name = "reference_type", length = 20, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reference_number", length = 30, updatable = false)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_kind", length = 10, updatable = false)
    private ReminderKind reminderKind;

    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(name = "dedupe_key", nullable = false, length = 150, updatable = false)
    private String dedupeKey;

    protected Notification() {
        // for JPA only
    }

    // A supplier due-date reminder for one purchase (FR-NTF-02), PENDING until it is sent.
    public static Notification supplierDue(UUID supplierId, UUID purchaseId, String purchaseNumber, ReminderKind kind,
                                           Priority priority, String message, String dedupeKey, Instant scheduledAt) {
        Notification n = new Notification();
        n.notificationType = NotificationType.SUPPLIER_DUE;
        n.recipientPartyId = supplierId;
        n.referenceType = "PURCHASE";
        n.referenceId = purchaseId;
        n.referenceNumber = purchaseNumber;
        n.reminderKind = kind;
        n.priority = priority;
        n.message = message;
        n.dedupeKey = dedupeKey;
        n.scheduledAt = scheduledAt;
        n.status = NotificationStatus.PENDING;
        return n;
    }

    // A low-stock alert for an inventory product/location (FR-NTF-07).
    public static Notification lowStock(UUID thresholdId, String referenceNumber, Priority priority, String message,
                                        String dedupeKey, Instant scheduledAt) {
        Notification n = new Notification();
        n.notificationType = NotificationType.LOW_STOCK;
        if (thresholdId != null) {
            n.referenceType = "INVENTORY";
            n.referenceId = thresholdId;
        }
        n.referenceNumber = referenceNumber;
        n.priority = priority;
        n.message = message;
        n.dedupeKey = dedupeKey;
        n.scheduledAt = scheduledAt;
        n.status = NotificationStatus.PENDING;
        return n;
    }

    // A loan repayment or interest reminder for a loan (FR-NTF-08).
    public static Notification loanDue(UUID lenderId, UUID loanId, String loanNumber, ReminderKind kind,
                                       Priority priority, String message, String dedupeKey, Instant scheduledAt) {
        Notification n = new Notification();
        n.notificationType = NotificationType.LOAN_DUE;
        n.recipientPartyId = lenderId;
        n.referenceType = "LOAN";
        n.referenceId = loanId;
        n.referenceNumber = loanNumber;
        n.reminderKind = kind;
        n.priority = priority;
        n.message = message;
        n.dedupeKey = dedupeKey;
        n.scheduledAt = scheduledAt;
        n.status = NotificationStatus.PENDING;
        return n;
    }

    // A monthly contribution reminder for a chit fund (FR-NTF-09).
    public static Notification chitDue(UUID chitId, String chitNumber, ReminderKind kind,
                                       Priority priority, String message, String dedupeKey, Instant scheduledAt) {
        Notification n = new Notification();
        n.notificationType = NotificationType.CHIT_DUE;
        n.recipientPartyId = null;
        n.referenceType = "CHIT";
        n.referenceId = chitId;
        n.referenceNumber = chitNumber;
        n.reminderKind = kind;
        n.priority = priority;
        n.message = message;
        n.dedupeKey = dedupeKey;
        n.scheduledAt = scheduledAt;
        n.status = NotificationStatus.PENDING;
        return n;
    }

    // The provider accepted it (FR-NTF-04 AC1). Only a pending notification is sent.
    public void markSent(Instant at) {
        requirePending();
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = at;
        lastError = null;
    }

    // One failed attempt; after maxAttempts the notification is FAILED (AC2), otherwise it stays PENDING for a retry.
    public void recordFailure(String error, int maxAttempts) {
        requirePending();
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            status = NotificationStatus.FAILED;
        }
    }

    // The purchase is paid: nothing left to remind about (FR-NTF-03). Resolving twice changes nothing.
    public void resolve() {
        status = NotificationStatus.RESOLVED;
    }

    // A failed notification can be retried (E-NTF-04): resets to PENDING and clears attempts and lastError.
    public void retry() {
        if (status != NotificationStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a failed notification can be retried; this one is " + status + ".");
        }
        status = NotificationStatus.PENDING;
        attempts = 0;
        lastError = null;
    }

    public boolean isOpen() {
        return status != NotificationStatus.RESOLVED;
    }

    private void requirePending() {
        if (status != NotificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a pending notification can be sent; this one is " + status + ".");
        }
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public UUID getRecipientPartyId() {
        return recipientPartyId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public Priority getPriority() {
        return priority;
    }

    public ReminderKind getReminderKind() {
        return reminderKind;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public String getMessage() {
        return message;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}

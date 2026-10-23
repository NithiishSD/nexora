package com.nexora.notifications.domain;

// PENDING -> SENT or FAILED (after the retries); PENDING, SENT or FAILED -> RESOLVED once paid.
public enum NotificationStatus {
    PENDING, SENT, FAILED, RESOLVED
}

package com.nexora.notifications.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexora.notifications.domain.NotificationStatus;
import com.nexora.notifications.domain.NotificationType;
import com.nexora.notifications.domain.Priority;
import com.nexora.notifications.domain.ReminderKind;
import com.nexora.parties.application.PartyRef;

// One notification as the app shows it (05-API-SPEC section 17).
public record NotificationResponse(UUID id, NotificationType notificationType, ReminderKind kind, Priority priority,
                                   PartyRef party, String referenceType, UUID referenceId, String referenceNumber,
                                   String message, Instant scheduledAt, Instant sentAt, NotificationStatus status,
                                   int attempts, Instant createdAt, Instant updatedAt) {
}

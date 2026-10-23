package com.nexora.notifications.api.dto;

import java.time.Instant;

import com.nexora.notifications.domain.NotificationType;
import com.nexora.notifications.domain.Priority;

// One notification type's schedule (E-NTF-02).
public record NotificationConfigurationResponse(NotificationType notificationType, boolean enabled, int daysBeforeDue,
                                                int overdueRepeatDays, Priority priority, Instant updatedAt) {
}

package com.nexora.notifications.api.dto;

import com.nexora.notifications.domain.Priority;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Body of PUT /api/v1/notifications/configurations/{type} (E-NTF-03): the whole configuration, replaced as one.
public record NotificationConfigurationRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(0) @Max(30) Integer daysBeforeDue,
        @NotNull @Min(1) @Max(30) Integer overdueRepeatDays,
        @NotNull Priority priority) {
}

package com.nexora.notifications.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.notifications.api.dto.NotificationConfigurationRequest;
import com.nexora.notifications.api.dto.NotificationConfigurationResponse;
import com.nexora.notifications.application.NotificationService;
import com.nexora.notifications.domain.NotificationType;

import jakarta.validation.Valid;

// When reminders are created (E-NTF-02, E-NTF-03). PUT is the one place the API allows it: configuration only.
@RestController
@RequestMapping("/api/v1/notifications/configurations")
class NotificationConfigurationController {

    private final NotificationService notifications;

    NotificationConfigurationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    List<NotificationConfigurationResponse> list() {
        return notifications.configurations();
    }

    @PutMapping("/{type}")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    NotificationConfigurationResponse update(@PathVariable NotificationType type,
                                             @Valid @RequestBody NotificationConfigurationRequest request) {
        return notifications.updateConfiguration(type, request);
    }
}

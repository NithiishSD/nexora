package com.nexora.notifications.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.notifications.api.dto.NotificationResponse;
import com.nexora.notifications.application.NotificationService;
import com.nexora.notifications.domain.NotificationStatus;
import com.nexora.notifications.domain.NotificationType;
import com.nexora.notifications.domain.ReminderKind;
import com.nexora.shared.api.PageResponse;

// The reminders the system created (E-NTF-01). Read only: they are made by the daily job and resolved by payments.
@RestController
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/v1/notifications")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    PageResponse<NotificationResponse> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) ReminderKind kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notifications.list(status, type, kind, from, to, pageable);
    }

    // E-NTF-04.
    @PostMapping("/api/v1/notifications/{id}/retry")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    NotificationResponse retry(@PathVariable UUID id) {
        return notifications.retry(id);
    }
}

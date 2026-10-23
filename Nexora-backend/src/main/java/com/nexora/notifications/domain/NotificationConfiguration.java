package com.nexora.notifications.domain;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.error.FieldValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// How one notification type is scheduled (FR-NTF-01). Rows are seeded by the migration; the owner only changes them.
@Entity
@Table(name = "notification_configurations")
public class NotificationConfiguration extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30, updatable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "days_before_due", nullable = false)
    private int daysBeforeDue;

    @Column(name = "overdue_repeat_days", nullable = false)
    private int overdueRepeatDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    protected NotificationConfiguration() {
        // for JPA only
    }

    // The same bounds as the database (0-30 days before, repeat 1-30 days); the request DTO checks them first.
    public void update(boolean enabled, int daysBeforeDue, int overdueRepeatDays, Priority priority) {
        if (daysBeforeDue < 0 || daysBeforeDue > 30) {
            throw new FieldValidationException("daysBeforeDue", "Must be between 0 and 30.");
        }
        if (overdueRepeatDays < 1 || overdueRepeatDays > 30) {
            throw new FieldValidationException("overdueRepeatDays", "Must be between 1 and 30.");
        }
        this.enabled = enabled;
        this.daysBeforeDue = daysBeforeDue;
        this.overdueRepeatDays = overdueRepeatDays;
        this.priority = priority;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getDaysBeforeDue() {
        return daysBeforeDue;
    }

    public int getOverdueRepeatDays() {
        return overdueRepeatDays;
    }

    public Priority getPriority() {
        return priority;
    }
}

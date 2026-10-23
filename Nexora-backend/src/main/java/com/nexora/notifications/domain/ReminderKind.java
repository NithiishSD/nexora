package com.nexora.notifications.domain;

// Which due-date reminder (FR-NTF-02): before the due date, on it, or a repeat while overdue.
public enum ReminderKind {
    DUE_SOON, DUE_TODAY, OVERDUE
}

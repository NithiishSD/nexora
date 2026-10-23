package com.nexora.notifications.domain;

// What a notification is about. Only SUPPLIER_DUE is scheduled in the first release (FR-NTF-07).
public enum NotificationType {
    SUPPLIER_DUE, CUSTOMER_OUTSTANDING, LOAN_DUE, CHIT_DUE, LOW_STOCK, PENDING_OUTSOURCING
}

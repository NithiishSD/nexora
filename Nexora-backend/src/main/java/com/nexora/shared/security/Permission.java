package com.nexora.shared.security;

// Every action a user can be allowed to do. Roles (OWNER, STAFF) are just bundles of these.
// The same codes exist in the "permissions" table (seeded by migration V5); a test keeps them in sync.
// Controllers use them as:  @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
public enum Permission {
    USER_MANAGE,

    PARTY_VIEW,
    PARTY_MANAGE,

    PURCHASE_VIEW,
    PURCHASE_CREATE,

    INVENTORY_VIEW,
    INVENTORY_ADJUST,

    ORDER_VIEW,
    ORDER_MANAGE,

    PRODUCTION_VIEW,
    PRODUCTION_MANAGE,
    OUTSOURCING_MANAGE,
    WORK_RECORD_MANAGE,

    PAYMENT_VIEW,
    PAYMENT_CREATE,
    PAYMENT_REVERSE,

    FINANCE_VIEW,
    FINANCE_MANAGE,
    EXPENSE_CREATE,
    LOAN_MANAGE,
    CHIT_MANAGE,

    REPORT_VIEW,
    DOCUMENT_MANAGE
}

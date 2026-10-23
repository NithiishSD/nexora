-- Expand notification constraints to support CHIT_DUE and CHIT reference type (FR-NTF-08, FR-NTF-09)

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    notification_type IN ('SUPPLIER_DUE', 'CUSTOMER_OUTSTANDING', 'LOAN_DUE', 'CHIT_DUE', 'LOW_STOCK', 'PENDING_OUTSOURCING')
);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_reference_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_reference_type CHECK (
    reference_type IS NULL OR reference_type IN ('PURCHASE', 'ORDER', 'LOAN', 'CHIT', 'OUTSOURCING_JOB', 'INVENTORY')
);

ALTER TABLE notification_configurations DROP CONSTRAINT ck_notification_configurations_type;
ALTER TABLE notification_configurations ADD CONSTRAINT ck_notification_configurations_type CHECK (
    notification_type IN ('SUPPLIER_DUE', 'CUSTOMER_OUTSTANDING', 'LOAN_DUE', 'CHIT_DUE', 'LOW_STOCK', 'PENDING_OUTSOURCING')
);

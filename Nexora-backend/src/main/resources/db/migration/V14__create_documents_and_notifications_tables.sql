-- Documents and notifications (04-DATA-MODEL section 12). Documents hold only metadata: the file lives in file
-- storage under a generated key. Notifications are reminders the system creates and sends after commit; they never
-- touch money or stock.

-- ---------------------------------------------------------------------------------------------
-- documents: an uploaded bill, receipt or statement. Never overwritten: a corrected file is a new row that
-- supersedes the earlier one.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE documents (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type          VARCHAR(20)  NOT NULL,
    file_name              VARCHAR(255) NOT NULL,                     -- original name, sanitised
    content_type           VARCHAR(100) NOT NULL,
    size_bytes             BIGINT       NOT NULL,
    storage_path           VARCHAR(500) NOT NULL,                     -- storage key, never user-controlled
    reference_type         VARCHAR(20),
    reference_id           UUID,                                      -- polymorphic, no FK
    status                 VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    supersedes_document_id UUID,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_documents_storage_path UNIQUE (storage_path),
    CONSTRAINT fk_documents_supersedes FOREIGN KEY (supersedes_document_id) REFERENCES documents (id),
    CONSTRAINT fk_documents_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_documents_type CHECK (document_type IN ('PURCHASE_BILL', 'SALES_BILL', 'BANK_STATEMENT',
                                                          'EXPENSE_RECEIPT', 'OTHER')),
    CONSTRAINT ck_documents_size CHECK (size_bytes > 0),
    CONSTRAINT ck_documents_reference CHECK ((reference_type IS NULL) = (reference_id IS NULL)),
    CONSTRAINT ck_documents_reference_type CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE', 'ORDER', 'EXPENSE', 'PAYMENT', 'LOAN', 'PARTY')),
    CONSTRAINT ck_documents_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_documents_not_self CHECK (supersedes_document_id IS NULL OR supersedes_document_id <> id)
);
CREATE INDEX ix_documents_reference ON documents (reference_type, reference_id);
CREATE INDEX ix_documents_supersedes_document_id ON documents (supersedes_document_id);
CREATE INDEX ix_documents_created_by ON documents (created_by);

-- ---------------------------------------------------------------------------------------------
-- notifications: one reminder. dedupe_key makes the daily job safe to re-run: one row per purchase, kind and
-- (for overdue repeats) date.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_type  VARCHAR(30)  NOT NULL,
    recipient_party_id UUID,                                          -- the party the reminder is about
    reference_type     VARCHAR(20),
    reference_id       UUID,                                          -- polymorphic, no FK
    reference_number   VARCHAR(30),                                   -- PUR-2026-0003, for the message list
    priority           VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    reminder_kind      VARCHAR(10),
    scheduled_at       TIMESTAMPTZ  NOT NULL,
    sent_at            TIMESTAMPTZ,
    status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts           INTEGER      NOT NULL DEFAULT 0,
    last_error         VARCHAR(500),
    message            TEXT         NOT NULL,
    dedupe_key         VARCHAR(150) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_notifications_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT fk_notifications_recipient_party FOREIGN KEY (recipient_party_id) REFERENCES parties (id),
    CONSTRAINT fk_notifications_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_notifications_type CHECK (notification_type IN ('SUPPLIER_DUE', 'CUSTOMER_OUTSTANDING', 'LOAN_DUE',
                                                                  'LOW_STOCK', 'PENDING_OUTSOURCING')),
    CONSTRAINT ck_notifications_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_notifications_reminder_kind CHECK (reminder_kind IS NULL
                                                     OR reminder_kind IN ('DUE_SOON', 'DUE_TODAY', 'OVERDUE')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RESOLVED')),
    CONSTRAINT ck_notifications_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_notifications_reference CHECK ((reference_type IS NULL) = (reference_id IS NULL)),
    CONSTRAINT ck_notifications_reference_type CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE', 'ORDER', 'LOAN', 'OUTSOURCING_JOB', 'INVENTORY')),
    CONSTRAINT ck_notifications_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);
CREATE INDEX ix_notifications_status_scheduled ON notifications (status, scheduled_at);
CREATE INDEX ix_notifications_recipient_party_id ON notifications (recipient_party_id);
CREATE INDEX ix_notifications_reference ON notifications (reference_type, reference_id);
CREATE INDEX ix_notifications_created_by ON notifications (created_by);

-- ---------------------------------------------------------------------------------------------
-- notification_configurations: one row per type. No penalty amount is computed in the MVP (assumption Q3).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE notification_configurations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_type   VARCHAR(30) NOT NULL,
    enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
    days_before_due     INTEGER     NOT NULL DEFAULT 3,               -- first reminder; also one on the due date
    overdue_repeat_days INTEGER     NOT NULL DEFAULT 1,               -- repeat every N days while overdue
    priority            VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uq_notification_configurations_type UNIQUE (notification_type),
    CONSTRAINT fk_notification_configurations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_notification_configurations_type CHECK (notification_type IN (
        'SUPPLIER_DUE', 'CUSTOMER_OUTSTANDING', 'LOAN_DUE', 'LOW_STOCK', 'PENDING_OUTSOURCING')),
    CONSTRAINT ck_notification_configurations_days_before_due CHECK (days_before_due BETWEEN 0 AND 30),
    CONSTRAINT ck_notification_configurations_repeat CHECK (overdue_repeat_days > 0),
    CONSTRAINT ck_notification_configurations_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);
CREATE INDEX ix_notification_configurations_created_by ON notification_configurations (created_by);

-- the one type the first release schedules (FR-NTF-01, FR-NTF-07)
INSERT INTO notification_configurations (notification_type, enabled, days_before_due, overdue_repeat_days, priority)
VALUES ('SUPPLIER_DUE', TRUE, 3, 1, 'HIGH');

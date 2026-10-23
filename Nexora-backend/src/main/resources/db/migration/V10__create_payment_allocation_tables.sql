-- Settlement (04-DATA-MODEL section 8 and 12.4): what each payment paid off, the obligations that existed before
-- go-live, the idempotency keys that make retried POSTs safe, and the audit log of sensitive actions.

-- ---------------------------------------------------------------------------------------------
-- opening_obligations: receivables and payables carried over from the paper ledger on the go-live day.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE opening_obligations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id        UUID           NOT NULL,
    purpose         VARCHAR(15)    NOT NULL,
    obligation_date DATE           NOT NULL,                         -- original date: oldest-first order
    due_date        DATE,
    original_amount NUMERIC(14, 2) NOT NULL,
    reference_note  VARCHAR(255)   NOT NULL,                         -- old bill / notebook page
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      UUID,
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_opening_obligations_party FOREIGN KEY (party_id) REFERENCES parties (id),
    CONSTRAINT ck_opening_obligations_purpose CHECK (purpose IN ('CUSTOMER', 'SUPPLIER', 'MANUFACTURER', 'WORKER')),
    CONSTRAINT ck_opening_obligations_amount CHECK (original_amount > 0),
    CONSTRAINT ck_opening_obligations_due CHECK (due_date IS NULL OR due_date >= obligation_date),
    CONSTRAINT fk_opening_obligations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_opening_obligations_party_purpose ON opening_obligations (party_id, purpose);
CREATE INDEX ix_opening_obligations_created_by ON opening_obligations (created_by);

-- ---------------------------------------------------------------------------------------------
-- customer_payment_allocations: how much of a customer payment went to which order item or opening receivable.
-- A reversed payment's rows stay; queries count only allocations of RECORDED payments.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE customer_payment_allocations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID           NOT NULL,
    order_item_id         UUID,
    opening_obligation_id UUID,
    allocated_amount      NUMERIC(14, 2) NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_customer_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_customer_payment_allocations_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_customer_payment_allocations_opening_obligation FOREIGN KEY (opening_obligation_id)
        REFERENCES opening_obligations (id),
    CONSTRAINT uq_customer_payment_allocations_payment_item UNIQUE (payment_id, order_item_id),
    CONSTRAINT uq_customer_payment_allocations_payment_opening UNIQUE (payment_id, opening_obligation_id),
    CONSTRAINT ck_customer_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_customer_payment_allocations_target CHECK (num_nonnulls(order_item_id, opening_obligation_id) = 1),
    CONSTRAINT fk_customer_payment_allocations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_customer_payment_allocations_payment_id ON customer_payment_allocations (payment_id);
CREATE INDEX ix_customer_payment_allocations_order_item_id ON customer_payment_allocations (order_item_id);
CREATE INDEX ix_customer_payment_allocations_opening_obligation_id ON customer_payment_allocations (opening_obligation_id);
CREATE INDEX ix_customer_payment_allocations_created_by ON customer_payment_allocations (created_by);

-- ---------------------------------------------------------------------------------------------
-- supplier_payment_allocations: how much of a supplier payment went to which purchase or opening payable.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE supplier_payment_allocations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID           NOT NULL,
    purchase_id           UUID,
    opening_obligation_id UUID,
    allocated_amount      NUMERIC(14, 2) NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_supplier_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_supplier_payment_allocations_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_supplier_payment_allocations_opening_obligation FOREIGN KEY (opening_obligation_id)
        REFERENCES opening_obligations (id),
    CONSTRAINT uq_supplier_payment_allocations_payment_purchase UNIQUE (payment_id, purchase_id),
    CONSTRAINT uq_supplier_payment_allocations_payment_opening UNIQUE (payment_id, opening_obligation_id),
    CONSTRAINT ck_supplier_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_supplier_payment_allocations_target CHECK (num_nonnulls(purchase_id, opening_obligation_id) = 1),
    CONSTRAINT fk_supplier_payment_allocations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_supplier_payment_allocations_payment_id ON supplier_payment_allocations (payment_id);
CREATE INDEX ix_supplier_payment_allocations_purchase_id ON supplier_payment_allocations (purchase_id);
CREATE INDEX ix_supplier_payment_allocations_opening_obligation_id ON supplier_payment_allocations (opening_obligation_id);
CREATE INDEX ix_supplier_payment_allocations_created_by ON supplier_payment_allocations (created_by);

-- ---------------------------------------------------------------------------------------------
-- idempotency_keys: the stored response of a retry-sensitive POST (ADR-018/033). Inserted in the same
-- transaction as the use case, so a failed request leaves no key and its retry runs normally.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(100) NOT NULL,
    user_id         UUID         NOT NULL,
    request_method  VARCHAR(10)  NOT NULL,
    request_path    VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,                           -- SHA-256 hex of the body
    response_status INTEGER      NOT NULL,
    response_body   TEXT         NOT NULL,
    response_location VARCHAR(255),                                -- Location header of a 201, replayed too
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,                           -- created_at + 48 h

    CONSTRAINT uq_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);

-- ---------------------------------------------------------------------------------------------
-- audit_log: who did a sensitive thing and why. Append-only (immutability trigger arrives with V15).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_user_id UUID,                                              -- NULL for system actions
    action        VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(50)  NOT NULL,
    entity_id     UUID         NOT NULL,                             -- polymorphic, no FK
    reason        VARCHAR(255),
    request_id    VARCHAR(64),
    details       JSONB,                                             -- never secrets or full account numbers

    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_audit_log_action CHECK (action IN (
        'PAYMENT_REVERSED', 'EXPENSE_REVERSED', 'WORK_RECORD_REVERSED', 'INVENTORY_ADJUSTED', 'PARTY_DEACTIVATED',
        'PARTY_ACTIVATED', 'PURCHASE_CANCELLED', 'ORDER_CANCELLED', 'PRODUCTION_CANCELLED', 'OUTSOURCING_CANCELLED',
        'ACCOUNT_CREATED', 'TRANSFER_RECORDED', 'LOAN_CREATED', 'LOGIN_LOCKED', 'REFRESH_TOKEN_REUSE', 'USER_CREATED',
        'USER_DEACTIVATED', 'USER_ROLE_CHANGED', 'OPENING_BALANCE_IMPORTED', 'ACCOUNT_ADJUSTED', 'LOAN_PAYMENT_REVERSED',
        'CHIT_ENTRY_REVERSED'))
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);

-- ---------------------------------------------------------------------------------------------
-- purchase_payables: what each purchase is worth to the supplier, the ONE place assumption Q1 lives in SQL
-- (PLAN_DECISIONS section 3): round((accepted kg - returned kg) x the purchase's rate, 2). Rejected kg are not paid;
-- a return credits the original rate. Allocations are subtracted by the queries that read it.
-- ---------------------------------------------------------------------------------------------
CREATE VIEW purchase_payables AS
SELECT p.id AS purchase_id,
       p.supplier_id,
       p.purchase_number,
       p.purchase_date,
       p.due_date,
       p.status,
       round((coalesce(r.accepted, 0) - coalesce(t.returned, 0)) * p.rate_per_kg, 2) AS payable
FROM purchases p
LEFT JOIN (SELECT purchase_id, sum(accepted_weight_kg) AS accepted FROM material_receipts GROUP BY purchase_id) r
       ON r.purchase_id = p.id
LEFT JOIN (SELECT purchase_id, sum(returned_weight_kg) AS returned FROM supplier_returns GROUP BY purchase_id) t
       ON t.purchase_id = p.id;

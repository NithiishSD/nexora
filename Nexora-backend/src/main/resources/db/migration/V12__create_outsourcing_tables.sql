-- Outsourcing (04-DATA-MODEL section 10): raw silk sent to a manufacturer, warp received back in one or more receipts.
-- Stock moves only through material_movements (OUTSOURCE_ISSUE on issue; OUTSOURCE_RECEIPT and WASTAGE per receipt).

-- ---------------------------------------------------------------------------------------------
-- outsourcing_jobs: one job. received/wastage are running totals of its receipts and never pass the issued weight.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE outsourcing_jobs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_number         VARCHAR(30)    NOT NULL,                      -- JOB-2026-0001
    manufacturer_id    UUID           NOT NULL,                      -- role MANUFACTURER checked by the service
    product_type       VARCHAR(20)    NOT NULL,                      -- the warp expected back
    issued_weight_kg   NUMERIC(12, 3) NOT NULL,                      -- raw silk to send
    received_weight_kg NUMERIC(12, 3) NOT NULL DEFAULT 0,
    wastage_weight_kg  NUMERIC(12, 3) NOT NULL DEFAULT 0,
    rate_per_kg        NUMERIC(14, 2) NOT NULL,                      -- historical, never recalculated
    issue_date         DATE,
    received_date      DATE,                                         -- latest receipt
    status             VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_outsourcing_jobs_job_number UNIQUE (job_number),
    CONSTRAINT fk_outsourcing_jobs_manufacturer FOREIGN KEY (manufacturer_id) REFERENCES parties (id),
    CONSTRAINT fk_outsourcing_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_outsourcing_jobs_product_type CHECK (product_type IN ('SILK_WARP', 'KORA_WARP')),
    CONSTRAINT ck_outsourcing_jobs_issued CHECK (issued_weight_kg > 0),
    CONSTRAINT ck_outsourcing_jobs_received CHECK (received_weight_kg >= 0 AND wastage_weight_kg >= 0),
    -- OUTSOURCE_RECEIPT_EXCEEDED at the database level
    CONSTRAINT ck_outsourcing_jobs_within_issued CHECK (received_weight_kg + wastage_weight_kg <= issued_weight_kg),
    CONSTRAINT ck_outsourcing_jobs_rate CHECK (rate_per_kg >= 0),
    CONSTRAINT ck_outsourcing_jobs_status CHECK (status IN ('CREATED', 'MATERIAL_ISSUED', 'PARTIALLY_RECEIVED',
                                                           'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_outsourcing_jobs_issue_date CHECK (status IN ('CREATED', 'CANCELLED') OR issue_date IS NOT NULL),
    CONSTRAINT ck_outsourcing_jobs_completed CHECK (status <> 'COMPLETED'
                                                    OR received_weight_kg + wastage_weight_kg = issued_weight_kg)
);
CREATE INDEX ix_outsourcing_jobs_manufacturer_status ON outsourcing_jobs (manufacturer_id, status);
CREATE INDEX ix_outsourcing_jobs_issue_date ON outsourcing_jobs (issue_date);
CREATE INDEX ix_outsourcing_jobs_created_by ON outsourcing_jobs (created_by);

-- ---------------------------------------------------------------------------------------------
-- outsourcing_order_items: optional links of a job to the order items it serves (work can run ahead of demand).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE outsourcing_order_items (
    outsourcing_job_id  UUID           NOT NULL,
    order_item_id       UUID           NOT NULL,
    allocated_weight_kg NUMERIC(12, 3) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          UUID,

    CONSTRAINT pk_outsourcing_order_items PRIMARY KEY (outsourcing_job_id, order_item_id),
    CONSTRAINT fk_outsourcing_order_items_job FOREIGN KEY (outsourcing_job_id) REFERENCES outsourcing_jobs (id),
    CONSTRAINT fk_outsourcing_order_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_outsourcing_order_items_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_outsourcing_order_items_weight CHECK (allocated_weight_kg > 0)
);
CREATE INDEX ix_outsourcing_order_items_order_item_id ON outsourcing_order_items (order_item_id);
CREATE INDEX ix_outsourcing_order_items_created_by ON outsourcing_order_items (created_by);

-- ---------------------------------------------------------------------------------------------
-- manufacturer_payment_allocations: how a MANUFACTURER payment settles jobs or opening payables (append-only;
-- the immutability trigger arrives with V15, as for the other allocation tables).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE manufacturer_payment_allocations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID           NOT NULL,
    outsourcing_job_id    UUID,
    opening_obligation_id UUID,
    allocated_amount      NUMERIC(14, 2) NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_manufacturer_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_manufacturer_payment_allocations_job FOREIGN KEY (outsourcing_job_id) REFERENCES outsourcing_jobs (id),
    CONSTRAINT fk_manufacturer_payment_allocations_opening_obligation FOREIGN KEY (opening_obligation_id)
        REFERENCES opening_obligations (id),
    CONSTRAINT fk_manufacturer_payment_allocations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uq_manufacturer_payment_allocations_payment_job UNIQUE (payment_id, outsourcing_job_id),
    CONSTRAINT uq_manufacturer_payment_allocations_payment_opening UNIQUE (payment_id, opening_obligation_id),
    CONSTRAINT ck_manufacturer_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_manufacturer_payment_allocations_target CHECK (num_nonnulls(outsourcing_job_id, opening_obligation_id) = 1)
);
CREATE INDEX ix_manufacturer_payment_allocations_payment_id ON manufacturer_payment_allocations (payment_id);
CREATE INDEX ix_manufacturer_payment_allocations_outsourcing_job_id ON manufacturer_payment_allocations (outsourcing_job_id);
CREATE INDEX ix_manufacturer_payment_allocations_opening_obligation_id
    ON manufacturer_payment_allocations (opening_obligation_id);
CREATE INDEX ix_manufacturer_payment_allocations_created_by ON manufacturer_payment_allocations (created_by);

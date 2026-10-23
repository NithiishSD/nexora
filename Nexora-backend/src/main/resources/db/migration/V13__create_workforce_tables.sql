-- Workforce (04-DATA-MODEL section 11): work done by workers (rolling by the hour, warping by the kg) and how worker
-- payments settle it. The rate is copied into each record and the amount is frozen.

-- ---------------------------------------------------------------------------------------------
-- work_records: one piece of work. Amounts are never edited; a mistaken record is REVERSED, never deleted.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE work_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id           UUID           NOT NULL,                     -- role WORKER checked by the service
    work_date           DATE           NOT NULL,
    work_type           VARCHAR(10)    NOT NULL,                     -- ROLLING (hours) / WARPING (kg)
    hours               NUMERIC(8, 2),                               -- ROLLING only
    quantity_kg         NUMERIC(12, 3),                              -- WARPING only
    rate                NUMERIC(14, 2) NOT NULL,                     -- per hour or per kg, copied at recording
    amount              NUMERIC(14, 2) NOT NULL,                     -- round(units x rate, 2), frozen
    production_batch_id UUID,
    status              VARCHAR(10)    NOT NULL DEFAULT 'RECORDED',
    reversed_at         TIMESTAMPTZ,
    reversal_reason     VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          UUID,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_work_records_worker FOREIGN KEY (worker_id) REFERENCES parties (id),
    CONSTRAINT fk_work_records_production_batch FOREIGN KEY (production_batch_id) REFERENCES production_batches (id),
    CONSTRAINT fk_work_records_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_work_records_type CHECK (work_type IN ('ROLLING', 'WARPING')),
    CONSTRAINT ck_work_records_units CHECK ((work_type = 'ROLLING' AND hours > 0 AND quantity_kg IS NULL)
                                            OR (work_type = 'WARPING' AND quantity_kg > 0 AND hours IS NULL)),
    CONSTRAINT ck_work_records_rate CHECK (rate >= 0),
    CONSTRAINT ck_work_records_amount CHECK (amount = round(COALESCE(hours, quantity_kg) * rate, 2)),
    CONSTRAINT ck_work_records_status CHECK (status IN ('RECORDED', 'REVERSED')),
    CONSTRAINT ck_work_records_reversal CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL
                                                                          AND reversal_reason IS NOT NULL))
);
CREATE INDEX ix_work_records_worker_date ON work_records (worker_id, work_date);
CREATE INDEX ix_work_records_production_batch_id ON work_records (production_batch_id);
CREATE INDEX ix_work_records_status ON work_records (status);
CREATE INDEX ix_work_records_created_by ON work_records (created_by);

-- ---------------------------------------------------------------------------------------------
-- worker_payment_allocations: how a WORKER payment settles work records or opening payables (append-only; the
-- immutability trigger arrives with V15, as for the other allocation tables).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE worker_payment_allocations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID           NOT NULL,
    work_record_id        UUID,
    opening_obligation_id UUID,
    allocated_amount      NUMERIC(14, 2) NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_worker_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_worker_payment_allocations_work_record FOREIGN KEY (work_record_id) REFERENCES work_records (id),
    CONSTRAINT fk_worker_payment_allocations_opening_obligation FOREIGN KEY (opening_obligation_id)
        REFERENCES opening_obligations (id),
    CONSTRAINT fk_worker_payment_allocations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uq_worker_payment_allocations_payment_record UNIQUE (payment_id, work_record_id),
    CONSTRAINT uq_worker_payment_allocations_payment_opening UNIQUE (payment_id, opening_obligation_id),
    CONSTRAINT ck_worker_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_worker_payment_allocations_target CHECK (num_nonnulls(work_record_id, opening_obligation_id) = 1)
);
CREATE INDEX ix_worker_payment_allocations_payment_id ON worker_payment_allocations (payment_id);
CREATE INDEX ix_worker_payment_allocations_work_record_id ON worker_payment_allocations (work_record_id);
CREATE INDEX ix_worker_payment_allocations_opening_obligation_id ON worker_payment_allocations (opening_obligation_id);
CREATE INDEX ix_worker_payment_allocations_created_by ON worker_payment_allocations (created_by);

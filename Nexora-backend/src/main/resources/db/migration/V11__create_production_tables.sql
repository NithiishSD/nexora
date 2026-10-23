-- Production (04-DATA-MODEL section 9): in-house batches that turn raw silk into silk or kora warp. Stock moves only
-- through material_movements (CONSUMPTION on start; PRODUCTION_OUTPUT and WASTAGE on completion).

-- ---------------------------------------------------------------------------------------------
-- production_batches: one batch, from plan to reconciliation (input = output + wastage + discrepancy).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE production_batches (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_number          VARCHAR(30)    NOT NULL,                   -- BAT-2026-0001
    production_date       DATE           NOT NULL,
    product_type          VARCHAR(20)    NOT NULL,                   -- the warp being made, never vuda
    input_weight_kg       NUMERIC(12, 3) NOT NULL,                   -- raw silk to consume
    output_weight_kg      NUMERIC(12, 3),                            -- set on completion
    wastage_weight_kg     NUMERIC(12, 3),
    discrepancy_weight_kg NUMERIC(12, 3),                            -- explicit and visible, never silent
    status                VARCHAR(15)    NOT NULL DEFAULT 'PLANNED',
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    notes                 TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_production_batches_batch_number UNIQUE (batch_number),
    CONSTRAINT ck_production_batches_product_type CHECK (product_type IN ('SILK_WARP', 'KORA_WARP')),
    CONSTRAINT ck_production_batches_input CHECK (input_weight_kg > 0),
    CONSTRAINT ck_production_batches_results CHECK ((output_weight_kg IS NULL OR output_weight_kg >= 0)
        AND (wastage_weight_kg IS NULL OR wastage_weight_kg >= 0)
        AND (discrepancy_weight_kg IS NULL OR discrepancy_weight_kg >= 0)),
    CONSTRAINT ck_production_batches_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_production_batches_started CHECK (status NOT IN ('IN_PROGRESS', 'COMPLETED') OR started_at IS NOT NULL),
    -- PRODUCTION_NOT_RECONCILED at the database level
    CONSTRAINT ck_production_batches_reconciled CHECK (status <> 'COMPLETED' OR (output_weight_kg IS NOT NULL
        AND wastage_weight_kg IS NOT NULL AND discrepancy_weight_kg IS NOT NULL AND completed_at IS NOT NULL
        AND input_weight_kg = output_weight_kg + wastage_weight_kg + discrepancy_weight_kg)),
    CONSTRAINT fk_production_batches_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_production_batches_status ON production_batches (status);
CREATE INDEX ix_production_batches_production_date ON production_batches (production_date);
CREATE INDEX ix_production_batches_created_by ON production_batches (created_by);

-- ---------------------------------------------------------------------------------------------
-- production_order_items: which order items a batch serves, with the weight meant for each (many-to-many).
-- Replaced as a whole while the batch is PLANNED; fixed once it starts.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE production_order_items (
    production_batch_id UUID           NOT NULL,
    order_item_id       UUID           NOT NULL,
    allocated_weight_kg NUMERIC(12, 3) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          UUID,

    CONSTRAINT pk_production_order_items PRIMARY KEY (production_batch_id, order_item_id),
    CONSTRAINT fk_production_order_items_batch FOREIGN KEY (production_batch_id) REFERENCES production_batches (id),
    CONSTRAINT fk_production_order_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_production_order_items_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_production_order_items_weight CHECK (allocated_weight_kg > 0)
);
CREATE INDEX ix_production_order_items_order_item_id ON production_order_items (order_item_id);
CREATE INDEX ix_production_order_items_created_by ON production_order_items (created_by);

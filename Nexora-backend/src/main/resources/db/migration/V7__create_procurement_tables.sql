-- Procurement (04-DATA-MODEL section 5): what was bought, what arrived, what went back.
-- Stock itself lives in the inventory ledger: a receipt's accepted kg and a return become material_movements
-- that point back here through (reference_type, reference_id).

-- ---------------------------------------------------------------------------------------------
-- purchases: what was agreed with a supplier and on what terms. Not stock, not money.
-- No total column: the value is ordered kg x rate, computed on read (DOMAIN_RULES rule 2).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE purchases (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_number   VARCHAR(30)    NOT NULL,                        -- PUR-2026-0001
    supplier_id       UUID           NOT NULL,
    product_type      VARCHAR(20)    NOT NULL DEFAULT 'RAW_SILK',
    purchase_date     DATE           NOT NULL,
    ordered_weight_kg NUMERIC(12, 3) NOT NULL,
    rate_per_kg       NUMERIC(14, 2) NOT NULL,                        -- historical: never recalculated
    payment_type      VARCHAR(10)    NOT NULL,
    credit_days       INTEGER        NOT NULL DEFAULT 0,
    due_date          DATE           NOT NULL,                        -- purchase_date + credit_days
    bill_reference    VARCHAR(100),                                   -- the supplier's bill number
    status            VARCHAR(30)    NOT NULL DEFAULT 'ORDERED',
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_purchases_purchase_number UNIQUE (purchase_number),
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES parties (id),
    -- only trading stock is bought: raw silk and vuda warp (warp is made, never purchased)
    CONSTRAINT ck_purchases_product_type CHECK (product_type IN ('RAW_SILK', 'VUDA_WARP')),
    CONSTRAINT ck_purchases_ordered_weight CHECK (ordered_weight_kg > 0),
    CONSTRAINT ck_purchases_rate CHECK (rate_per_kg >= 0),
    CONSTRAINT ck_purchases_payment_type CHECK (payment_type IN ('CASH', 'CREDIT')),
    CONSTRAINT ck_purchases_credit_days CHECK (credit_days >= 0),
    -- a cash purchase is due on the day it is made
    CONSTRAINT ck_purchases_cash_credit_days CHECK (payment_type = 'CREDIT' OR credit_days = 0),
    CONSTRAINT ck_purchases_due_date CHECK (due_date = purchase_date + credit_days),
    CONSTRAINT ck_purchases_status CHECK (status IN ('ORDERED', 'PARTIALLY_RECEIVED', 'FULLY_RECEIVED', 'CANCELLED')),
    CONSTRAINT fk_purchases_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_purchases_supplier_status ON purchases (supplier_id, status);
CREATE INDEX ix_purchases_purchase_date ON purchases (purchase_date);
CREATE INDEX ix_purchases_due_date ON purchases (due_date);
CREATE INDEX ix_purchases_created_by ON purchases (created_by);

-- ---------------------------------------------------------------------------------------------
-- material_receipts: one physical arrival against a purchase (append-only). Only the accepted kg
-- enters stock. "Sum of received <= ordered" spans rows, so the service checks it while holding a
-- lock on the purchases row.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE material_receipts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number     VARCHAR(30)    NOT NULL,                       -- RCT-2026-0001
    purchase_id        UUID           NOT NULL,
    receipt_date       DATE           NOT NULL,
    received_weight_kg NUMERIC(12, 3) NOT NULL,
    accepted_weight_kg NUMERIC(12, 3) NOT NULL,
    rejected_weight_kg NUMERIC(12, 3) NOT NULL,
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_material_receipts_receipt_number UNIQUE (receipt_number),
    CONSTRAINT fk_material_receipts_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT ck_material_receipts_received CHECK (received_weight_kg > 0),
    CONSTRAINT ck_material_receipts_accepted CHECK (accepted_weight_kg >= 0),
    CONSTRAINT ck_material_receipts_rejected CHECK (rejected_weight_kg >= 0),
    -- accepted and rejected may leave a part unclassified, but can never exceed what arrived
    CONSTRAINT ck_material_receipts_split CHECK (accepted_weight_kg + rejected_weight_kg <= received_weight_kg),
    CONSTRAINT fk_material_receipts_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_material_receipts_purchase_id ON material_receipts (purchase_id);
CREATE INDEX ix_material_receipts_receipt_date ON material_receipts (receipt_date);
CREATE INDEX ix_material_receipts_created_by ON material_receipts (created_by);

-- ---------------------------------------------------------------------------------------------
-- supplier_returns: material sent back to the supplier (append-only). The original receipt is never
-- rewritten. The PURCHASE_RETURN movement points at this row (reference_type 'SUPPLIER_RETURN').
-- ---------------------------------------------------------------------------------------------
CREATE TABLE supplier_returns (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_number      VARCHAR(30)    NOT NULL,                       -- RTN-2026-0001
    purchase_id        UUID           NOT NULL,
    return_date        DATE           NOT NULL,
    returned_weight_kg NUMERIC(12, 3) NOT NULL,
    reason             VARCHAR(255)   NOT NULL,
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_supplier_returns_return_number UNIQUE (return_number),
    CONSTRAINT fk_supplier_returns_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT ck_supplier_returns_weight CHECK (returned_weight_kg > 0),
    CONSTRAINT ck_supplier_returns_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT fk_supplier_returns_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_supplier_returns_purchase_id ON supplier_returns (purchase_id);
CREATE INDEX ix_supplier_returns_created_by ON supplier_returns (created_by);

-- Customer returns (docs/specs/SPEC-CUSTOMER-RETURNS.md, FR-ORD-20, FR-INV-17).
-- Append-only return records: goods sent back by a customer for an order item.
-- Usable goods return to stock (FINISHED_STOCK for warp, RAW_STOCK for raw silk/vuda);
-- damaged goods return directly to WASTAGE.

-- ---------------------------------------------------------------------------------------------
-- 1. Relax material_movements to allow CUSTOMER_RETURN to move directly to WASTAGE when damaged.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE material_movements DROP CONSTRAINT ck_material_movements_type_locations;
ALTER TABLE material_movements ADD CONSTRAINT ck_material_movements_type_locations CHECK (
    (movement_type = 'OPENING_BALANCE' AND from_location IS NULL
        AND to_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK'))
    OR (movement_type = 'RECEIPT' AND from_location = 'SUPPLIER' AND to_location = 'RAW_STOCK')
    OR (movement_type = 'PURCHASE_RETURN' AND from_location = 'RAW_STOCK' AND to_location = 'RETURNED')
    OR (movement_type = 'DIRECT_SALE' AND from_location = 'RAW_STOCK' AND to_location = 'CUSTOMER')
    OR (movement_type = 'CONSUMPTION' AND from_location = 'RAW_STOCK' AND to_location = 'INTERNAL_WIP')
    OR (movement_type = 'PRODUCTION_OUTPUT' AND from_location = 'INTERNAL_WIP' AND to_location = 'FINISHED_STOCK')
    OR (movement_type = 'WASTAGE' AND from_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP')
        AND to_location = 'WASTAGE')
    OR (movement_type = 'OUTSOURCE_ISSUE' AND from_location = 'RAW_STOCK' AND to_location = 'EXTERNAL_WIP')
    OR (movement_type = 'OUTSOURCE_RECEIPT' AND from_location = 'EXTERNAL_WIP' AND to_location = 'FINISHED_STOCK')
    OR (movement_type = 'DELIVERY' AND from_location = 'FINISHED_STOCK' AND to_location = 'CUSTOMER')
    OR (movement_type = 'CUSTOMER_RETURN' AND from_location = 'CUSTOMER'
        AND to_location IN ('FINISHED_STOCK', 'RAW_STOCK', 'WASTAGE'))
    OR (movement_type = 'ADJUSTMENT' AND from_location IS NOT NULL
        AND (from_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
             OR to_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')))
);

ALTER TABLE material_movements DROP CONSTRAINT ck_material_movements_reference_type;
ALTER TABLE material_movements ADD CONSTRAINT ck_material_movements_reference_type CHECK (
    reference_type IS NULL OR reference_type IN (
        'PURCHASE', 'MATERIAL_RECEIPT', 'SUPPLIER_RETURN', 'ORDER_ITEM',
        'PRODUCTION_BATCH', 'OUTSOURCING_JOB', 'CUSTOMER_RETURN'
    )
);

-- ---------------------------------------------------------------------------------------------
-- 2. customer_returns: material returned by a customer for an order item (append-only).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE customer_returns (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_number      VARCHAR(30)    NOT NULL,                       -- CRN-2026-0001
    order_id           UUID           NOT NULL,
    order_item_id      UUID           NOT NULL,
    return_date        DATE           NOT NULL,
    returned_weight_kg NUMERIC(12, 3) NOT NULL,
    usable             BOOLEAN        NOT NULL DEFAULT true,
    reason             VARCHAR(255)   NOT NULL,
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_customer_returns_return_number UNIQUE (return_number),
    CONSTRAINT fk_customer_returns_order FOREIGN KEY (order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_customer_returns_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_customer_returns_weight CHECK (returned_weight_kg > 0),
    CONSTRAINT ck_customer_returns_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT fk_customer_returns_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX ix_customer_returns_order_id ON customer_returns (order_id);
CREATE INDEX ix_customer_returns_order_item_id ON customer_returns (order_item_id);
CREATE INDEX ix_customer_returns_return_date ON customer_returns (return_date);
CREATE INDEX ix_customer_returns_created_by ON customer_returns (created_by);

-- ---------------------------------------------------------------------------------------------
-- 3. Immutability trigger: rows can never be updated or deleted.
-- ---------------------------------------------------------------------------------------------
CREATE TRIGGER trg_customer_returns_immutable BEFORE UPDATE OR DELETE ON customer_returns
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();

-- ---------------------------------------------------------------------------------------------
-- 4. Role privilege restriction for nexora_app.
-- ---------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexora_app') THEN
        REVOKE UPDATE, DELETE, TRUNCATE ON customer_returns FROM nexora_app;
    END IF;
END
$$;

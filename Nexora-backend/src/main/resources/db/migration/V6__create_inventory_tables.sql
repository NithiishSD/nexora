-- Inventory (04-DATA-MODEL section 4): the stock ledger and its balance projection.
-- No foreign keys to other modules: a movement points at its source record polymorphically
-- (reference_type + reference_id), so inventory does not depend on procurement, orders, production ...

-- ---------------------------------------------------------------------------------------------
-- inventory_balances: current stock per (product, internal location). A cached copy of
-- "sum of movements in - sum of movements out", updated in the same transaction as each movement.
-- The row is also the lock anchor: InventoryService locks it (SELECT ... FOR UPDATE) before checking.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE inventory_balances (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_type VARCHAR(20)    NOT NULL,
    location     VARCHAR(20)    NOT NULL,
    quantity_kg  NUMERIC(12, 3) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by   UUID,
    version      BIGINT         NOT NULL DEFAULT 0,

    -- one row per product and location; rows are created on first use (INSERT ... ON CONFLICT DO NOTHING)
    CONSTRAINT uq_inventory_balances_product_location UNIQUE (product_type, location),
    CONSTRAINT ck_inventory_balances_product CHECK (product_type IN ('RAW_SILK', 'SILK_WARP', 'KORA_WARP', 'VUDA_WARP')),
    -- balances exist only for the four internal locations
    CONSTRAINT ck_inventory_balances_location CHECK (location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')),
    -- stock can never go negative: the last line of defence behind the locked check in the service (ADR-026)
    CONSTRAINT ck_inventory_balances_quantity CHECK (quantity_kg >= 0),
    -- raw stock holds raw silk and vuda warp (trading stock); WIP and finished stock hold silk and kora warp
    CONSTRAINT ck_inventory_balances_location_product CHECK (
        (location = 'RAW_STOCK' AND product_type IN ('RAW_SILK', 'VUDA_WARP'))
        OR (location IN ('INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK') AND product_type IN ('SILK_WARP', 'KORA_WARP'))),
    CONSTRAINT fk_inventory_balances_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_inventory_balances_created_by ON inventory_balances (created_by);

-- ---------------------------------------------------------------------------------------------
-- material_movements: the inventory ledger. Every stock change is exactly one row, never updated
-- or deleted. Quantities are positive; the direction comes from from_location -> to_location.
-- product_type is what leaves from_location, to_product_type what arrives at to_location; they
-- differ only when raw silk enters work in progress as the warp type being made.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE material_movements (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movement_number  VARCHAR(30)    NOT NULL,              -- MOV-2026-0001
    product_type     VARCHAR(20)    NOT NULL,
    to_product_type  VARCHAR(20)    NOT NULL,
    movement_type    VARCHAR(30)    NOT NULL,
    quantity_kg      NUMERIC(12, 3) NOT NULL,
    from_location    VARCHAR(20),                          -- NULL only for OPENING_BALANCE
    to_location      VARCHAR(20)    NOT NULL,
    movement_date    DATE           NOT NULL,              -- business date
    reference_type   VARCHAR(30),                          -- which kind of record caused it
    reference_id     UUID,                                 -- its id (no FK: polymorphic)
    reference_number VARCHAR(30),                          -- its business number, copied for history
    reason           VARCHAR(255),                         -- required for ADJUSTMENT
    notes            TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       UUID,
    version          BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_material_movements_movement_number UNIQUE (movement_number),
    CONSTRAINT ck_material_movements_product CHECK (
        product_type IN ('RAW_SILK', 'SILK_WARP', 'KORA_WARP', 'VUDA_WARP')
        AND to_product_type IN ('RAW_SILK', 'SILK_WARP', 'KORA_WARP', 'VUDA_WARP')),
    CONSTRAINT ck_material_movements_movement_type CHECK (movement_type IN (
        'OPENING_BALANCE', 'RECEIPT', 'PURCHASE_RETURN', 'DIRECT_SALE', 'CONSUMPTION', 'PRODUCTION_OUTPUT',
        'WASTAGE', 'OUTSOURCE_ISSUE', 'OUTSOURCE_RECEIPT', 'DELIVERY', 'CUSTOMER_RETURN', 'ADJUSTMENT')),
    CONSTRAINT ck_material_movements_quantity CHECK (quantity_kg > 0),
    CONSTRAINT ck_material_movements_locations CHECK (
        (from_location IS NULL OR from_location IN ('SUPPLIER', 'RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP',
                                                    'FINISHED_STOCK', 'CUSTOMER', 'WASTAGE', 'RETURNED'))
        AND to_location IN ('SUPPLIER', 'RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP',
                            'FINISHED_STOCK', 'CUSTOMER', 'WASTAGE', 'RETURNED')
        AND (from_location IS NULL OR from_location <> to_location)),
    -- the (type, from, to) table of DOMAIN_RULES section 3
    CONSTRAINT ck_material_movements_type_locations CHECK (
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
            AND to_location IN ('FINISHED_STOCK', 'RAW_STOCK'))
        -- an adjustment needs both sides and at least one internal one (between two outside places nothing changes)
        OR (movement_type = 'ADJUSTMENT' AND from_location IS NOT NULL
            AND (from_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
                 OR to_location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')))),
    -- raw silk becomes warp only when it enters work in progress
    CONSTRAINT ck_material_movements_product_conversion CHECK (
        to_product_type = product_type
        OR (movement_type IN ('CONSUMPTION', 'OUTSOURCE_ISSUE') AND product_type = 'RAW_SILK'
            AND to_product_type IN ('SILK_WARP', 'KORA_WARP'))),
    -- finished warp leaves through DELIVERY; only raw silk and vuda warp are sold directly
    CONSTRAINT ck_material_movements_direct_sale CHECK (
        movement_type <> 'DIRECT_SALE' OR product_type IN ('RAW_SILK', 'VUDA_WARP')),
    -- vuda warp is trade-only: never consumed, issued or produced
    CONSTRAINT ck_material_movements_vuda CHECK (
        movement_type NOT IN ('CONSUMPTION', 'OUTSOURCE_ISSUE', 'OUTSOURCE_RECEIPT', 'PRODUCTION_OUTPUT')
        OR (product_type <> 'VUDA_WARP' AND to_product_type <> 'VUDA_WARP')),
    -- an internal location only ever holds its allowed products (same table as the balances);
    -- the supplier only ever supplies raw silk or vuda warp
    CONSTRAINT ck_material_movements_location_products CHECK (
        (from_location IS NULL
            OR from_location IN ('CUSTOMER', 'WASTAGE', 'RETURNED')
            OR (from_location IN ('SUPPLIER', 'RAW_STOCK') AND product_type IN ('RAW_SILK', 'VUDA_WARP'))
            OR (from_location IN ('INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
                AND product_type IN ('SILK_WARP', 'KORA_WARP')))
        AND (to_location IN ('SUPPLIER', 'CUSTOMER', 'WASTAGE', 'RETURNED')
            OR (to_location = 'RAW_STOCK' AND to_product_type IN ('RAW_SILK', 'VUDA_WARP'))
            OR (to_location IN ('INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
                AND to_product_type IN ('SILK_WARP', 'KORA_WARP')))),
    -- every movement except opening stock and manual adjustments points at the record that caused it
    CONSTRAINT ck_material_movements_reference CHECK (
        (reference_type IS NULL) = (reference_id IS NULL)
        AND (reference_id IS NULL OR reference_number IS NOT NULL)
        AND (movement_type IN ('OPENING_BALANCE', 'ADJUSTMENT', 'WASTAGE') OR reference_id IS NOT NULL)),
    CONSTRAINT ck_material_movements_reference_type CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE', 'MATERIAL_RECEIPT', 'SUPPLIER_RETURN', 'ORDER_ITEM', 'PRODUCTION_BATCH', 'OUTSOURCING_JOB')),
    CONSTRAINT ck_material_movements_reason CHECK (
        movement_type <> 'ADJUSTMENT' OR (reason IS NOT NULL AND length(trim(reason)) > 0)),
    CONSTRAINT fk_material_movements_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_material_movements_product_date ON material_movements (product_type, movement_date);
CREATE INDEX ix_material_movements_to_product ON material_movements (to_product_type, to_location);
CREATE INDEX ix_material_movements_from ON material_movements (product_type, from_location);
CREATE INDEX ix_material_movements_reference ON material_movements (reference_type, reference_id);
CREATE INDEX ix_material_movements_type ON material_movements (movement_type);
CREATE INDEX ix_material_movements_created_by ON material_movements (created_by);
-- opening stock is entered once per product and location (FR-INV-13); later corrections are adjustments
CREATE UNIQUE INDEX uq_material_movements_opening_balance ON material_movements (to_product_type, to_location)
    WHERE movement_type = 'OPENING_BALANCE';

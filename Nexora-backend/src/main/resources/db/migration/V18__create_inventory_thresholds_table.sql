-- Inventory thresholds for low-stock warnings and alerts (FR-INV-18, FR-PROC-20, FR-NTF-07).
-- Configurable minimum stock weights per legal (product_type, location) pair.

CREATE TABLE inventory_thresholds (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_type     VARCHAR(20)    NOT NULL,
    location         VARCHAR(20)    NOT NULL,
    min_threshold_kg NUMERIC(12, 3) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       UUID,
    version          BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_inventory_thresholds_product_location UNIQUE (product_type, location),
    CONSTRAINT ck_inventory_thresholds_product CHECK (product_type IN ('RAW_SILK', 'SILK_WARP', 'KORA_WARP', 'VUDA_WARP')),
    CONSTRAINT ck_inventory_thresholds_location CHECK (location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')),
    CONSTRAINT ck_inventory_thresholds_min_weight CHECK (min_threshold_kg >= 0),
    CONSTRAINT ck_inventory_thresholds_location_product CHECK (
        (location = 'RAW_STOCK' AND product_type IN ('RAW_SILK', 'VUDA_WARP'))
        OR (location IN ('INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK') AND product_type IN ('SILK_WARP', 'KORA_WARP'))),
    CONSTRAINT fk_inventory_thresholds_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_inventory_thresholds_created_by ON inventory_thresholds (created_by);

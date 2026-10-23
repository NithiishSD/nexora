-- Customer orders (04-DATA-MODEL section 6): what a customer asked for, at which agreed rates.
-- Stock leaves through material_movements (DELIVERY / DIRECT_SALE) that point back at the order items;
-- money arrives through payments and their allocations. Payment status and "locked" are derived, never stored.

-- ---------------------------------------------------------------------------------------------
-- customer_orders: the order header and its operational status.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE customer_orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number  VARCHAR(30) NOT NULL,                               -- ORD-2026-0001
    customer_id   UUID        NOT NULL,
    order_date    DATE        NOT NULL,
    required_date DATE,                                               -- expected delivery
    delivered_on  DATE,                                               -- set when delivered
    status        VARCHAR(20) NOT NULL DEFAULT 'PLACED',
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uq_customer_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_customer_orders_customer FOREIGN KEY (customer_id) REFERENCES parties (id),
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('PLACED', 'CONFIRMED', 'IN_PROGRESS', 'READY', 'DELIVERED',
                                                            'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_customer_orders_required_date CHECK (required_date IS NULL OR required_date >= order_date),
    -- a delivery date exists exactly when the goods have left
    CONSTRAINT ck_customer_orders_delivered CHECK ((status IN ('DELIVERED', 'COMPLETED')) = (delivered_on IS NOT NULL)),
    CONSTRAINT fk_customer_orders_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_customer_orders_customer_status ON customer_orders (customer_id, status);
CREATE INDEX ix_customer_orders_status ON customer_orders (status);
CREATE INDEX ix_customer_orders_order_date ON customer_orders (order_date);
CREATE INDEX ix_customer_orders_required_date ON customer_orders (required_date);
CREATE INDEX ix_customer_orders_created_by ON customer_orders (created_by);

-- ---------------------------------------------------------------------------------------------
-- order_items: one product line of an order. The rate is the agreed historical rate and the amount is
-- frozen when the line is written: round(weight x rate, 2), checked by the database as well.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE order_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID           NOT NULL,
    line_no            INTEGER        NOT NULL,                       -- 1-based position in the order
    product_type       VARCHAR(20)    NOT NULL,
    required_weight_kg NUMERIC(12, 3) NOT NULL,
    rate_per_kg        NUMERIC(14, 2) NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    warp_specification TEXT,
    status             VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES customer_orders (id),
    CONSTRAINT uq_order_items_order_line UNIQUE (order_id, line_no),
    CONSTRAINT ck_order_items_line_no CHECK (line_no > 0),
    -- raw silk and vuda warp are sold through orders too (direct sale, assumption Q7)
    CONSTRAINT ck_order_items_product_type CHECK (product_type IN ('RAW_SILK', 'SILK_WARP', 'KORA_WARP', 'VUDA_WARP')),
    CONSTRAINT ck_order_items_weight CHECK (required_weight_kg > 0),
    CONSTRAINT ck_order_items_rate CHECK (rate_per_kg >= 0),
    -- PostgreSQL's round(numeric, 2) rounds halves away from zero, the same as HALF_UP for these positive values
    CONSTRAINT ck_order_items_amount CHECK (amount = round(required_weight_kg * rate_per_kg, 2)),
    CONSTRAINT ck_order_items_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT fk_order_items_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_order_items_order_id ON order_items (order_id);
CREATE INDEX ix_order_items_status ON order_items (status);
CREATE INDEX ix_order_items_product_status ON order_items (product_type, status);
CREATE INDEX ix_order_items_created_by ON order_items (created_by);

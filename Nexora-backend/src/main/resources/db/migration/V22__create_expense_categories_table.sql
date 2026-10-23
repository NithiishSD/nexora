-- Configurable expense categories (FR-FIN-19, E-FIN-17)

CREATE TABLE expense_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expense_categories_name UNIQUE (name)
);

-- Seed initial standard categories (FR-FIN-11)
INSERT INTO expense_categories (id, name, active, created_at) VALUES
    (gen_random_uuid(), 'TRANSPORT', true, NOW()),
    (gen_random_uuid(), 'ELECTRICITY', true, NOW()),
    (gen_random_uuid(), 'RENT', true, NOW()),
    (gen_random_uuid(), 'WORKER', true, NOW()),
    (gen_random_uuid(), 'MAINTENANCE', true, NOW()),
    (gen_random_uuid(), 'PACKING', true, NOW()),
    (gen_random_uuid(), 'OTHER', true, NOW());

-- Allow dynamic category names up to 30 characters
ALTER TABLE expenses DROP CONSTRAINT ck_expenses_category;
ALTER TABLE expenses ALTER COLUMN category TYPE VARCHAR(30);

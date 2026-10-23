CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------------------------
-- users: people who log in to the app (not the same thing as parties)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    display_name          VARCHAR(200) NOT NULL,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_username UNIQUE (username),
    -- usernames are stored in lower case so "Owner" and "owner" can never be two accounts
    CONSTRAINT ck_users_username_lower CHECK (username = lower(username)),
    CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_users_created_by ON users (created_by);

-- ---------------------------------------------------------------------------------------------
-- parties: every external person or business (customer, supplier, manufacturer, worker, lender)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE parties (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_code                    VARCHAR(30)  NOT NULL,              -- human id, e.g. PTY-2026-0001
    name                          VARCHAR(200) NOT NULL,
    address_line                  VARCHAR(255),
    area                          VARCHAR(155),
    city                          VARCHAR(100),
    district                      VARCHAR(100),
    state                         VARCHAR(50),
    pincode                       VARCHAR(20),
    note                          TEXT,
    status                        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- supplier terms: how many days of credit this supplier normally gives (pre-fills new purchases)
    default_credit_days           INTEGER,
    -- worker rates: copied into each work record when it is created (history is never recalculated)
    default_rolling_rate_per_hour NUMERIC(14, 2),
    default_warping_rate_per_kg   NUMERIC(14, 2),
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                    UUID,
    version                       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_parties_party_code UNIQUE (party_code),
    CONSTRAINT ck_parties_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_parties_default_credit_days CHECK (default_credit_days IS NULL OR default_credit_days >= 0),
    CONSTRAINT ck_parties_default_rolling_rate CHECK (default_rolling_rate_per_hour IS NULL OR default_rolling_rate_per_hour >= 0),
    CONSTRAINT ck_parties_default_warping_rate CHECK (default_warping_rate_per_kg IS NULL OR default_warping_rate_per_kg >= 0),
    CONSTRAINT fk_parties_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_parties_name_lower ON parties (lower(name));
CREATE INDEX ix_parties_status ON parties (status);
CREATE INDEX ix_parties_created_by ON parties (created_by);

-- one party can hold several roles (a supplier who is also a customer)
CREATE TABLE party_roles (
    party_id UUID        NOT NULL,
    role     VARCHAR(30) NOT NULL,

    CONSTRAINT pk_party_roles PRIMARY KEY (party_id, role),
    CONSTRAINT fk_party_roles_party FOREIGN KEY (party_id) REFERENCES parties (id),
    CONSTRAINT ck_party_roles_role CHECK (role IN ('CUSTOMER', 'SUPPLIER', 'MANUFACTURER', 'WORKER', 'LENDER'))
);
CREATE INDEX ix_party_roles_role ON party_roles (role);

-- phone numbers are NOT unique: family members share numbers
CREATE TABLE party_phone_numbers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id     UUID        NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    label        VARCHAR(50) NOT NULL,
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_party_phone_numbers_party FOREIGN KEY (party_id) REFERENCES parties (id)
);
CREATE INDEX ix_party_phone_numbers_party_id ON party_phone_numbers (party_id);
-- at most one primary number per party
CREATE UNIQUE INDEX uq_party_phone_numbers_one_primary ON party_phone_numbers (party_id) WHERE is_primary;

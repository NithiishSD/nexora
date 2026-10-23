-- Roles, permissions and refresh tokens (ADR-014). The users table itself is created in V3.

-- the fixed list of permission codes; mirrors the Permission enum in the code
CREATE TABLE permissions (
    code VARCHAR(60) PRIMARY KEY
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT fk_roles_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_roles_created_by ON roles (created_by);

CREATE TABLE role_permissions (
    role_id         UUID        NOT NULL,
    permission_code VARCHAR(60) NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_code);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- Only the SHA-256 hash of a refresh token is stored, never the token itself.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_created_by ON refresh_tokens (created_by);

-- ---------------------------------------------------------------------------------------------
-- seed data
-- ---------------------------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    ('USER_MANAGE'),
    ('PARTY_VIEW'), ('PARTY_MANAGE'),
    ('PURCHASE_VIEW'), ('PURCHASE_CREATE'),
    ('INVENTORY_VIEW'), ('INVENTORY_ADJUST'),
    ('ORDER_VIEW'), ('ORDER_MANAGE'),
    ('PRODUCTION_VIEW'), ('PRODUCTION_MANAGE'), ('OUTSOURCING_MANAGE'), ('WORK_RECORD_MANAGE'),
    ('PAYMENT_VIEW'), ('PAYMENT_CREATE'), ('PAYMENT_REVERSE'),
    ('FINANCE_VIEW'), ('FINANCE_MANAGE'), ('EXPENSE_CREATE'), ('LOAN_MANAGE'), ('CHIT_MANAGE'),
    ('REPORT_VIEW'), ('DOCUMENT_MANAGE');

INSERT INTO roles (name, description) VALUES
    ('OWNER', 'The business owner: every permission'),
    ('STAFF', 'Read-only access to parties, inventory, orders and production');

-- OWNER gets every permission
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p WHERE r.name = 'OWNER';

-- STAFF (ASSUMPTION until open question Q8 is answered by the owner): read-only
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r JOIN permissions p ON p.code IN
    ('PARTY_VIEW', 'INVENTORY_VIEW', 'ORDER_VIEW', 'PRODUCTION_VIEW')
WHERE r.name = 'STAFF';

-- V23: System settings for dynamic UI-managed business profile, backup, and feature integrations
CREATE TABLE system_settings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category    VARCHAR(64)  NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_system_settings_category_key UNIQUE (category, setting_key),
    CONSTRAINT fk_system_settings_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_system_settings_category ON system_settings (category);
CREATE INDEX ix_system_settings_created_by ON system_settings (created_by);

-- Seed initial standard system settings
INSERT INTO system_settings (id, category, setting_key, setting_value, created_at, updated_at) VALUES
    (gen_random_uuid(), 'BUSINESS', 'name', 'SmartSilk Enterprises', now(), now()),
    (gen_random_uuid(), 'BUSINESS', 'currency', 'INR', now(), now()),
    (gen_random_uuid(), 'BACKUP', 'provider', 'local', now(), now()),
    (gen_random_uuid(), 'BACKUP', 'auto_backup', 'true', now(), now()),
    (gen_random_uuid(), 'WHATSAPP', 'enabled', 'false', now(), now()),
    (gen_random_uuid(), 'AI_OCR', 'enabled', 'false', now(), now());

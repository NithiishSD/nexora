-- V20__add_customer_credit_period.sql
-- Add customer_credit_days to parties table for customer-specific credit terms (FR-PARTY-17)

ALTER TABLE parties ADD COLUMN customer_credit_days INTEGER;
ALTER TABLE parties ADD CONSTRAINT ck_parties_customer_credit_days
    CHECK (customer_credit_days IS NULL OR (customer_credit_days >= 0 AND customer_credit_days <= 365));

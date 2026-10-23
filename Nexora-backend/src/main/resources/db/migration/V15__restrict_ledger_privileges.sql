-- History is immutable (DOMAIN_RULES section 2, ADR-032): the application's database user may add rows to the
-- insert-only tables (04-DATA-MODEL section 14) but never change, delete or truncate them, so even a bug or an
-- injected statement cannot rewrite the ledgers. Skipped where the role does not exist (dev, Testcontainers): there
-- the BEFORE UPDATE/DELETE triggers of V16 are the guard.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexora_app') THEN
        REVOKE UPDATE, DELETE, TRUNCATE ON
            material_movements, financial_transactions, audit_log,
            material_receipts, supplier_returns, account_transfers, opening_obligations,
            customer_payment_allocations, supplier_payment_allocations, manufacturer_payment_allocations,
            worker_payment_allocations
        FROM nexora_app;
    END IF;
END
$$;

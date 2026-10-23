-- Insert-only tables (04-DATA-MODEL section 14, ADR-032): history is never rewritten. A correction is a new row
-- (ADJUSTMENT, REVERSAL, a new allocation), so any UPDATE or DELETE of these rows is a bug and is refused here, for every
-- database user. Row triggers do not fire on TRUNCATE, which the tests use to empty tables between cases.
CREATE FUNCTION prevent_row_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Rows of % are never changed or deleted; record a correction instead.', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_material_movements_immutable BEFORE UPDATE OR DELETE ON material_movements
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_financial_transactions_immutable BEFORE UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_material_receipts_immutable BEFORE UPDATE OR DELETE ON material_receipts
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_supplier_returns_immutable BEFORE UPDATE OR DELETE ON supplier_returns
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_account_transfers_immutable BEFORE UPDATE OR DELETE ON account_transfers
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_opening_obligations_immutable BEFORE UPDATE OR DELETE ON opening_obligations
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_customer_payment_allocations_immutable BEFORE UPDATE OR DELETE ON customer_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_supplier_payment_allocations_immutable BEFORE UPDATE OR DELETE ON supplier_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_manufacturer_payment_allocations_immutable BEFORE UPDATE OR DELETE
    ON manufacturer_payment_allocations FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();
CREATE TRIGGER trg_worker_payment_allocations_immutable BEFORE UPDATE OR DELETE ON worker_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION prevent_row_modification();

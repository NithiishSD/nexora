-- Customer advance refunds and cheque clearance/bounce states (FR-PAY-25, E-PAY-13, E-PAY-14)

-- 1. Allow PENDING and BOUNCED states for cheques
ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('RECORDED', 'REVERSED', 'PENDING', 'BOUNCED'));

ALTER TABLE payments DROP CONSTRAINT ck_payments_reversal;
ALTER TABLE payments ADD CONSTRAINT ck_payments_reversal
    CHECK ((status IN ('REVERSED', 'BOUNCED')) = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL));

-- 2. Allow customer refunds (direction = PAID) linked to an original payment
ALTER TABLE payments ADD COLUMN refund_of_payment_id UUID;
ALTER TABLE payments ADD CONSTRAINT fk_payments_refund_of
    FOREIGN KEY (refund_of_payment_id) REFERENCES payments (id);
CREATE INDEX ix_payments_refund_of_payment_id ON payments (refund_of_payment_id);

ALTER TABLE payments DROP CONSTRAINT ck_payments_purpose_direction;
ALTER TABLE payments ADD CONSTRAINT ck_payments_purpose_direction
    CHECK ((purpose = 'CUSTOMER' AND direction = 'RECEIVED' AND refund_of_payment_id IS NULL)
        OR (purpose = 'CUSTOMER' AND direction = 'PAID' AND refund_of_payment_id IS NOT NULL)
        OR (purpose <> 'CUSTOMER' AND direction = 'PAID' AND refund_of_payment_id IS NULL));

-- 3. Extend financial transactions to support CUSTOMER_REFUND
ALTER TABLE financial_transactions DROP CONSTRAINT ck_financial_transactions_type;
ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_type CHECK (transaction_type IN (
    'OPENING_BALANCE', 'ADJUSTMENT', 'CUSTOMER_PAYMENT', 'CUSTOMER_REFUND', 'SUPPLIER_PAYMENT',
    'MANUFACTURER_PAYMENT', 'WORKER_PAYMENT', 'EXPENSE', 'PERSONAL_DRAWING', 'LOAN_RECEIVED',
    'LOAN_PRINCIPAL_REPAYMENT', 'LOAN_INTEREST_PAYMENT', 'CHIT_CONTRIBUTION', 'CHIT_PAYOUT',
    'CHIT_ORGANISER_PAYMENT', 'TRANSFER_OUT', 'TRANSFER_IN', 'REVERSAL'));

ALTER TABLE financial_transactions DROP CONSTRAINT ck_financial_transactions_type_direction;
ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_type_direction CHECK (
    transaction_type IN ('ADJUSTMENT', 'REVERSAL')
    OR (direction = 'IN' AND transaction_type IN ('OPENING_BALANCE', 'CUSTOMER_PAYMENT', 'LOAN_RECEIVED',
                                                   'CHIT_PAYOUT', 'TRANSFER_IN'))
    OR (direction = 'OUT' AND transaction_type IN ('CUSTOMER_REFUND', 'SUPPLIER_PAYMENT', 'MANUFACTURER_PAYMENT',
                                                    'WORKER_PAYMENT', 'EXPENSE', 'PERSONAL_DRAWING',
                                                    'LOAN_PRINCIPAL_REPAYMENT', 'LOAN_INTEREST_PAYMENT',
                                                    'CHIT_CONTRIBUTION', 'CHIT_ORGANISER_PAYMENT',
                                                    'TRANSFER_OUT')));

ALTER TABLE financial_transactions DROP CONSTRAINT ck_financial_transactions_type_reference;
ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_type_reference CHECK (
    (transaction_type IN ('OPENING_BALANCE', 'ADJUSTMENT')
        AND num_nonnulls(payment_id, expense_id, loan_id, chit_id, account_transfer_id) = 0)
    OR (transaction_type IN ('CUSTOMER_PAYMENT', 'CUSTOMER_REFUND', 'SUPPLIER_PAYMENT', 'MANUFACTURER_PAYMENT', 'WORKER_PAYMENT')
        AND payment_id IS NOT NULL)
    OR (transaction_type IN ('EXPENSE', 'PERSONAL_DRAWING') AND expense_id IS NOT NULL)
    OR (transaction_type IN ('LOAN_RECEIVED', 'LOAN_PRINCIPAL_REPAYMENT', 'LOAN_INTEREST_PAYMENT')
        AND loan_id IS NOT NULL)
    OR (transaction_type IN ('CHIT_CONTRIBUTION', 'CHIT_PAYOUT', 'CHIT_ORGANISER_PAYMENT') AND chit_id IS NOT NULL)
    OR (transaction_type IN ('TRANSFER_OUT', 'TRANSFER_IN') AND account_transfer_id IS NOT NULL)
    OR transaction_type = 'REVERSAL');

-- 4. Extend audit log action check
ALTER TABLE audit_log DROP CONSTRAINT ck_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_log_action CHECK (action IN (
    'PAYMENT_REVERSED', 'EXPENSE_REVERSED', 'WORK_RECORD_REVERSED', 'INVENTORY_ADJUSTED', 'PARTY_DEACTIVATED',
    'PARTY_ACTIVATED', 'PURCHASE_CANCELLED', 'ORDER_CANCELLED', 'PRODUCTION_CANCELLED', 'OUTSOURCING_CANCELLED',
    'ACCOUNT_CREATED', 'TRANSFER_RECORDED', 'LOAN_CREATED', 'LOGIN_LOCKED', 'REFRESH_TOKEN_REUSE', 'USER_CREATED',
    'USER_DEACTIVATED', 'USER_ROLE_CHANGED', 'OPENING_BALANCE_IMPORTED', 'ACCOUNT_ADJUSTED', 'LOAN_PAYMENT_REVERSED',
    'CHIT_ENTRY_REVERSED', 'CHEQUE_CLEARED', 'CHEQUE_BOUNCED', 'PAYMENT_REFUNDED'));

-- Finance (04-DATA-MODEL section 7): accounts, the business records that move money (payments, expenses, loans,
-- chits, transfers) and the money ledger itself. Balances are never stored: an account balance is
-- SUM(IN) - SUM(OUT) over financial_transactions, and an opening balance is an OPENING_BALANCE transaction.

-- ---------------------------------------------------------------------------------------------
-- financial_accounts: one row per cash box or bank account; the lock anchor for every cash-out check.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE financial_accounts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_name          VARCHAR(100) NOT NULL,                     -- "Cash", "SBI Current"
    account_type          VARCHAR(10)  NOT NULL,
    bank_name             VARCHAR(100),                              -- BANK only
    masked_account_number VARCHAR(30),                               -- XXXXXX4821; never a full number
    status                VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_financial_accounts_account_name UNIQUE (account_name),
    CONSTRAINT ck_financial_accounts_type CHECK (account_type IN ('CASH', 'BANK')),
    CONSTRAINT ck_financial_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_financial_accounts_bank_fields CHECK (account_type = 'BANK'
                                                        OR (bank_name IS NULL AND masked_account_number IS NULL)),
    -- only the last four digits are kept (NFR-SEC-06)
    CONSTRAINT ck_financial_accounts_masked CHECK (masked_account_number IS NULL
                                                   OR masked_account_number ~ '^X{6}[0-9]{4}$'),
    CONSTRAINT fk_financial_accounts_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_financial_accounts_created_by ON financial_accounts (created_by);

-- ---------------------------------------------------------------------------------------------
-- payments: who paid whom (the settlement). The money movement is a separate financial_transactions row;
-- the unallocated part of a customer payment is the customer's advance (derived).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_number       VARCHAR(30)    NOT NULL,                    -- PAY-2026-0001
    party_id             UUID           NOT NULL,
    purpose              VARCHAR(15)    NOT NULL,
    direction            VARCHAR(10)    NOT NULL,
    amount               NUMERIC(14, 2) NOT NULL,
    payment_method       VARCHAR(15)    NOT NULL,
    financial_account_id UUID           NOT NULL,
    payment_date         DATE           NOT NULL,
    reference_number     VARCHAR(100),                               -- cheque / UTR / receipt number
    status               VARCHAR(10)    NOT NULL DEFAULT 'RECORDED',
    reversed_at          TIMESTAMPTZ,
    reversal_reason      VARCHAR(255),
    notes                TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_payments_payment_number UNIQUE (payment_number),
    CONSTRAINT fk_payments_party FOREIGN KEY (party_id) REFERENCES parties (id),
    CONSTRAINT fk_payments_financial_account FOREIGN KEY (financial_account_id) REFERENCES financial_accounts (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_purpose CHECK (purpose IN ('CUSTOMER', 'SUPPLIER', 'MANUFACTURER', 'WORKER')),
    CONSTRAINT ck_payments_direction CHECK (direction IN ('RECEIVED', 'PAID')),
    CONSTRAINT ck_payments_purpose_direction CHECK ((purpose = 'CUSTOMER' AND direction = 'RECEIVED')
                                                    OR (purpose <> 'CUSTOMER' AND direction = 'PAID')),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('RECORDED', 'REVERSED')),
    CONSTRAINT ck_payments_reversal CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_payments_party_date ON payments (party_id, payment_date);
CREATE INDEX ix_payments_financial_account_id ON payments (financial_account_id);
CREATE INDEX ix_payments_status ON payments (status);
CREATE INDEX ix_payments_payment_date ON payments (payment_date);
CREATE INDEX ix_payments_created_by ON payments (created_by);

-- ---------------------------------------------------------------------------------------------
-- expenses: why money was spent. Personal drawings are kind PERSONAL and excluded from business profit.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE expenses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_number       VARCHAR(30)    NOT NULL,                    -- EXP-2026-0001
    category             VARCHAR(20)    NOT NULL,
    expense_kind         VARCHAR(10)    NOT NULL DEFAULT 'BUSINESS',
    recurrence           VARCHAR(10)    NOT NULL DEFAULT 'ONE_TIME',
    amount               NUMERIC(14, 2) NOT NULL,
    expense_date         DATE           NOT NULL,
    financial_account_id UUID           NOT NULL,
    payment_method       VARCHAR(15)    NOT NULL,
    description          TEXT,
    document_reference   VARCHAR(100),                               -- bill / receipt number
    status               VARCHAR(10)    NOT NULL DEFAULT 'RECORDED',
    reversed_at          TIMESTAMPTZ,
    reversal_reason      VARCHAR(255),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_expenses_expense_number UNIQUE (expense_number),
    CONSTRAINT fk_expenses_financial_account FOREIGN KEY (financial_account_id) REFERENCES financial_accounts (id),
    CONSTRAINT ck_expenses_amount CHECK (amount > 0),
    CONSTRAINT ck_expenses_category CHECK (category IN ('TRANSPORT', 'ELECTRICITY', 'RENT', 'WORKER', 'MAINTENANCE',
                                                        'PACKING', 'OTHER')),
    CONSTRAINT ck_expenses_kind CHECK (expense_kind IN ('BUSINESS', 'PERSONAL')),
    CONSTRAINT ck_expenses_recurrence CHECK (recurrence IN ('ONE_TIME', 'MONTHLY')),
    CONSTRAINT ck_expenses_method CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    CONSTRAINT ck_expenses_status CHECK (status IN ('RECORDED', 'REVERSED')),
    CONSTRAINT ck_expenses_reversal CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)),
    CONSTRAINT fk_expenses_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_expenses_date_category ON expenses (expense_date, category);
CREATE INDEX ix_expenses_financial_account_id ON expenses (financial_account_id);
CREATE INDEX ix_expenses_kind_date ON expenses (expense_kind, expense_date);
CREATE INDEX ix_expenses_created_by ON expenses (created_by);

-- ---------------------------------------------------------------------------------------------
-- loans: a borrowing from a LENDER party. Principal in, repayments and interest are ledger rows by loan_id;
-- the outstanding principal is derived.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE loans (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number           VARCHAR(30)    NOT NULL,                   -- LON-2026-0001
    party_id              UUID           NOT NULL,
    loan_type             VARCHAR(10)    NOT NULL,
    principal_amount      NUMERIC(14, 2) NOT NULL,
    repaid_before_go_live NUMERIC(14, 2) NOT NULL DEFAULT 0,
    interest_notes        VARCHAR(500),
    interest_rate_percent NUMERIC(6, 3),                             -- annual %, informational only
    start_date            DATE           NOT NULL,
    due_date              DATE,
    status                VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    notes                 TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_loans_loan_number UNIQUE (loan_number),
    CONSTRAINT fk_loans_party FOREIGN KEY (party_id) REFERENCES parties (id),
    CONSTRAINT ck_loans_type CHECK (loan_type IN ('BANK', 'PRIVATE')),
    CONSTRAINT ck_loans_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_loans_repaid_before_go_live CHECK (repaid_before_go_live >= 0
                                                     AND repaid_before_go_live <= principal_amount),
    CONSTRAINT ck_loans_interest_rate CHECK (interest_rate_percent IS NULL OR interest_rate_percent >= 0),
    CONSTRAINT ck_loans_due_date CHECK (due_date IS NULL OR due_date >= start_date),
    CONSTRAINT ck_loans_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT fk_loans_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_loans_party_id ON loans (party_id);
CREATE INDEX ix_loans_status ON loans (status);
CREATE INDEX ix_loans_created_by ON loans (created_by);

-- ---------------------------------------------------------------------------------------------
-- chits: a chit-fund commitment, cash only. Amounts are entered by the user (no bidding calculation, Q6).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE chits (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chit_number                VARCHAR(30)    NOT NULL,              -- CHT-2026-0001
    chit_name                  VARCHAR(150)   NOT NULL,
    total_amount               NUMERIC(14, 2) NOT NULL,
    monthly_amount             NUMERIC(14, 2) NOT NULL,
    duration_months            INTEGER        NOT NULL,
    prior_contributions_count  INTEGER        NOT NULL DEFAULT 0,    -- paid before go-live, no cash row
    prior_contributions_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    start_date                 DATE           NOT NULL,
    status                     VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    notes                      TEXT,
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                 UUID,
    version                    BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_chits_chit_number UNIQUE (chit_number),
    CONSTRAINT ck_chits_total CHECK (total_amount > 0),
    CONSTRAINT ck_chits_monthly CHECK (monthly_amount > 0),
    CONSTRAINT ck_chits_duration CHECK (duration_months > 0),
    CONSTRAINT ck_chits_prior_count CHECK (prior_contributions_count >= 0
                                           AND prior_contributions_count <= duration_months),
    CONSTRAINT ck_chits_prior_amount CHECK (prior_contributions_amount >= 0
                                            AND (prior_contributions_count > 0 OR prior_contributions_amount = 0)),
    CONSTRAINT ck_chits_status CHECK (status IN ('ACTIVE', 'MATURED', 'CLOSED')),
    CONSTRAINT fk_chits_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_chits_status ON chits (status);
CREATE INDEX ix_chits_created_by ON chits (created_by);

-- ---------------------------------------------------------------------------------------------
-- account_transfers: money moved between two own accounts (one TRANSFER_OUT + one TRANSFER_IN row).
-- Neither revenue nor expense; a mistake is corrected by a transfer the other way.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE account_transfers (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_number        VARCHAR(30)    NOT NULL,                  -- TRF-2026-0001
    source_account_id      UUID           NOT NULL,
    destination_account_id UUID           NOT NULL,
    amount                 NUMERIC(14, 2) NOT NULL,
    transfer_date          DATE           NOT NULL,
    notes                  TEXT,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by             UUID,
    version                BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_account_transfers_transfer_number UNIQUE (transfer_number),
    CONSTRAINT fk_account_transfers_source FOREIGN KEY (source_account_id) REFERENCES financial_accounts (id),
    CONSTRAINT fk_account_transfers_destination FOREIGN KEY (destination_account_id) REFERENCES financial_accounts (id),
    CONSTRAINT ck_account_transfers_amount CHECK (amount > 0),
    CONSTRAINT ck_account_transfers_accounts_differ CHECK (source_account_id <> destination_account_id),
    CONSTRAINT fk_account_transfers_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_account_transfers_source ON account_transfers (source_account_id);
CREATE INDEX ix_account_transfers_destination ON account_transfers (destination_account_id);
CREATE INDEX ix_account_transfers_transfer_date ON account_transfers (transfer_date);
CREATE INDEX ix_account_transfers_created_by ON account_transfers (created_by);

-- ---------------------------------------------------------------------------------------------
-- financial_transactions: the money ledger. Every money change is exactly one row, append-only; a mistake is
-- corrected by a REVERSAL row pointing at the original. Written only by FinanceService.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE financial_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_account_id    UUID           NOT NULL,
    transaction_date        DATE           NOT NULL,
    transaction_type        VARCHAR(30)    NOT NULL,
    direction               VARCHAR(3)     NOT NULL,
    amount                  NUMERIC(14, 2) NOT NULL,
    payment_id              UUID,                                    -- at most one business reference
    expense_id              UUID,
    loan_id                 UUID,
    chit_id                 UUID,
    account_transfer_id     UUID,
    reverses_transaction_id UUID,                                    -- REVERSAL rows only
    reference_number        VARCHAR(100),
    description             TEXT,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              UUID,
    version                 BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_financial_transactions_account FOREIGN KEY (financial_account_id) REFERENCES financial_accounts (id),
    CONSTRAINT fk_financial_transactions_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_financial_transactions_expense FOREIGN KEY (expense_id) REFERENCES expenses (id),
    CONSTRAINT fk_financial_transactions_loan FOREIGN KEY (loan_id) REFERENCES loans (id),
    CONSTRAINT fk_financial_transactions_chit FOREIGN KEY (chit_id) REFERENCES chits (id),
    CONSTRAINT fk_financial_transactions_account_transfer FOREIGN KEY (account_transfer_id) REFERENCES account_transfers (id),
    CONSTRAINT fk_financial_transactions_reverses FOREIGN KEY (reverses_transaction_id) REFERENCES financial_transactions (id),
    CONSTRAINT uq_financial_transactions_reverses UNIQUE (reverses_transaction_id),   -- reversed at most once
    CONSTRAINT ck_financial_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_financial_transactions_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_financial_transactions_type CHECK (transaction_type IN (
        'OPENING_BALANCE', 'ADJUSTMENT', 'CUSTOMER_PAYMENT', 'SUPPLIER_PAYMENT', 'MANUFACTURER_PAYMENT', 'WORKER_PAYMENT',
        'EXPENSE', 'PERSONAL_DRAWING', 'LOAN_RECEIVED', 'LOAN_PRINCIPAL_REPAYMENT', 'LOAN_INTEREST_PAYMENT',
        'CHIT_CONTRIBUTION', 'CHIT_PAYOUT', 'CHIT_ORGANISER_PAYMENT', 'TRANSFER_OUT', 'TRANSFER_IN', 'REVERSAL')),
    CONSTRAINT ck_financial_transactions_single_reference CHECK (
        num_nonnulls(payment_id, expense_id, loan_id, chit_id, account_transfer_id) <= 1),
    -- each type carries its own reference (section 7.7 table); a REVERSAL copies the original's, or none
    CONSTRAINT ck_financial_transactions_type_reference CHECK (
        (transaction_type IN ('OPENING_BALANCE', 'ADJUSTMENT')
            AND num_nonnulls(payment_id, expense_id, loan_id, chit_id, account_transfer_id) = 0)
        OR (transaction_type IN ('CUSTOMER_PAYMENT', 'SUPPLIER_PAYMENT', 'MANUFACTURER_PAYMENT', 'WORKER_PAYMENT')
            AND payment_id IS NOT NULL)
        OR (transaction_type IN ('EXPENSE', 'PERSONAL_DRAWING') AND expense_id IS NOT NULL)
        OR (transaction_type IN ('LOAN_RECEIVED', 'LOAN_PRINCIPAL_REPAYMENT', 'LOAN_INTEREST_PAYMENT')
            AND loan_id IS NOT NULL)
        OR (transaction_type IN ('CHIT_CONTRIBUTION', 'CHIT_PAYOUT', 'CHIT_ORGANISER_PAYMENT') AND chit_id IS NOT NULL)
        OR (transaction_type IN ('TRANSFER_OUT', 'TRANSFER_IN') AND account_transfer_id IS NOT NULL)
        OR transaction_type = 'REVERSAL'),
    -- the direction each type must have; ADJUSTMENT goes either way, a REVERSAL the opposite of its original
    CONSTRAINT ck_financial_transactions_type_direction CHECK (
        transaction_type IN ('ADJUSTMENT', 'REVERSAL')
        OR (direction = 'IN' AND transaction_type IN ('OPENING_BALANCE', 'CUSTOMER_PAYMENT', 'LOAN_RECEIVED',
                                                       'CHIT_PAYOUT', 'TRANSFER_IN'))
        OR (direction = 'OUT' AND transaction_type IN ('SUPPLIER_PAYMENT', 'MANUFACTURER_PAYMENT', 'WORKER_PAYMENT',
                                                        'EXPENSE', 'PERSONAL_DRAWING', 'LOAN_PRINCIPAL_REPAYMENT',
                                                        'LOAN_INTEREST_PAYMENT', 'CHIT_CONTRIBUTION',
                                                        'CHIT_ORGANISER_PAYMENT', 'TRANSFER_OUT'))),
    CONSTRAINT ck_financial_transactions_reversal CHECK ((transaction_type = 'REVERSAL') = (reverses_transaction_id IS NOT NULL)),
    CONSTRAINT fk_financial_transactions_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_financial_transactions_account_date ON financial_transactions (financial_account_id, transaction_date);
CREATE INDEX ix_financial_transactions_payment_id ON financial_transactions (payment_id);
CREATE INDEX ix_financial_transactions_expense_id ON financial_transactions (expense_id);
CREATE INDEX ix_financial_transactions_loan_id ON financial_transactions (loan_id);
CREATE INDEX ix_financial_transactions_chit_id ON financial_transactions (chit_id);
CREATE INDEX ix_financial_transactions_account_transfer_id ON financial_transactions (account_transfer_id);
CREATE INDEX ix_financial_transactions_type_date ON financial_transactions (transaction_type, transaction_date);
CREATE INDEX ix_financial_transactions_created_by ON financial_transactions (created_by);

-- ---------------------------------------------------------------------------------------------
-- Last line of defence for "money never goes negative" (FR-FIN-06, ADR-031): an OUT row that leaves its account
-- below zero is refused by the database. FinanceService checks first under the account row lock (the real,
-- race-free check); this trigger only catches a code path that forgot to.
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION check_account_not_negative() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF (SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amount ELSE -amount END), 0)
        FROM financial_transactions
        WHERE financial_account_id = NEW.financial_account_id) < 0 THEN
        RAISE EXCEPTION 'account % would go negative', NEW.financial_account_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_financial_transactions_balance';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_financial_transactions_not_negative
    AFTER INSERT ON financial_transactions
    FOR EACH ROW
    WHEN (NEW.direction = 'OUT')
EXECUTE FUNCTION check_account_not_negative();

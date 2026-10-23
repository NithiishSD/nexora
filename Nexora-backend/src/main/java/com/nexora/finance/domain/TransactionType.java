package com.nexora.finance.domain;

import static com.nexora.finance.domain.Direction.IN;
import static com.nexora.finance.domain.Direction.OUT;

// Why money moved (04-DATA-MODEL section 7.7). Each type fixes its direction and the business record it must point
// at; the same table is enforced by ck_financial_transactions_type_direction / _type_reference in the database.
// direction == null: either way (ADJUSTMENT) or the opposite of the original (REVERSAL).
// reference == null: a REVERSAL copies the reference of the row it reverses.
public enum TransactionType {
    OPENING_BALANCE(IN, TransactionReferenceType.NONE),
    ADJUSTMENT(null, TransactionReferenceType.NONE),
    CUSTOMER_PAYMENT(IN, TransactionReferenceType.PAYMENT),
    CUSTOMER_REFUND(OUT, TransactionReferenceType.PAYMENT),
    SUPPLIER_PAYMENT(OUT, TransactionReferenceType.PAYMENT),
    MANUFACTURER_PAYMENT(OUT, TransactionReferenceType.PAYMENT),
    WORKER_PAYMENT(OUT, TransactionReferenceType.PAYMENT),
    EXPENSE(OUT, TransactionReferenceType.EXPENSE),
    PERSONAL_DRAWING(OUT, TransactionReferenceType.EXPENSE),
    LOAN_RECEIVED(IN, TransactionReferenceType.LOAN),
    LOAN_PRINCIPAL_REPAYMENT(OUT, TransactionReferenceType.LOAN),
    LOAN_INTEREST_PAYMENT(OUT, TransactionReferenceType.LOAN),
    CHIT_CONTRIBUTION(OUT, TransactionReferenceType.CHIT),
    CHIT_PAYOUT(IN, TransactionReferenceType.CHIT),
    CHIT_ORGANISER_PAYMENT(OUT, TransactionReferenceType.CHIT),
    TRANSFER_OUT(OUT, TransactionReferenceType.TRANSFER),
    TRANSFER_IN(IN, TransactionReferenceType.TRANSFER),
    REVERSAL(null, null);

    private final Direction direction;
    private final TransactionReferenceType reference;

    TransactionType(Direction direction, TransactionReferenceType reference) {
        this.direction = direction;
        this.reference = reference;
    }

    // The fixed direction, or null when it is chosen per row.
    public Direction direction() {
        return direction;
    }

    // The business record this type must reference, or null for REVERSAL.
    public TransactionReferenceType reference() {
        return reference;
    }
}

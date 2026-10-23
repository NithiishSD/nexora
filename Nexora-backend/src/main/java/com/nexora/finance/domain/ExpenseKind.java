package com.nexora.finance.domain;

// BUSINESS spending, or a PERSONAL drawing by the owner: it reduces cash the same way but is kept out of business
// expense totals and profitability (FR-FIN-12).
public enum ExpenseKind {
    BUSINESS,
    PERSONAL;

    public TransactionType transactionType() {
        return this == BUSINESS ? TransactionType.EXPENSE : TransactionType.PERSONAL_DRAWING;
    }
}

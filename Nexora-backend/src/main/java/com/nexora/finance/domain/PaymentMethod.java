package com.nexora.finance.domain;

// How the money moved. CASH needs a CASH account, every other method a BANK account (FR-PAY-12).
public enum PaymentMethod {
    CASH,
    UPI,
    BANK_TRANSFER,
    CHEQUE;

    public boolean fits(AccountType accountType) {
        return (this == CASH) == (accountType == AccountType.CASH);
    }
}

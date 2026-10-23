package com.nexora.finance.domain;

import java.math.BigDecimal;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// A cash box or one bank account. There is no balance column: the balance is SUM(IN) - SUM(OUT) of its ledger rows
// (DOMAIN_RULES rule 2). The row exists to be locked (FOR UPDATE) before every cash-out check.
@Entity
@Table(name = "financial_accounts")
public class FinancialAccount extends AuditableEntity {

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10, updatable = false)
    private AccountType accountType;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "masked_account_number", length = 30)
    private String maskedAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    protected FinancialAccount() {
        // for JPA only
    }

    // Bank accounts need a bank name; cash accounts have no bank fields (FR-FIN-01 AC4). Only the last four digits
    // of an account number are ever kept, stored as XXXXXX1234 (NFR-SEC-06).
    public static FinancialAccount open(String accountName, AccountType type, String bankName, String last4) {
        if (type == AccountType.CASH && bankName != null) {
            throw new FieldValidationException("bankName", "A cash account has no bank name.");
        }
        if (type == AccountType.CASH && last4 != null) {
            throw new FieldValidationException("accountNumberLast4", "A cash account has no account number.");
        }
        if (type == AccountType.BANK && bankName == null) {
            throw new FieldValidationException("bankName", "A bank account needs the bank name.");
        }
        FinancialAccount account = new FinancialAccount();
        account.accountName = accountName;
        account.accountType = type;
        account.bankName = bankName;
        account.maskedAccountNumber = last4 == null ? null : "XXXXXX" + last4;
        account.status = AccountStatus.ACTIVE;
        return account;
    }

    // ACTIVE -> CLOSED, only at 0.00 (FR-FIN-16); the history stays and no new row can be written to it.
    public void close(BigDecimal balance) {
        if (status != AccountStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Account " + accountName, status, AccountStatus.CLOSED);
        }
        if (balance.signum() != 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Account " + accountName + " still holds "
                    + balance.toPlainString() + "; transfer or adjust it to 0.00 before closing.");
        }
        status = AccountStatus.CLOSED;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getBankName() {
        return bankName;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public AccountStatus getStatus() {
        return status;
    }
}

package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.FinancialTransaction;
import com.nexora.finance.domain.InsufficientFundsException;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;

// The ONLY writer of the money ledger (DOMAIN_RULES rule 6, 03-ARCHITECTURE 5.4). Payments, expenses, transfers,
// loans, chits and adjustments call recordTransaction from inside their own use case; no HTTP route writes a row.
@Service
public class FinanceService {

    // Chits are cash only (FR-CHIT-04, DOMAIN_RULES Finance).
    private static final Set<TransactionType> CASH_ONLY = EnumSet.of(TransactionType.CHIT_CONTRIBUTION,
            TransactionType.CHIT_PAYOUT, TransactionType.CHIT_ORGANISER_PAYMENT);

    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;

    public FinanceService(FinancialAccountRepository accounts, FinancialTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    // MANDATORY: the ledger row commits or rolls back with the business record that caused it (NFR-REL-02).
    // Lock order (ADR-028): the caller has taken its party/aggregate locks; the account row comes after them.
    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionResult recordTransaction(TransactionCommand c) {
        // 1. The command must follow the type table (04 section 7.7); a violation is a programming error.
        TransactionReferenceType referenceType = requireWellFormed(c);

        // 2. Lock the account, then check it under the lock: open, right kind, enough money.
        FinancialAccount account = accounts.findByIdForUpdate(c.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", c.accountId()));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Account " + account.getAccountName() + " is closed.");
        }
        if (CASH_ONLY.contains(c.transactionType()) && account.getAccountType() != AccountType.CASH) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, "Chit transactions are cash only.");
        }
        BigDecimal amount = Money.round(c.amount());
        if (c.direction() == Direction.OUT) {
            BigDecimal balance = transactions.balanceOf(account.getId());
            if (balance.compareTo(amount) < 0) {                  // no overdraft for cash or bank (ADR-031, Q4)
                throw new InsufficientFundsException(account.getAccountName(), balance, amount);
            }
        }

        // 3. Append the row; the balance is never stored anywhere.
        FinancialTransaction row = transactions.save(FinancialTransaction.record(account.getId(), c.transactionDate(),
                c.transactionType(), c.direction(), amount, referenceType, c.referenceId(), null,
                c.referenceNumber(), c.description()));
        return new TransactionResult(row.getId());
    }

    // FR-FIN-17: undo a ledger row by adding its mirror image, never by changing it. Same account, opposite direction,
    // same business reference, pointing at the original (uq_financial_transactions_reverses: once only). Money going
    // OUT again needs the balance (a reversed customer payment, 409 INSUFFICIENT_FUNDS).
    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionResult reverse(UUID originalId, LocalDate date, String description) {
        FinancialTransaction original = transactions.findById(originalId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", originalId));
        FinancialAccount account = accounts.findByIdForUpdate(original.getAccountId()).orElseThrow();
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Account " + account.getAccountName() + " is closed.");
        }
        Direction direction = original.getDirection().opposite();
        if (direction == Direction.OUT) {
            BigDecimal balance = transactions.balanceOf(account.getId());
            if (balance.compareTo(original.getAmount()) < 0) {
                throw new InsufficientFundsException(account.getAccountName(), balance, original.getAmount());
            }
        }
        FinancialTransaction row = transactions.save(FinancialTransaction.record(account.getId(), date,
                TransactionType.REVERSAL, direction, original.getAmount(), original.getReferenceType(),
                original.getReferenceId(), original.getId(), original.getReferenceNumber(), description));
        return new TransactionResult(row.getId());
    }

    // FR-PAY-12 for other modules (loans): the method must fit the account's kind, cash with cash, the rest with a
    // bank; 404 unknown account, 422 INVALID_PAYMENT_METHOD. Read before the use case's own locks.
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireMethodFits(UUID accountId, PaymentMethod method) {
        FinancialAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (!method.fits(account.getAccountType())) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, method + " needs a "
                    + (method == PaymentMethod.CASH ? "cash" : "bank") + " account; " + account.getAccountName()
                    + " is " + account.getAccountType() + ".");
        }
    }

    // Returns the reference type with null read as NONE.
    private static TransactionReferenceType requireWellFormed(TransactionCommand c) {
        TransactionType type = c.transactionType();
        if (type == null || type == TransactionType.REVERSAL) {
            throw new IllegalArgumentException("A transaction type other than REVERSAL is required: " + type);
        }
        if (c.amount() == null || c.amount().signum() <= 0 || c.amount().stripTrailingZeros().scale() > Money.SCALE) {
            throw new IllegalArgumentException("Amount must be > 0 with at most 2 decimals: " + c.amount());
        }
        if (c.direction() == null || (type.direction() != null && type.direction() != c.direction())) {
            throw new IllegalArgumentException(type + " must be " + type.direction() + ", got " + c.direction());
        }
        if (c.transactionDate() == null) {
            throw new IllegalArgumentException("A transaction date is required.");
        }
        TransactionReferenceType ref = c.referenceType() == null ? TransactionReferenceType.NONE : c.referenceType();
        boolean hasId = c.referenceId() != null;
        if (ref != type.reference() || hasId == (ref == TransactionReferenceType.NONE)) {
            throw new IllegalArgumentException(type + " needs reference " + type.reference() + ", got " + ref
                    + (hasId ? " with an id" : " without an id"));
        }
        if (type == TransactionType.ADJUSTMENT && (c.description() == null || c.description().isBlank())) {
            throw new IllegalArgumentException("An adjustment needs its reason as the description.");
        }
        return ref;
    }
}

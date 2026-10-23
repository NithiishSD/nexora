package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.AccountAdjustmentRequest;
import com.nexora.finance.api.dto.AccountLineResponse;
import com.nexora.finance.api.dto.AccountListResponse;
import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.api.dto.AccountResponse;
import com.nexora.finance.api.dto.CashPositionResponse;
import com.nexora.finance.api.dto.CreateAccountRequest;
import com.nexora.finance.api.dto.TransactionResponse;
import com.nexora.finance.domain.AccountStatus;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.FinancialTransaction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.ResourceNotFoundException;

// Account use cases (E-FIN-01..03) and the read-only transaction list (E-FIN-07). Balances are always computed from
// the ledger by SQL; nothing here writes a transaction except through FinanceService.
@Service
public class FinancialAccountService {

    private static final Logger log = LoggerFactory.getLogger(FinancialAccountService.class);
    private static final Set<String> TRANSACTION_SORTABLE = Set.of("transactionDate", "amount", "createdAt");

    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final FinanceService finance;
    private final AuditService audit;

    public FinancialAccountService(FinancialAccountRepository accounts, FinancialTransactionRepository transactions,
                                   FinanceService finance, AuditService audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.finance = finance;
        this.audit = audit;
    }

    // E-FIN-01. The opening balance is an OPENING_BALANCE IN transaction, never a column (FR-FIN-02). A duplicate
    // name hits uq_financial_accounts_account_name on the flush: 409 DUPLICATE_RESOURCE, nothing stored.
    @Transactional
    public AccountResponse create(CreateAccountRequest r) {
        BigDecimal opening = r.openingBalance() == null ? Money.zero() : Money.round(r.openingBalance());
        if (Money.isPositive(opening) && r.openingDate() == null) {
            throw new FieldValidationException("openingDate", "An opening balance needs its date.");
        }
        FinancialAccount account = accounts.saveAndFlush(FinancialAccount.open(r.accountName().strip(),
                r.accountType(), blankToNull(r.bankName()), r.accountNumberLast4()));
        if (Money.isPositive(opening)) {
            finance.recordTransaction(new TransactionCommand(account.getId(), Direction.IN, opening,
                    TransactionType.OPENING_BALANCE, r.openingDate(), "Opening balance", null,
                    TransactionReferenceType.NONE, null));
        }
        log.info("event=account_created name={} type={}", account.getAccountName(), account.getAccountType());
        return toResponse(account, opening);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        FinancialAccount account = find(id);
        return toResponse(account, transactions.balanceOf(id));
    }

    // E-FIN-02. totalAvailable is cash plus every bank balance over ALL accounts, whatever the filter (closed
    // accounts hold 0.00); receivables are not money in hand (FR-FIN-03 AC2).
    @Transactional(readOnly = true)
    public AccountListResponse list(AccountStatus status, AccountType type) {
        List<FinancialAccount> all = accounts.findAll(Sort.by("accountType", "accountName"));
        Map<UUID, BigDecimal> balances = balances(all.stream().map(FinancialAccount::getId).toList());
        BigDecimal total = balances.values().stream().reduce(Money.zero(), BigDecimal::add);
        List<AccountResponse> shown = all.stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> type == null || a.getAccountType() == type)
                .map(a -> toResponse(a, balances.getOrDefault(a.getId(), Money.zero())))
                .toList();
        return new AccountListResponse(shown, total);
    }

    // E-FIN-07, read only. An unknown accountId is 404 (FR-FIN-07 AC3); from after to is 400 (AC2).
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(UUID accountId, TransactionType type, Direction direction,
                                                          LocalDate from, LocalDate to,
                                                          TransactionReferenceType referenceType, UUID referenceId,
                                                          Pageable pageable) {
        ListParameters.requireSortable(pageable, TRANSACTION_SORTABLE);
        ListParameters.requireDateRange(from, to);
        if (accountId != null) {
            find(accountId);
        }
        Specification<FinancialTransaction> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<FinancialTransaction> part : Arrays.asList(
                equal("accountId", accountId), equal("transactionType", type), equal("direction", direction),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to),
                reference(referenceType, referenceId))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        Page<FinancialTransaction> page = transactions.findAll(filter, pageable);
        return PageResponse.from(page.map(toTransactionResponse(page.getContent())));
    }

    // E-FIN-06: one account's history, oldest first, each row with the balance right after it (whatever the
    // filters, the running balance is the account's real balance at that row).
    @Transactional(readOnly = true)
    public PageResponse<AccountLineResponse> history(UUID accountId, TransactionType type, LocalDate from,
                                                     LocalDate to, Pageable pageable) {
        find(accountId);
        PageResponse<TransactionResponse> page = transactions(accountId, type, null, from, to, null, null,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("transactionDate", "createdAt")));
        Map<UUID, BigDecimal> running = new HashMap<>();
        transactions.runningBalances(accountId).forEach(row -> running.put((UUID) row[0], (BigDecimal) row[1]));
        return new PageResponse<>(page.content().stream()
                .map(t -> AccountLineResponse.of(t, Money.round(running.get(t.id())))).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    // E-FIN-05. FinanceService locks the account and refuses a closed one (409) or an OUT above the balance (409);
    // the reason becomes the row's description and the audit entry's reason.
    @Transactional
    public TransactionResponse adjust(UUID accountId, AccountAdjustmentRequest r) {
        find(accountId);
        TransactionResult row = finance.recordTransaction(new TransactionCommand(accountId, r.direction(),
                r.amount(), TransactionType.ADJUSTMENT, r.transactionDate(), r.reason().strip(), null,
                TransactionReferenceType.NONE, null));
        audit.record(AuditAction.ACCOUNT_ADJUSTED, "FINANCIAL_ACCOUNT", accountId, r.reason().strip(), Map.of(
                "direction", r.direction().name(), "amount", Money.round(r.amount()).toPlainString(),
                "transactionId", row.transactionId().toString()));
        log.info("event=account_adjusted account={} direction={} amount={}", accountId, r.direction(), r.amount());
        return transaction(row.transactionId());
    }

    // E-FIN-04: only at 0.00, under the account lock so no row can land between the check and the close.
    @Transactional
    public AccountResponse close(UUID accountId) {
        FinancialAccount account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        BigDecimal balance = transactions.balanceOf(accountId);
        account.close(balance);
        log.info("event=account_closed name={}", account.getAccountName());
        return toResponse(accounts.saveAndFlush(account), balance);
    }

    // E-FIN-08: every active account's balance, cash and banks apart (closed accounts hold 0.00 and are left out).
    @Transactional(readOnly = true)
    public CashPositionResponse cashPosition(Instant asOf) {
        List<FinancialAccount> active = accounts.findAll(Sort.by("accountName")).stream()
                .filter(FinancialAccount::isActive).toList();
        Map<UUID, BigDecimal> balances = balances(active.stream().map(FinancialAccount::getId).toList());
        List<CashPositionResponse.AccountBalance> cash = new ArrayList<>();
        List<CashPositionResponse.AccountBalance> banks = new ArrayList<>();
        for (FinancialAccount a : active) {
            (a.getAccountType() == AccountType.CASH ? cash : banks).add(new CashPositionResponse.AccountBalance(
                    new AccountRef(a.getId(), a.getAccountName(), a.getAccountType()),
                    balances.getOrDefault(a.getId(), Money.zero())));
        }
        BigDecimal totalCash = cash.stream().map(CashPositionResponse.AccountBalance::balance)
                .reduce(Money.zero(), BigDecimal::add);
        BigDecimal totalBank = banks.stream().map(CashPositionResponse.AccountBalance::balance)
                .reduce(Money.zero(), BigDecimal::add);
        return new CashPositionResponse(cash, banks, totalCash, totalBank, totalCash.add(totalBank), asOf);
    }

    @Transactional(readOnly = true)
    public TransactionResponse transaction(UUID id) {
        FinancialTransaction row = transactions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        return toTransactionResponse(List.of(row)).apply(row);
    }

    // ---- helpers --------------------------------------------------------------------------

    private FinancialAccount find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    private Map<UUID, BigDecimal> balances(List<UUID> ids) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (!ids.isEmpty()) {
            transactions.balancesOf(ids).forEach(row -> result.put((UUID) row[0], Money.round((BigDecimal) row[1])));
        }
        return result;
    }

    // Column of each reference type; NONE = no reference at all (opening balances, adjustments).
    private static final Map<TransactionReferenceType, String> REFERENCE_COLUMNS = Map.of(
            TransactionReferenceType.PAYMENT, "paymentId", TransactionReferenceType.EXPENSE, "expenseId",
            TransactionReferenceType.LOAN, "loanId", TransactionReferenceType.CHIT, "chitId",
            TransactionReferenceType.TRANSFER, "accountTransferId");

    private static Specification<FinancialTransaction> reference(TransactionReferenceType type, UUID id) {
        if (type == TransactionReferenceType.NONE) {
            return (root, query, cb) -> cb.and(REFERENCE_COLUMNS.values().stream()
                    .map(column -> cb.isNull(root.get(column))).toArray(jakarta.persistence.criteria.Predicate[]::new));
        }
        if (type != null) {
            String column = REFERENCE_COLUMNS.get(type);
            return id == null ? (root, query, cb) -> cb.isNotNull(root.get(column))
                    : (root, query, cb) -> cb.equal(root.get(column), id);
        }
        return id == null ? null : (root, query, cb) -> cb.or(REFERENCE_COLUMNS.values().stream()
                .map(column -> cb.equal(root.get(column), id)).toArray(jakarta.persistence.criteria.Predicate[]::new));
    }

    private static Specification<FinancialTransaction> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    // Accounts and reversals of one page are fetched with one query each (no N+1).
    private Function<FinancialTransaction, TransactionResponse> toTransactionResponse(List<FinancialTransaction> rows) {
        Map<UUID, FinancialAccount> byId = accounts.findAllById(rows.stream().map(FinancialTransaction::getAccountId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(FinancialAccount::getId, a -> a));
        Map<UUID, UUID> reversedBy = new HashMap<>();
        if (!rows.isEmpty()) {
            transactions.reversalsOf(rows.stream().map(FinancialTransaction::getId).toList())
                    .forEach(row -> reversedBy.put((UUID) row[0], (UUID) row[1]));
        }
        return t -> {
            FinancialAccount a = byId.get(t.getAccountId());
            return new TransactionResponse(t.getId(), new AccountRef(a.getId(), a.getAccountName(), a.getAccountType()),
                    t.getTransactionDate(), t.getTransactionType(), t.getDirection(), t.getAmount(),
                    t.getReferenceType(), t.getReferenceId(), t.getReferenceNumber(), t.getDescription(),
                    t.getReversesTransactionId(), reversedBy.get(t.getId()), t.getCreatedAt(), t.getCreatedBy());
        };
    }

    private static AccountResponse toResponse(FinancialAccount a, BigDecimal balance) {
        return new AccountResponse(a.getId(), a.getAccountName(), a.getAccountType(), a.getBankName(),
                a.getMaskedAccountNumber(), a.getStatus(), Money.round(balance), a.getCreatedAt(), a.getUpdatedAt(),
                a.getCreatedBy());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

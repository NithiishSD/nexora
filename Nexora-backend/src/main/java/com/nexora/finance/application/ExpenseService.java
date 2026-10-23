package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.api.dto.ExpenseResponse;
import com.nexora.finance.api.dto.ExpenseSummaryResponse;
import com.nexora.finance.api.dto.RecordExpenseRequest;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.Expense;
import com.nexora.finance.domain.ExpenseCategory;
import com.nexora.finance.domain.ExpenseKind;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.finance.domain.Recurrence;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.infrastructure.ExpenseRepository;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Expenses and personal drawings (FR-FIN-11..14). One public method = one use case = one transaction.
@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private static final Set<String> SORTABLE = Set.of("expenseDate", "amount", "createdAt");

    private final ExpenseRepository expenses;
    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final FinanceService finance;
    private final BusinessNumberGenerator numbers;
    private final AuditService audit;
    private final ExpenseCategoryService categoryValidation;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenses, FinancialAccountRepository accounts,
                          FinancialTransactionRepository transactions, FinanceService finance,
                          BusinessNumberGenerator numbers, AuditService audit,
                          ExpenseCategoryService categoryValidation, Clock clock) {
        this.expenses = expenses;
        this.accounts = accounts;
        this.transactions = transactions;
        this.finance = finance;
        this.numbers = numbers;
        this.audit = audit;
        this.categoryValidation = categoryValidation;
        this.clock = clock;
    }

    // E-FIN-12. Lock order (ADR-028): idempotency key (filter) -> account -> EXP number. The method must fit the
    // account (422, FR-PAY-12); FinanceService refuses a closed account (409) or more than it holds (409); then
    // nothing is stored.
    @Transactional
    public ExpenseResponse record(RecordExpenseRequest r) {
        String normalizedCategory = r.category() != null ? r.category().trim().toUpperCase(Locale.ROOT) : null;
        if (!categoryValidation.isValidActiveCategory(normalizedCategory)) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Unknown or inactive expense category: " + r.category());
        }
        FinancialAccount account = accounts.findByIdForUpdate(r.financialAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", r.financialAccountId()));
        if (!r.paymentMethod().fits(account.getAccountType())) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, r.paymentMethod() + " needs a "
                    + (r.paymentMethod() == PaymentMethod.CASH ? "cash" : "bank") + " account; "
                    + account.getAccountName() + " is " + account.getAccountType() + ".");
        }
        ExpenseKind kind = r.expenseKind() != null ? r.expenseKind() : ExpenseKind.BUSINESS;
        String number = numbers.next("EXP");
        Expense expense = expenses.saveAndFlush(Expense.record(number, normalizedCategory, kind,
                r.recurrence() != null ? r.recurrence() : Recurrence.ONE_TIME, Money.round(r.amount()),
                r.expenseDate(), account.getId(), r.paymentMethod(), blankToNull(r.description()),
                blankToNull(r.documentReference())));
        finance.recordTransaction(new TransactionCommand(account.getId(), Direction.OUT, expense.getAmount(),
                kind.transactionType(), r.expenseDate(), "Expense " + number + " " + normalizedCategory, number,
                TransactionReferenceType.EXPENSE, expense.getId()));
        log.info("event=expense_recorded number={} kind={} category={} amount={}", number, kind, normalizedCategory,
                expense.getAmount());
        return views(List.of(expense)).getFirst();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        return views(List.of(find(id))).getFirst();
    }

    // E-FIN-13.
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(String category, ExpenseKind kind, Recurrence recurrence,
                                              UUID accountId, RecordStatus status, LocalDate from, LocalDate to,
                                              Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<Expense> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Expense> part : Arrays.asList(equal("category", category), equal("expenseKind", kind),
                equal("recurrence", recurrence), equal("accountId", accountId), equal("status", status),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), to))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        Page<Expense> page = expenses.findAll(filter, pageable);
        Map<UUID, ExpenseResponse> rows = views(page.getContent()).stream()
                .collect(Collectors.toMap(ExpenseResponse::id, v -> v));
        return PageResponse.from(page.map(e -> rows.get(e.getId())));
    }

    // E-FIN-15. Reversed expenses are left out; personal drawings are totalled apart (FR-FIN-12).
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(LocalDate from, LocalDate to) {
        ListParameters.requireDateRange(from, to);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal[]> byMonth = new TreeMap<>();
        BigDecimal business = Money.zero();
        BigDecimal personal = Money.zero();
        for (Expense e : expenses.findRecordedBetween(from, to)) {
            BigDecimal[] month = byMonth.computeIfAbsent(YearMonth.from(e.getExpenseDate()),
                    m -> new BigDecimal[] {Money.zero(), Money.zero()});
            if (e.getExpenseKind() == ExpenseKind.BUSINESS) {
                business = business.add(e.getAmount());
                byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
                month[0] = month[0].add(e.getAmount());
            } else {
                personal = personal.add(e.getAmount());
                month[1] = month[1].add(e.getAmount());
            }
        }
        return new ExpenseSummaryResponse(from, to, business, personal,
                byCategory.entrySet().stream()
                        .map(c -> new ExpenseSummaryResponse.CategoryTotal(c.getKey(), c.getValue())).toList(),
                byMonth.entrySet().stream().map(m -> new ExpenseSummaryResponse.MonthTotal(m.getKey().toString(),
                        m.getValue()[0], m.getValue()[1])).toList());
    }

    // E-FIN-16. Lock order (ADR-028): idempotency key -> expense row -> account (inside FinanceService). The money
    // comes back IN with a REVERSAL row pointing at the original; the expense stays, marked REVERSED.
    @Transactional
    public ExpenseResponse reverse(UUID id, String reason) {
        Expense expense = expenses.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", id));
        expense.reverse(reason, clock.instant());                          // 409 if already reversed
        UUID original = originals(List.of(id)).get(id);
        finance.reverse(original, LocalDate.now(clock), "Reversal of " + expense.getExpenseNumber() + ": " + reason);
        audit.record(AuditAction.EXPENSE_REVERSED, "EXPENSE", expense.getId(), reason, Map.of(
                "expenseNumber", expense.getExpenseNumber(), "amount", expense.getAmount().toPlainString()));
        log.info("event=expense_reversed number={} amount={}", expense.getExpenseNumber(), expense.getAmount());
        return views(List.of(expenses.saveAndFlush(expense))).getFirst();
    }

    // ---- helpers --------------------------------------------------------------------------

    private Expense find(UUID id) {
        return expenses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    }

    private Map<UUID, UUID> originals(List<UUID> expenseIds) {
        Map<UUID, UUID> result = new HashMap<>();
        for (Object[] row : transactions.expenseTransactions(expenseIds)) {
            result.put((UUID) row[0], (UUID) row[1]);
        }
        return result;
    }

    // Accounts and ledger rows of all expenses, one query each.
    private List<ExpenseResponse> views(List<Expense> rows) {
        Map<UUID, AccountRef> refs = accounts.findAllById(rows.stream().map(Expense::getAccountId).distinct().toList())
                .stream().collect(Collectors.toMap(FinancialAccount::getId,
                        a -> new AccountRef(a.getId(), a.getAccountName(), a.getAccountType())));
        Map<UUID, UUID> ledger = originals(rows.stream().map(Expense::getId).toList());
        return rows.stream().map(e -> new ExpenseResponse(e.getId(), e.getExpenseNumber(), e.getCategory(),
                e.getExpenseKind(), e.getRecurrence(), e.getAmount(), e.getExpenseDate(), refs.get(e.getAccountId()),
                e.getPaymentMethod(), e.getDescription(), e.getDocumentReference(), e.getStatus(), e.getReversedAt(),
                e.getReversalReason(), ledger.get(e.getId()), e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy()))
                .toList();
    }

    private static Specification<Expense> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

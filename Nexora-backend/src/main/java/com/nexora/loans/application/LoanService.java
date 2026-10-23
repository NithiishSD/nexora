package com.nexora.loans.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.FinanceService;
import com.nexora.finance.application.LedgerLine;
import com.nexora.finance.application.LedgerQueryService;
import com.nexora.finance.application.TransactionCommand;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.loans.api.dto.CreateLoanRequest;
import com.nexora.loans.api.dto.ExistingLoanRequest;
import com.nexora.loans.api.dto.LoanPaymentRequest;
import com.nexora.loans.api.dto.LoanResponse;
import com.nexora.loans.domain.Loan;
import com.nexora.loans.domain.LoanStatus;
import com.nexora.loans.domain.LoanType;
import com.nexora.loans.infrastructure.LoanRepository;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

import com.nexora.finance.application.FinancialAccountService;
import com.nexora.loans.api.dto.LoanAdviceResponse;

// Loans (FR-LOAN-01..09): money borrowed is a liability, principal repaid reduces it, interest is an expense. Every
// rupee moves through FinanceService as a row linked to the loan. One public method = one use case = one transaction.
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    private static final Set<String> SORTABLE = Set.of("startDate", "outstandingPrincipal");

    private final LoanRepository loans;
    private final PartyService parties;
    private final FinanceService finance;
    private final FinancialAccountService accounts;
    private final LedgerQueryService ledger;
    private final BusinessNumberGenerator numbers;
    private final AuditService audit;
    private final Clock clock;

    public LoanService(LoanRepository loans, PartyService parties, FinanceService finance,
                       FinancialAccountService accounts, LedgerQueryService ledger,
                       BusinessNumberGenerator numbers, AuditService audit, Clock clock) {
        this.loans = loans;
        this.parties = parties;
        this.finance = finance;
        this.accounts = accounts;
        this.ledger = ledger;
        this.numbers = numbers;
        this.audit = audit;
        this.clock = clock;
    }

    // E-LOAN-09. Interest calculation and repayment advice (FR-LOAN-10). Read-only: never creates a payment.
    @Transactional(readOnly = true)
    public LoanAdviceResponse getAdvice(UUID id) {
        Loan loan = loans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Loan", id));
        List<LedgerLine> lines = ledger.linesOf(TransactionReferenceType.LOAN, List.of(id));
        BigDecimal repaid = principalRepaid(lines);
        BigDecimal outstanding = loan.outstanding(repaid);

        BigDecimal monthlyRate = deriveMonthlyRate(loan);
        BigDecimal interestSuggestion = (loan.getStatus() == LoanStatus.CLOSED || outstanding.compareTo(BigDecimal.ZERO) <= 0)
                ? Money.zero()
                : Money.round(outstanding.multiply(monthlyRate));

        BigDecimal availableFunds = accounts.cashPosition(clock.instant()).totalAvailable();

        List<LoanAdviceResponse.LoanAdviceOption> options = new ArrayList<>();
        // 1. Pay interest
        options.add(new LoanAdviceResponse.LoanAdviceOption(
                "PAY_INTEREST",
                interestSuggestion,
                availableFunds.subtract(interestSuggestion),
                interestSuggestion.compareTo(BigDecimal.ZERO) > 0
                        ? "Pay monthly interest of " + interestSuggestion + " to service loan"
                        : "No interest due"));

        // 2. Part principal
        BigDecimal partPrincipal = Money.zero();
        if (loan.getStatus() == LoanStatus.ACTIVE && outstanding.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal twentyPercent = outstanding.multiply(new BigDecimal("0.20"));
            BigDecimal rounded = twentyPercent.divide(new BigDecimal("1000"), 0, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("1000"));
            if (rounded.compareTo(BigDecimal.ZERO) <= 0) {
                rounded = outstanding.min(new BigDecimal("5000.00"));
            }
            partPrincipal = Money.round(rounded.min(outstanding));
        }
        options.add(new LoanAdviceResponse.LoanAdviceOption(
                "PART_PRINCIPAL",
                partPrincipal,
                availableFunds.subtract(partPrincipal),
                partPrincipal.compareTo(BigDecimal.ZERO) > 0
                        ? "Pay partial principal of " + partPrincipal + " to reduce outstanding balance to "
                                + outstanding.subtract(partPrincipal)
                        : "No principal outstanding"));

        // 3. Full closure
        BigDecimal closureAmount = outstanding.add(interestSuggestion);
        options.add(new LoanAdviceResponse.LoanAdviceOption(
                "FULL_CLOSURE",
                closureAmount,
                availableFunds.subtract(closureAmount),
                closureAmount.compareTo(BigDecimal.ZERO) > 0
                        ? "Full closure: pay principal of " + outstanding + " and interest of " + interestSuggestion + " to close loan"
                        : "Loan is already fully repaid"));

        return new LoanAdviceResponse(
                loan.getId(),
                loan.getLoanNumber(),
                outstanding,
                loan.getInterestRatePercent(),
                interestSuggestion,
                availableFunds,
                options);
    }

    private static BigDecimal deriveMonthlyRate(Loan loan) {
        if (loan.getInterestRatePercent() != null && loan.getInterestRatePercent().compareTo(BigDecimal.ZERO) > 0) {
            if (loan.getLoanType() == LoanType.BANK) {
                return loan.getInterestRatePercent().divide(new BigDecimal("1200"), 6, java.math.RoundingMode.HALF_UP);
            } else {
                return loan.getInterestRatePercent().divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
            }
        }
        if (loan.getInterestNotes() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)\\s*%").matcher(loan.getInterestNotes());
            if (m.find()) {
                BigDecimal pct = new BigDecimal(m.group(1));
                boolean annual = loan.getInterestNotes().toLowerCase().contains("annu") || loan.getInterestNotes().toLowerCase().contains("p.a");
                return annual
                        ? pct.divide(new BigDecimal("1200"), 6, java.math.RoundingMode.HALF_UP)
                        : pct.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    // E-LOAN-01. Lender active with LENDER (404/422), method fits the account (422); lock order: key -> LON number ->
    // account (inside FinanceService). The money arrives as LOAN_RECEIVED IN: a liability, never revenue.
    @Transactional
    public LoanResponse create(CreateLoanRequest r) {
        parties.requireActiveWithRole(r.lenderId(), PartyRole.LENDER);
        finance.requireMethodFits(r.financialAccountId(), r.paymentMethod());
        Loan loan = loans.saveAndFlush(Loan.register(numbers.next("LON"), r.lenderId(), r.loanType(),
                r.principalAmount(), r.startDate(), r.dueDate(), r.interestRatePercent(), blankToNull(r.interestNotes()),
                blankToNull(r.notes()), null));
        finance.recordTransaction(new TransactionCommand(r.financialAccountId(), Direction.IN, loan.getPrincipalAmount(),
                TransactionType.LOAN_RECEIVED, r.startDate(), "Loan " + loan.getLoanNumber() + " received",
                loan.getLoanNumber(), TransactionReferenceType.LOAN, loan.getId()));
        log.info("event=loan_received number={} principal={}", loan.getLoanNumber(), loan.getPrincipalAmount());
        return detail(loan);
    }

    // E-LOAN-02. A loan running before go-live: what was already repaid is stored, no money moves (NFR-DATA-04).
    @Transactional
    public LoanResponse registerExisting(ExistingLoanRequest r) {
        parties.requireActiveWithRole(r.lenderId(), PartyRole.LENDER);
        Loan loan = loans.saveAndFlush(Loan.register(numbers.next("LON"), r.lenderId(), r.loanType(),
                r.principalAmount(), r.startDate(), r.dueDate(), r.interestRatePercent(), blankToNull(r.interestNotes()),
                blankToNull(r.notes()), r.outstandingPrincipal()));
        log.info("event=loan_registered_existing number={} outstanding={}", loan.getLoanNumber(), r.outstandingPrincipal());
        return detail(loan);
    }

    @Transactional(readOnly = true)
    public LoanResponse get(UUID id) {
        return detail(loans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Loan", id)));
    }

    // E-LOAN-03. outstandingPrincipal is derived, so the (few) matching loans are sorted and paged in memory.
    // ponytail: loads every matching loan per request; a SQL-side sort if there are ever thousands of loans.
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> list(LoanStatus status, UUID lenderId, LoanType loanType, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        Specification<Loan> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Loan> part : Arrays.asList(equal("status", status), equal("lenderId", lenderId),
                equal("loanType", loanType))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        List<LoanResponse> all = views(loans.findAll(filter), false);
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(Sort.Order.desc("startDate"));
        Comparator<LoanResponse> by = order.getProperty().equals("outstandingPrincipal")
                ? Comparator.comparing(LoanResponse::outstandingPrincipal)
                : Comparator.comparing(LoanResponse::startDate);
        List<LoanResponse> sorted = all.stream().sorted(order.isDescending() ? by.reversed() : by).toList();
        int start = (int) Math.min(pageable.getOffset(), sorted.size());
        List<LoanResponse> content = sorted.subList(start, Math.min(start + pageable.getPageSize(), sorted.size()));
        return PageResponse.from(new PageImpl<>(content, pageable, sorted.size()));
    }

    // Every loan with its derived figures, by number (loans and chits report, FR-RPT-14).
    @Transactional(readOnly = true)
    public List<LoanResponse> all() {
        return views(loans.findAll(Sort.by("loanNumber")), false);
    }

    // Principal still owed on every ACTIVE loan, from the same derivation as the loan views (dashboard, FR-RPT-01).
    @Transactional(readOnly = true)
    public BigDecimal activeOutstandingPrincipal() {
        return views(loans.findAll(equal("status", LoanStatus.ACTIVE)), false).stream()
                .map(LoanResponse::outstandingPrincipal).reduce(Money.zero(), BigDecimal::add);
    }

    // E-LOAN-05. Lock order: key -> loan row -> account (inside FinanceService). Not above the outstanding principal
    // (409 PAYMENT_ALLOCATION_EXCEEDED), not on a closed loan (409), not above the account balance (409).
    @Transactional
    public LoanResponse repayPrincipal(UUID id, LoanPaymentRequest r) {
        Loan loan = lock(id);
        BigDecimal amount = Money.round(r.amount());
        loan.requireRepayable(amount, principalRepaid(ledger.linesOf(TransactionReferenceType.LOAN, List.of(id))));
        pay(loan, r, TransactionType.LOAN_PRINCIPAL_REPAYMENT, "Principal repayment");
        return detail(loan);
    }

    // E-LOAN-06. Interest is an expense (FR-LOAN-08) and never touches the principal; entered by hand (FR-LOAN-10).
    @Transactional
    public LoanResponse payInterest(UUID id, LoanPaymentRequest r) {
        Loan loan = lock(id);
        loan.requireActive();
        pay(loan, r, TransactionType.LOAN_INTEREST_PAYMENT, "Interest");
        return detail(loan);
    }

    // E-LOAN-07. Only a principal or interest payment of this loan (404 otherwise), once (409); the money comes back
    // IN as a REVERSAL row and the outstanding is recomputed from the ledger.
    @Transactional
    public LoanResponse reversePayment(UUID id, UUID transactionId, String reason) {
        Loan loan = lock(id);
        LedgerLine line = ledger.linesOf(TransactionReferenceType.LOAN, List.of(id)).stream()
                .filter(l -> l.transactionId().equals(transactionId))
                .filter(l -> l.transactionType() != TransactionType.LOAN_RECEIVED)
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Loan payment", transactionId));
        if (line.reversed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "This loan payment is already reversed.");
        }
        finance.reverse(transactionId, LocalDate.now(clock), "Reversal of a " + loan.getLoanNumber() + " payment: "
                + reason);
        audit.record(AuditAction.LOAN_PAYMENT_REVERSED, "LOAN", loan.getId(), reason, Map.of(
                "loanNumber", loan.getLoanNumber(), "transactionId", transactionId.toString(),
                "transactionType", line.transactionType().name(), "amount", line.amount().toPlainString()));
        log.info("event=loan_payment_reversed number={} type={} amount={}", loan.getLoanNumber(),
                line.transactionType(), line.amount());
        return detail(loan);
    }

    // E-LOAN-08: only at 0.00 principal outstanding.
    @Transactional
    public LoanResponse close(UUID id) {
        Loan loan = lock(id);
        loan.close(principalRepaid(ledger.linesOf(TransactionReferenceType.LOAN, List.of(id))));
        log.info("event=loan_closed number={}", loan.getLoanNumber());
        return detail(loans.saveAndFlush(loan));
    }

    // ---- helpers --------------------------------------------------------------------------

    private void pay(Loan loan, LoanPaymentRequest r, TransactionType type, String what) {
        finance.requireMethodFits(r.financialAccountId(), r.paymentMethod());
        String text = Stream.of(r.periodNote(), r.notes()).filter(s -> s != null && !s.isBlank()).map(String::strip)
                .collect(Collectors.joining(" - "));
        finance.recordTransaction(new TransactionCommand(r.financialAccountId(), Direction.OUT, Money.round(r.amount()),
                type, r.paymentDate(), text.isEmpty() ? what + " " + loan.getLoanNumber() : text, loan.getLoanNumber(),
                TransactionReferenceType.LOAN, loan.getId()));
        log.info("event=loan_payment number={} type={} amount={}", loan.getLoanNumber(), type, r.amount());
    }

    private Loan lock(UUID id) {
        return loans.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Loan", id));
    }

    private static BigDecimal principalRepaid(List<LedgerLine> lines) {
        return sum(lines, TransactionType.LOAN_PRINCIPAL_REPAYMENT);
    }

    // Rows of one type that still count (not reversed).
    private static BigDecimal sum(List<LedgerLine> lines, TransactionType type) {
        return lines.stream().filter(l -> l.transactionType() == type && !l.reversed()).map(LedgerLine::amount)
                .reduce(Money.zero(), BigDecimal::add);
    }

    private LoanResponse detail(Loan loan) {
        return views(List.of(loan), true).getFirst();
    }

    // Lenders and ledger rows of all loans, one query each.
    private List<LoanResponse> views(List<Loan> rows, boolean withPayments) {
        Map<UUID, PartyRef> lenders = parties.getRefs(rows.stream().map(Loan::getLenderId).distinct().toList());
        Map<UUID, List<LedgerLine>> byLoan = ledger.linesOf(TransactionReferenceType.LOAN,
                rows.stream().map(Loan::getId).toList()).stream()
                .collect(Collectors.groupingBy(LedgerLine::referenceId));
        return rows.stream().map(l -> {
            List<LedgerLine> lines = byLoan.getOrDefault(l.getId(), List.of());
            BigDecimal repaid = principalRepaid(lines);
            UUID received = lines.stream().filter(x -> x.transactionType() == TransactionType.LOAN_RECEIVED)
                    .map(LedgerLine::transactionId).findFirst().orElse(null);
            List<LoanResponse.Payment> payments = !withPayments ? null : lines.stream()
                    .filter(x -> x.transactionType() != TransactionType.LOAN_RECEIVED)
                    .map(x -> new LoanResponse.Payment(x.transactionId(), x.transactionType(), x.amount(), x.date(),
                            x.account(), x.account().accountType() == AccountType.CASH ? "CASH" : null,
                            x.transactionType() == TransactionType.LOAN_INTEREST_PAYMENT ? x.description() : null,
                            x.reversed(), x.reversalTransactionId()))
                    .toList();
            return new LoanResponse(l.getId(), l.getLoanNumber(), lenders.get(l.getLenderId()), l.getLoanType(),
                    l.getPrincipalAmount(), l.getRepaidBeforeGoLive(), repaid, l.outstanding(repaid),
                    sum(lines, TransactionType.LOAN_INTEREST_PAYMENT), l.getStartDate(), l.getDueDate(),
                    l.getInterestRatePercent(), l.getInterestNotes(), l.getStatus(), received == null, received,
                    payments, l.getNotes(), l.getCreatedAt(), l.getUpdatedAt(), l.getCreatedBy());
        }).toList();
    }

    private static Specification<Loan> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

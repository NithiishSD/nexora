package com.nexora.chits.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.chits.api.dto.ChitBidRequest;
import com.nexora.chits.api.dto.ChitBidResponse;
import com.nexora.chits.api.dto.ChitEntryRequest;
import com.nexora.chits.api.dto.ChitResponse;
import com.nexora.chits.api.dto.CreateChitRequest;
import com.nexora.chits.domain.Chit;
import com.nexora.chits.domain.ChitStatus;
import com.nexora.chits.infrastructure.ChitRepository;
import com.nexora.finance.application.FinanceService;
import com.nexora.finance.application.LedgerLine;
import com.nexora.finance.application.LedgerQueryService;
import com.nexora.finance.application.TransactionCommand;
import com.nexora.finance.domain.PaymentMethod;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Chits (FR-CHIT-01..09): contributions and organiser payments go OUT, the payout comes IN, all in physical cash and
// all as ledger rows linked to the chit; investments, never expenses or revenue (FR-CHIT-07). One public method = one
// use case = one transaction.
@Service
public class ChitService {

    private static final Logger log = LoggerFactory.getLogger(ChitService.class);
    private static final Set<String> SORTABLE = Set.of("startDate");

    private final ChitRepository chits;
    private final FinanceService finance;
    private final LedgerQueryService ledger;
    private final BusinessNumberGenerator numbers;
    private final AuditService audit;
    private final Clock clock;

    public ChitService(ChitRepository chits, FinanceService finance, LedgerQueryService ledger,
                       BusinessNumberGenerator numbers, AuditService audit, Clock clock) {
        this.chits = chits;
        this.finance = finance;
        this.ledger = ledger;
        this.numbers = numbers;
        this.audit = audit;
        this.clock = clock;
    }

    // E-CHIT-01. Prior contributions (a chit running at go-live) are counted without any cash row.
    @Transactional
    public ChitResponse create(CreateChitRequest r) {
        Chit chit = chits.saveAndFlush(Chit.create(numbers.next("CHT"), r.chitName().strip(), r.totalAmount(),
                r.monthlyAmount(), r.durationMonths(), r.startDate(), r.priorContributionsCount(),
                r.priorContributionsAmount(), blankToNull(r.notes())));
        log.info("event=chit_created number={} months={} prior={}", chit.getChitNumber(), chit.getDurationMonths(),
                chit.getPriorContributionsCount());
        return detail(chit);
    }

    @Transactional(readOnly = true)
    public ChitResponse get(UUID id) {
        return detail(chits.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chit", id)));
    }

    // E-CHIT-02.
    @Transactional(readOnly = true)
    public PageResponse<ChitResponse> list(ChitStatus status, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        Specification<Chit> filter = status == null ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
        Page<Chit> page = chits.findAll(filter, pageable);
        Map<UUID, ChitResponse> rows = views(page.getContent(), false).stream()
                .collect(Collectors.toMap(ChitResponse::id, v -> v));
        return PageResponse.from(page.map(c -> rows.get(c.getId())));
    }

    // Every chit with its derived figures, by number (loans and chits report, FR-RPT-14).
    @Transactional(readOnly = true)
    public List<ChitResponse> all() {
        return views(chits.findAll(Sort.by("chitNumber")), false);
    }

    // E-CHIT-04. Lock order: key -> chit row -> account (inside FinanceService, 409 INSUFFICIENT_FUNDS).
    @Transactional
    public ChitResponse contribute(UUID id, ChitEntryRequest r) {
        Chit chit = lock(id);
        chit.requireOpen();
        entry(chit, r, TransactionType.CHIT_CONTRIBUTION, "Contribution");
        return detail(chit);
    }

    // E-CHIT-05. ACTIVE -> MATURED; the money comes IN.
    @Transactional
    public ChitResponse payout(UUID id, ChitEntryRequest r) {
        Chit chit = lock(id);
        chit.receivePayout();
        entry(chit, r, TransactionType.CHIT_PAYOUT, "Payout");
        return detail(chits.saveAndFlush(chit));
    }

    // E-CHIT-06. Not a contribution: counted apart (FR-CHIT-04).
    @Transactional
    public ChitResponse payOrganiser(UUID id, ChitEntryRequest r) {
        Chit chit = lock(id);
        chit.requireOpen();
        entry(chit, r, TransactionType.CHIT_ORGANISER_PAYMENT, "Organiser payment");
        return detail(chit);
    }

    // E-CHIT-07. Only an entry of this chit (404), once (409); a reversed payout needs the cash back out (409
    // INSUFFICIENT_FUNDS) and takes a MATURED chit back to ACTIVE.
    @Transactional
    public ChitResponse reverseEntry(UUID id, UUID transactionId, String reason) {
        Chit chit = lock(id);
        LedgerLine line = ledger.linesOf(TransactionReferenceType.CHIT, List.of(id)).stream()
                .filter(l -> l.transactionId().equals(transactionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Chit entry", transactionId));
        if (line.reversed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "This chit entry is already reversed.");
        }
        finance.reverse(transactionId, LocalDate.now(clock), "Reversal of a " + chit.getChitNumber() + " entry: "
                + reason);
        if (line.transactionType() == TransactionType.CHIT_PAYOUT) {
            chit.payoutReversed();
        }
        audit.record(AuditAction.CHIT_ENTRY_REVERSED, "CHIT", chit.getId(), reason, Map.of(
                "chitNumber", chit.getChitNumber(), "transactionId", transactionId.toString(),
                "transactionType", line.transactionType().name(), "amount", line.amount().toPlainString()));
        log.info("event=chit_entry_reversed number={} type={} amount={}", chit.getChitNumber(), line.transactionType(),
                line.amount());
        return detail(chits.saveAndFlush(chit));
    }

    // E-CHIT-08: ACTIVE or MATURED -> CLOSED; history stays.
    @Transactional
    public ChitResponse close(UUID id) {
        Chit chit = lock(id);
        chit.close();
        log.info("event=chit_closed number={}", chit.getChitNumber());
        return detail(chits.saveAndFlush(chit));
    }

    // E-CHIT-09. Monthly bid and dividend calculation (FR-CHIT-10).
    @Transactional(readOnly = true)
    public ChitBidResponse calculateBid(UUID id, ChitBidRequest request) {
        Chit chit = chits.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chit", id));
        if (chit.getStatus() == ChitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Cannot calculate bid on a closed chit.");
        }
        BigDecimal bidAmount = Money.round(request.bidAmount());
        if (bidAmount.compareTo(chit.getTotalAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Bid amount cannot exceed total chit amount of "
                    + chit.getTotalAmount());
        }
        int durationMonths = chit.getDurationMonths();
        BigDecimal dividendPerMember = Money.round(bidAmount.divide(
                BigDecimal.valueOf(durationMonths), 2, java.math.RoundingMode.HALF_UP));
        BigDecimal contributionDue = chit.getMonthlyAmount().subtract(dividendPerMember).max(BigDecimal.ZERO);

        return new ChitBidResponse(
                chit.getId(),
                chit.getChitNumber(),
                request.month(),
                bidAmount,
                durationMonths,
                dividendPerMember,
                chit.getMonthlyAmount(),
                contributionDue);
    }

    // ---- helpers --------------------------------------------------------------------------

    // Physical cash only: the CASH method on a CASH account (422 otherwise); FinanceService checks the balance.
    private void entry(Chit chit, ChitEntryRequest r, TransactionType type, String what) {
        if (r.paymentMethod() != PaymentMethod.CASH) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD, "Chit money is paid and received in cash"
                    + " only; " + r.paymentMethod() + " is not allowed.");
        }
        finance.requireMethodFits(r.financialAccountId(), r.paymentMethod());
        finance.recordTransaction(new TransactionCommand(r.financialAccountId(), type.direction(),
                Money.round(r.amount()), type, r.entryDate(), r.notes() == null || r.notes().isBlank()
                ? what + " " + chit.getChitNumber() : r.notes().strip(), chit.getChitNumber(),
                TransactionReferenceType.CHIT, chit.getId()));
        log.info("event=chit_entry number={} type={} amount={}", chit.getChitNumber(), type, r.amount());
    }

    private Chit lock(UUID id) {
        return chits.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Chit", id));
    }

    private ChitResponse detail(Chit chit) {
        return views(List.of(chit), true).getFirst();
    }

    // Ledger rows of all chits in one query; reversed rows count for nothing.
    private List<ChitResponse> views(List<Chit> rows, boolean withEntries) {
        Map<UUID, List<LedgerLine>> byChit = ledger.linesOf(TransactionReferenceType.CHIT,
                rows.stream().map(Chit::getId).toList()).stream()
                .collect(Collectors.groupingBy(LedgerLine::referenceId));
        return rows.stream().map(c -> {
            List<LedgerLine> lines = byChit.getOrDefault(c.getId(), List.of());
            List<LedgerLine> contributions = counting(lines, TransactionType.CHIT_CONTRIBUTION);
            List<LedgerLine> payouts = counting(lines, TransactionType.CHIT_PAYOUT);
            int count = c.getPriorContributionsCount() + contributions.size();
            return new ChitResponse(c.getId(), c.getChitNumber(), c.getChitName(), c.getTotalAmount(),
                    c.getMonthlyAmount(), c.getDurationMonths(), c.getStartDate(), c.getStatus(),
                    c.getPriorContributionsCount(), c.getPriorContributionsAmount(), count,
                    c.getPriorContributionsAmount().add(total(contributions)),
                    Math.max(0, c.getDurationMonths() - count),
                    total(counting(lines, TransactionType.CHIT_ORGANISER_PAYMENT)),
                    payouts.isEmpty() ? null : total(payouts),
                    !withEntries ? null : lines.stream().map(l -> new ChitResponse.Entry(l.transactionId(),
                            l.transactionType(), l.amount(), l.date(), l.reversed())).toList(),
                    c.getNotes(), c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBy());
        }).toList();
    }

    private static List<LedgerLine> counting(List<LedgerLine> lines, TransactionType type) {
        return lines.stream().filter(l -> l.transactionType() == type && !l.reversed()).toList();
    }

    private static BigDecimal total(List<LedgerLine> lines) {
        return lines.stream().map(LedgerLine::amount).reduce(Money.zero(), BigDecimal::add);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

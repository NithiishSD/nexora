package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.chits.application.ChitService;
import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.application.FinancialAccountService;
import com.nexora.finance.domain.Direction;
import com.nexora.loans.application.LoanService;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.reporting.api.dto.CashBankReportLine;
import com.nexora.reporting.api.dto.LoansAndChitsReport;
import com.nexora.reporting.api.dto.WorkerEfficiencyRow;
import com.nexora.reporting.api.dto.WorkerPayableReportLine;
import com.nexora.reporting.infrastructure.FinanceReportRepository;
import com.nexora.reporting.infrastructure.FinanceReportRepository.LedgerSums;
import com.nexora.reporting.infrastructure.FinanceReportRepository.WorkRow;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.workforce.domain.WorkType;

// Money reports (FR-RPT-12..14): account movements from the ledger, worker earnings against their allocations, and
// the loan and chit views their own modules derive.
@Service
public class FinanceReportQuery {

    private final FinanceReportRepository finance;
    private final FinancialAccountService accounts;
    private final AllocationQueryService allocations;
    private final LoanService loans;
    private final ChitService chits;
    private final PartyService parties;

    public FinanceReportQuery(FinanceReportRepository finance, FinancialAccountService accounts,
                              AllocationQueryService allocations, LoanService loans, ChitService chits,
                              PartyService parties) {
        this.finance = finance;
        this.accounts = accounts;
        this.allocations = allocations;
        this.loans = loans;
        this.chits = chits;
        this.parties = parties;
    }

    // E-RPT-12: every account (by name), or one (unknown 404). Types listed only when they moved in the period.
    @Transactional(readOnly = true)
    public List<CashBankReportLine> cashBank(LocalDate from, LocalDate to, UUID accountId) {
        ListParameters.requireDateRange(from, to);
        if (accountId != null) {
            accounts.get(accountId);
        }
        Map<UUID, List<LedgerSums>> byAccount = finance.ledgerSums(accountId, from, to).stream()
                .collect(Collectors.groupingBy(LedgerSums::accountId, LinkedHashMap::new, Collectors.toList()));
        List<CashBankReportLine> lines = new ArrayList<>();
        for (List<LedgerSums> sums : byAccount.values()) {
            LedgerSums first = sums.getFirst();
            BigDecimal opening = Money.round(sums.stream().map(LedgerSums::beforeNet)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            List<CashBankReportLine.TypeTotal> in = byType(sums, Direction.IN);
            List<CashBankReportLine.TypeTotal> out = byType(sums, Direction.OUT);
            BigDecimal totalIn = in.stream().map(CashBankReportLine.TypeTotal::amount).reduce(Money.zero(), BigDecimal::add);
            BigDecimal totalOut = out.stream().map(CashBankReportLine.TypeTotal::amount)
                    .reduce(Money.zero(), BigDecimal::add);
            lines.add(new CashBankReportLine(new AccountRef(first.accountId(), first.accountName(), first.accountType()),
                    opening, in, out, totalIn, totalOut, opening.add(totalIn).subtract(totalOut)));
        }
        return lines;
    }

    // E-RPT-13: workers with recorded work in the period (by name), or one (404 unknown, 422 not a worker).
    @Transactional(readOnly = true)
    public List<WorkerPayableReportLine> workerPayable(LocalDate from, LocalDate to, UUID workerId) {
        ListParameters.requireDateRange(from, to);
        if (workerId != null) {
            parties.requireRole(workerId, PartyRole.WORKER);
        }
        List<WorkRow> work = finance.work(from, to, workerId);
        Map<UUID, BigDecimal> paid = allocations.allocatedByWorkRecord(work.stream().map(WorkRow::id).toList());
        Map<UUID, BigDecimal[]> byWorker = new LinkedHashMap<>();     // rolling, warping, paid
        for (WorkRow w : work) {
            BigDecimal[] sums = byWorker.computeIfAbsent(w.workerId(),
                    k -> new BigDecimal[] {Money.zero(), Money.zero(), Money.zero()});
            sums[w.workType() == WorkType.ROLLING ? 0 : 1] = sums[w.workType() == WorkType.ROLLING ? 0 : 1]
                    .add(w.amount());
            sums[2] = sums[2].add(paid.getOrDefault(w.id(), BigDecimal.ZERO));
        }
        Map<UUID, PartyRef> refs = parties.getRefs(byWorker.keySet());
        return byWorker.entrySet().stream().map(e -> {
            BigDecimal[] s = e.getValue();
            BigDecimal earned = s[0].add(s[1]);
            return new WorkerPayableReportLine(refs.get(e.getKey()), s[0], s[1], earned, Money.round(s[2]),
                    Money.round(earned.subtract(s[2])));
        }).sorted(Comparator.comparing(l -> l.worker().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // E-RPT-14: every loan and chit with the figures their detail endpoints show.
    @Transactional(readOnly = true)
    public LoansAndChitsReport loansAndChits() {
        return new LoansAndChitsReport(
                loans.all().stream().map(l -> new LoansAndChitsReport.LoanLine(l.loanNumber(), l.lender(),
                        l.principalAmount(), l.outstandingPrincipal(), l.interestPaid(), l.status())).toList(),
                chits.all().stream().map(c -> new LoansAndChitsReport.ChitLine(c.chitNumber(), c.chitName(),
                        c.totalContributed(), c.remainingExpectedContributions(), c.organiserPayments(),
                        c.payoutReceived(), c.status())).toList());
    }

    // FR-WORK-10, E-WORK-05: worker efficiency over a date range.
    @Transactional(readOnly = true)
    public List<WorkerEfficiencyRow> workerEfficiency(LocalDate from, LocalDate to) {
        ListParameters.requireDateRange(from, to);
        List<FinanceReportRepository.WorkerEfficiencyAggregate> aggs = finance.workerEfficiency(from, to);
        if (aggs.isEmpty()) {
            return List.of();
        }
        Map<UUID, PartyRef> refs = parties.getRefs(aggs.stream()
                .map(FinanceReportRepository.WorkerEfficiencyAggregate::workerId)
                .collect(Collectors.toSet()));
        return aggs.stream().map(agg -> {
            PartyRef worker = refs.get(agg.workerId());
            BigDecimal hours = agg.hours() == null
                    ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                    : agg.hours().setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal kg = agg.kg() == null
                    ? BigDecimal.ZERO.setScale(3, java.math.RoundingMode.HALF_UP)
                    : agg.kg().setScale(3, java.math.RoundingMode.HALF_UP);
            BigDecimal kgPerHour = hours.signum() > 0
                    ? kg.divide(hours, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
            return new WorkerEfficiencyRow(worker, kgPerHour, hours, kg);
        }).sorted(Comparator.comparing(r -> r.worker().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static List<CashBankReportLine.TypeTotal> byType(List<LedgerSums> sums, Direction direction) {
        return sums.stream()
                .filter(s -> s.direction() == direction && s.inPeriod().signum() != 0)
                .sorted(Comparator.comparing(LedgerSums::type))
                .map(s -> new CashBankReportLine.TypeTotal(s.type(), Money.round(s.inPeriod())))
                .toList();
    }
}

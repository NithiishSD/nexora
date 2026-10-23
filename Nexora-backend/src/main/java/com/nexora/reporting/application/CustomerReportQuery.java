package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.domain.CustomerBalance;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.reporting.api.dto.CustomerOutstandingReport;
import com.nexora.reporting.api.dto.CustomerStatementResponse;
import com.nexora.reporting.infrastructure.CustomerReportRepository;
import com.nexora.reporting.infrastructure.CustomerReportRepository.Entry;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;

// Customer reports (FR-RPT-03, FR-RPT-04): balances come from finance's outstanding formula, the statement from the
// raw debits and credits; nothing is stored.
@Service
public class CustomerReportQuery {

    private static final Set<String> SORTABLE = Set.of("outstanding", "oldestAgeDays", "name");

    private final AllocationQueryService allocations;
    private final CustomerReportRepository reports;
    private final PartyService parties;
    private final Clock clock;

    public CustomerReportQuery(AllocationQueryService allocations, CustomerReportRepository reports,
                               PartyService parties, Clock clock) {
        this.allocations = allocations;
        this.reports = reports;
        this.parties = parties;
        this.clock = clock;
    }

    // E-RPT-02. Ages are counted up to asOf (default today); amounts are the current ones.
    // ponytail: rows are sorted and paged in memory (one row per customer); SQL paging if customers reach thousands.
    @Transactional(readOnly = true)
    public CustomerOutstandingReport outstanding(BigDecimal minOutstanding, LocalDate asOf, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        LocalDate day = asOf == null ? LocalDate.now(clock) : asOf;
        List<CustomerBalance> balances = allocations.customerBalances().stream()
                .filter(b -> minOutstanding == null || b.outstanding().compareTo(minOutstanding) >= 0)
                .toList();
        Map<UUID, PartyRef> refs = parties.getRefs(balances.stream().map(CustomerBalance::customerId).toList());
        List<CustomerOutstandingReport.Row> rows = balances.stream()
                .map(b -> new CustomerOutstandingReport.Row(refs.get(b.customerId()), Money.round(b.outstanding()),
                        Money.round(b.advance()), b.unpaidOrders(), b.oldestUnpaidDate() == null ? null
                        : (int) ChronoUnit.DAYS.between(b.oldestUnpaidDate(), day)))
                .sorted(order(pageable.getSort()))
                .toList();
        BigDecimal outstanding = rows.stream().map(CustomerOutstandingReport.Row::outstanding)
                .reduce(Money.zero(), BigDecimal::add);
        BigDecimal advance = rows.stream().map(CustomerOutstandingReport.Row::advance)
                .reduce(Money.zero(), BigDecimal::add);
        int start = (int) Math.min(pageable.getOffset(), rows.size());
        int size = pageable.getPageSize();
        return new CustomerOutstandingReport(day, rows.subList(start, Math.min(start + size, rows.size())),
                new CustomerOutstandingReport.Totals(outstanding, advance), pageable.getPageNumber(), size,
                rows.size(), (rows.size() + size - 1) / size);
    }

    // E-RPT-03. 404 unknown, 422 not a customer; 400 when from is after to.
    @Transactional(readOnly = true)
    public CustomerStatementResponse statement(UUID customerId, LocalDate from, LocalDate to) {
        ListParameters.requireDateRange(from, to);
        PartyRef customer = parties.requireRole(customerId, PartyRole.CUSTOMER);
        BigDecimal balance = Money.zero();
        List<CustomerStatementResponse.Line> lines = new ArrayList<>();
        for (Entry e : reports.entries(customerId, to, clock.getZone().getId())) {
            balance = balance.add(e.debit()).subtract(e.credit());
            if (!e.date().isBefore(from)) {
                lines.add(new CustomerStatementResponse.Line(e.date(), e.kind(), e.number(), Money.round(e.debit()),
                        Money.round(e.credit()), Money.round(balance)));
            }
        }
        BigDecimal inPeriod = lines.stream().map(l -> l.debit().subtract(l.credit())).reduce(Money.zero(),
                BigDecimal::add);
        return new CustomerStatementResponse(customer, from, to, Money.round(balance.subtract(inPeriod)), lines,
                Money.round(balance));
    }

    private static Comparator<CustomerOutstandingReport.Row> order(Sort sort) {
        Sort.Order o = sort.stream().findFirst().orElse(Sort.Order.desc("outstanding"));
        Comparator<CustomerOutstandingReport.Row> by = switch (o.getProperty()) {
            case "name" -> Comparator.comparing(r -> r.customer().name(), String.CASE_INSENSITIVE_ORDER);
            case "oldestAgeDays" -> Comparator.comparing(CustomerOutstandingReport.Row::oldestAgeDays,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(CustomerOutstandingReport.Row::outstanding);
        };
        return o.isDescending() ? by.reversed() : by;
    }
}

package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.reporting.api.dto.SalesReport;
import com.nexora.reporting.infrastructure.SalesReportRepository;
import com.nexora.reporting.infrastructure.SalesReportRepository.ItemRow;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;

// FR-RPT-07: what was ordered and what was delivered (the revenue), per month, customer or product. Allocations come
// from finance in one query for every item of the period.
@Service
public class SalesReportQuery {

    private final SalesReportRepository sales;
    private final AllocationQueryService allocations;
    private final PartyService parties;

    public SalesReportQuery(SalesReportRepository sales, AllocationQueryService allocations, PartyService parties) {
        this.sales = sales;
        this.allocations = allocations;
        this.parties = parties;
    }

    @Transactional(readOnly = true)
    public SalesReport report(LocalDate from, LocalDate to, UUID customerId, ProductType productType,
                              SalesGrouping groupBy) {
        ListParameters.requireDateRange(from, to);
        if (customerId != null) {
            parties.requireRole(customerId, PartyRole.CUSTOMER);
        }
        List<ItemRow> items = sales.items(from, to, customerId, productType);
        Map<UUID, BigDecimal> allocated = allocations.allocatedByOrderItem(items.stream().map(ItemRow::itemId).toList());
        Map<String, Sums> groups = new TreeMap<>();
        for (ItemRow i : items) {
            if (within(i.orderDate(), from, to)) {
                BigDecimal owed = i.status().isObligation()
                        ? i.amount().subtract(allocated.getOrDefault(i.itemId(), BigDecimal.ZERO)) : BigDecimal.ZERO;
                groups.computeIfAbsent(key(groupBy, i, i.orderDate()), k -> new Sums(i)).placed(i.amount(), owed);
            }
            if (i.deliveredOn() != null && within(i.deliveredOn(), from, to)) {
                groups.computeIfAbsent(key(groupBy, i, i.deliveredOn()), k -> new Sums(i))
                        .delivered(i.amount(), i.weightKg());
            }
        }
        Map<UUID, PartyRef> refs = groupBy == SalesGrouping.CUSTOMER
                ? parties.getRefs(groups.values().stream().map(s -> s.customerId).distinct().toList()) : Map.of();
        List<SalesReport.Row> rows = groups.entrySet().stream()
                .map(e -> new SalesReport.Row(groupBy == SalesGrouping.MONTH ? e.getKey() : null,
                        groupBy == SalesGrouping.CUSTOMER ? refs.get(e.getValue().customerId) : null,
                        groupBy == SalesGrouping.PRODUCT ? e.getValue().productType : null,
                        Money.round(e.getValue().placed), Money.round(e.getValue().revenue),
                        Weight.round(e.getValue().kg), Money.round(e.getValue().outstanding)))
                .sorted(groupBy == SalesGrouping.CUSTOMER
                        ? Comparator.comparing((SalesReport.Row r) -> r.customer().name(), String.CASE_INSENSITIVE_ORDER)
                        : (a, b) -> 0)
                .toList();
        return new SalesReport(from, to, groupBy, rows, new SalesReport.Totals(
                rows.stream().map(SalesReport.Row::orderValuePlaced).reduce(Money.zero(), BigDecimal::add),
                rows.stream().map(SalesReport.Row::revenueDelivered).reduce(Money.zero(), BigDecimal::add),
                rows.stream().map(SalesReport.Row::kgDelivered).reduce(Weight.zero(), BigDecimal::add),
                rows.stream().map(SalesReport.Row::outstanding).reduce(Money.zero(), BigDecimal::add)));
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    // Month keys sort by time (yyyy-MM); products by enum name; customers by id here, by name after mapping.
    private static String key(SalesGrouping groupBy, ItemRow i, LocalDate date) {
        return switch (groupBy) {
            case MONTH -> YearMonth.from(date).toString();
            case CUSTOMER -> i.customerId().toString();
            case PRODUCT -> i.productType().name();
        };
    }

    private static final class Sums {
        private final UUID customerId;
        private final ProductType productType;
        private BigDecimal placed = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal kg = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;

        Sums(ItemRow first) {
            this.customerId = first.customerId();
            this.productType = first.productType();
        }

        void placed(BigDecimal amount, BigDecimal owed) {
            placed = placed.add(amount);
            outstanding = outstanding.add(owed);
        }

        void delivered(BigDecimal amount, BigDecimal weightKg) {
            revenue = revenue.add(amount);
            kg = kg.add(weightKg);
        }
    }
}

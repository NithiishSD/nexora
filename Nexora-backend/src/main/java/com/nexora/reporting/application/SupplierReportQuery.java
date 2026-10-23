package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.procurement.api.dto.OpenPayable;
import com.nexora.procurement.application.SupplierPayableQuery;
import com.nexora.reporting.api.dto.PurchaseReport;
import com.nexora.reporting.api.dto.SupplierPayablesReport;
import com.nexora.reporting.infrastructure.SupplierReportRepository;
import com.nexora.reporting.infrastructure.SupplierReportRepository.PurchaseGroup;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;

// Supplier reports (FR-RPT-05, FR-RPT-06). What is owed comes from procurement's payable query, so the ageing total
// equals the supplier payables overview (E-PAY-08) for the same suppliers.
@Service
public class SupplierReportQuery {

    private final SupplierPayableQuery payables;
    private final SupplierReportRepository reports;
    private final PartyService parties;
    private final Clock clock;

    public SupplierReportQuery(SupplierPayableQuery payables, SupplierReportRepository reports, PartyService parties,
                               Clock clock) {
        this.payables = payables;
        this.reports = reports;
        this.parties = parties;
        this.clock = clock;
    }

    // E-RPT-04. from/to (purchase date) and supplierId are optional; an unknown supplier is 404, a non-supplier 422.
    @Transactional(readOnly = true)
    public SupplierPayablesReport payablesAgeing(LocalDate from, LocalDate to, UUID supplierId) {
        ListParameters.requireDateRange(from, to);
        if (supplierId != null) {
            parties.requireRole(supplierId, PartyRole.SUPPLIER);
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<OpenPayable>> bySupplier = payables.openPayables(supplierId, from, to).stream()
                .collect(Collectors.groupingBy(OpenPayable::supplierId));
        Map<UUID, PartyRef> refs = parties.getRefs(bySupplier.keySet());
        Map<String, BigDecimal> overall = emptyBuckets();
        List<SupplierPayablesReport.SupplierGroup> groups = bySupplier.entrySet().stream().map(e -> {
            Map<String, BigDecimal> buckets = emptyBuckets();
            List<SupplierPayablesReport.Item> items = e.getValue().stream()
                    .sorted(Comparator.comparing(OpenPayable::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(OpenPayable::reference))
                    .map(p -> {
                        int overdueDays = p.dueDate() == null || !p.dueDate().isBefore(today) ? 0
                                : (int) ChronoUnit.DAYS.between(p.dueDate(), today);
                        String bucket = AgeingBucket.of(overdueDays).name();
                        buckets.merge(bucket, p.outstanding(), BigDecimal::add);
                        overall.merge(bucket, p.outstanding(), BigDecimal::add);
                        return new SupplierPayablesReport.Item(p.purchaseId(), p.reference(), p.date(), p.dueDate(),
                                p.outstanding(), overdueDays, bucket);
                    })
                    .toList();
            return new SupplierPayablesReport.SupplierGroup(refs.get(e.getKey()), buckets, sum(buckets), items);
        }).sorted(Comparator.comparing(SupplierPayablesReport.SupplierGroup::total).reversed()).toList();
        return new SupplierPayablesReport(today, groups, overall, sum(overall));
    }

    // E-RPT-05. from and to are required (purchase date, inclusive).
    @Transactional(readOnly = true)
    public PurchaseReport purchases(LocalDate from, LocalDate to, UUID supplierId, ProductType productType) {
        ListParameters.requireDateRange(from, to);
        if (supplierId != null) {
            parties.requireRole(supplierId, PartyRole.SUPPLIER);
        }
        List<PurchaseGroup> groups = reports.purchases(from, to, supplierId, productType);
        Map<UUID, PartyRef> refs = parties.getRefs(groups.stream().map(PurchaseGroup::supplierId).distinct().toList());
        List<PurchaseReport.Row> rows = groups.stream()
                .map(g -> new PurchaseReport.Row(refs.get(g.supplierId()), g.productType(), g.purchases(),
                        Weight.round(g.orderedKg()), Weight.round(g.receivedKg()), Weight.round(g.acceptedKg()),
                        Weight.round(g.rejectedKg()), Weight.round(g.returnedKg()), Money.round(g.orderedValue()),
                        Money.round(g.payableValue())))
                .sorted(Comparator.comparing((PurchaseReport.Row r) -> r.supplier().name(),
                        String.CASE_INSENSITIVE_ORDER).thenComparing(PurchaseReport.Row::productType))
                .toList();
        return new PurchaseReport(from, to, rows, new PurchaseReport.Totals(
                rows.stream().mapToLong(PurchaseReport.Row::purchases).sum(),
                total(rows, PurchaseReport.Row::orderedKg, Weight.zero()),
                total(rows, PurchaseReport.Row::receivedKg, Weight.zero()),
                total(rows, PurchaseReport.Row::acceptedKg, Weight.zero()),
                total(rows, PurchaseReport.Row::rejectedKg, Weight.zero()),
                total(rows, PurchaseReport.Row::returnedKg, Weight.zero()),
                total(rows, PurchaseReport.Row::orderedValue, Money.zero()),
                total(rows, PurchaseReport.Row::payableValue, Money.zero())));
    }

    private static Map<String, BigDecimal> emptyBuckets() {
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        for (AgeingBucket b : AgeingBucket.values()) {
            buckets.put(b.name(), Money.zero());
        }
        return buckets;
    }

    private static BigDecimal sum(Map<String, BigDecimal> buckets) {
        return buckets.values().stream().reduce(Money.zero(), BigDecimal::add);
    }

    private static <T> BigDecimal total(List<T> rows, java.util.function.Function<T, BigDecimal> field,
                                        BigDecimal zero) {
        return rows.stream().map(field).reduce(zero, BigDecimal::add);
    }
}

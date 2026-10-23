package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.reporting.api.dto.InventoryMovementReportLine;
import com.nexora.reporting.api.dto.OutsourcingReport;
import com.nexora.reporting.api.dto.ProductionReport;
import com.nexora.reporting.infrastructure.OperationsReportRepository;
import com.nexora.reporting.infrastructure.OperationsReportRepository.BatchRow;
import com.nexora.reporting.infrastructure.OperationsReportRepository.JobRow;
import com.nexora.reporting.infrastructure.OperationsReportRepository.LedgerSums;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.FieldValidationException;

// Stock, production and outsourcing reports (FR-RPT-09..11), all derived from the ledger and the batch/job records.
@Service
public class OperationsReportQuery {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OperationsReportRepository operations;
    private final AllocationQueryService allocations;
    private final PartyService parties;
    private final Clock clock;

    public OperationsReportQuery(OperationsReportRepository operations, AllocationQueryService allocations,
                                 PartyService parties, Clock clock) {
        this.operations = operations;
        this.allocations = allocations;
        this.parties = parties;
        this.clock = clock;
    }

    // E-RPT-08: every internal location holding the product (or the one asked for). A location outside the business
    // (SUPPLIER, CUSTOMER, WASTAGE, RETURNED) has no balance and is 400.
    @Transactional(readOnly = true)
    public List<InventoryMovementReportLine> inventoryMovements(LocalDate from, LocalDate to, ProductType product,
                                                                StockLocation location) {
        ListParameters.requireDateRange(from, to);
        if (location != null && !location.isInternal()) {
            throw new FieldValidationException("location", "Only RAW_STOCK, INTERNAL_WIP, EXTERNAL_WIP and"
                    + " FINISHED_STOCK hold stock.");
        }
        Map<StockLocation, List<LedgerSums>> byLocation = operations.ledgerSums(product, from, to).stream()
                .collect(Collectors.groupingBy(LedgerSums::location));
        return Arrays.stream(StockLocation.values())
                .filter(l -> location == null ? l.isInternal() && l.holds(product) : l == location)
                .map(l -> {
                    List<LedgerSums> sums = byLocation.getOrDefault(l, List.of());
                    BigDecimal opening = Weight.round(sums.stream().map(LedgerSums::beforeKg)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    List<InventoryMovementReportLine.TypeLine> types = sums.stream()
                            .filter(s -> s.inKg().signum() != 0 || s.outKg().signum() != 0)
                            .sorted(Comparator.comparing(LedgerSums::movementType))
                            .map(s -> new InventoryMovementReportLine.TypeLine(s.movementType(), Weight.round(s.inKg()),
                                    Weight.round(s.outKg())))
                            .toList();
                    BigDecimal closing = types.stream().map(t -> t.inKg().subtract(t.outKg()))
                            .reduce(opening, BigDecimal::add);
                    return new InventoryMovementReportLine(l, opening, types, closing);
                })
                .toList();
    }

    // E-RPT-10: batches completed within the period; IN_PROGRESS batches have no output yet and are not counted.
    @Transactional(readOnly = true)
    public ProductionReport production(LocalDate from, LocalDate to, ProductType product) {
        ListParameters.requireDateRange(from, to);
        List<BatchRow> batches = operations.completedBatches(from, to, product, clock.getZone().getId());
        List<ProductionReport.Row> rows = batches.stream()
                .map(b -> new ProductionReport.Row(b.batchNumber(), b.productType(), b.completedOn(),
                        Weight.round(b.inputKg()), Weight.round(b.outputKg()), Weight.round(b.wastageKg()),
                        Weight.round(b.discrepancyKg()), percent(b.wastageKg(), b.inputKg())))
                .toList();
        BigDecimal input = weights(rows, ProductionReport.Row::inputKg);
        BigDecimal wastage = weights(rows, ProductionReport.Row::wastageKg);
        return new ProductionReport(from, to, rows, new ProductionReport.Totals(rows.size(), input,
                weights(rows, ProductionReport.Row::outputKg), wastage,
                weights(rows, ProductionReport.Row::discrepancyKg), percent(wastage, input)));
    }

    // E-RPT-11: jobs issued within the period, per manufacturer (by name); 404 unknown, 422 not a manufacturer.
    @Transactional(readOnly = true)
    public OutsourcingReport outsourcing(LocalDate from, LocalDate to, UUID manufacturerId) {
        ListParameters.requireDateRange(from, to);
        if (manufacturerId != null) {
            parties.requireRole(manufacturerId, PartyRole.MANUFACTURER);
        }
        List<JobRow> jobs = operations.issuedJobs(from, to, manufacturerId);
        Map<UUID, BigDecimal> paid = allocations.allocatedByJob(jobs.stream().map(JobRow::id).toList());
        Map<UUID, List<OutsourcingReport.Job>> byManufacturer = new LinkedHashMap<>();
        for (JobRow j : jobs) {
            BigDecimal payable = Money.round(j.payable());
            BigDecimal allocated = Money.round(paid.getOrDefault(j.id(), BigDecimal.ZERO));
            byManufacturer.computeIfAbsent(j.manufacturerId(), k -> new ArrayList<>()).add(new OutsourcingReport.Job(
                    j.id(), j.jobNumber(), j.productType(), j.issueDate(), j.status(), Weight.round(j.issuedKg()),
                    Weight.round(j.receivedKg()), Weight.round(j.wastageKg()),
                    Weight.round(j.issuedKg().subtract(j.receivedKg()).subtract(j.wastageKg())), payable, allocated,
                    payable.subtract(allocated)));
        }
        Map<UUID, PartyRef> refs = parties.getRefs(byManufacturer.keySet());
        List<OutsourcingReport.ManufacturerGroup> groups = byManufacturer.entrySet().stream()
                .map(e -> new OutsourcingReport.ManufacturerGroup(refs.get(e.getKey()), e.getValue(),
                        totals(e.getValue())))
                .sorted(Comparator.comparing(g -> g.manufacturer().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new OutsourcingReport(from, to, groups,
                totals(groups.stream().flatMap(g -> g.jobs().stream()).toList()));
    }

    // wastage / input x 100, 2 decimals HALF_UP (as ProductionBatch.wastagePercent); 0.00 when nothing went in.
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? Money.zero() : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal weights(List<ProductionReport.Row> rows,
                                      java.util.function.Function<ProductionReport.Row, BigDecimal> field) {
        return rows.stream().map(field).reduce(Weight.zero(), BigDecimal::add);
    }

    private static OutsourcingReport.Totals totals(List<OutsourcingReport.Job> jobs) {
        return new OutsourcingReport.Totals(
                jobs.stream().map(OutsourcingReport.Job::issuedKg).reduce(Weight.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::receivedKg).reduce(Weight.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::wastageKg).reduce(Weight.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::outsideKg).reduce(Weight.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::payable).reduce(Money.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::paid).reduce(Money.zero(), BigDecimal::add),
                jobs.stream().map(OutsourcingReport.Job::pending).reduce(Money.zero(), BigDecimal::add));
    }
}

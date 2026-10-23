package com.nexora.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
import com.nexora.reporting.api.dto.ProfitAndLossReportLine;
import com.nexora.reporting.api.dto.ProfitabilityReportLine;
import com.nexora.reporting.infrastructure.ProfitReportRepository;
import com.nexora.reporting.infrastructure.ProfitReportRepository.AvgOutsourceRateRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.AvgRateRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.DeliveredItemRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.ExpenseRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.InterestRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.JobOrderItemLink;
import com.nexora.reporting.infrastructure.ProfitReportRepository.OutsourcingJobCostRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.PurchaseCostRow;
import com.nexora.reporting.infrastructure.ProfitReportRepository.WorkerWageRow;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;

@Service
public class ProfitReportQuery {

    private final ProfitReportRepository repo;
    private final PartyService parties;

    public ProfitReportQuery(ProfitReportRepository repo, PartyService parties) {
        this.repo = repo;
        this.parties = parties;
    }

    // E-RPT-15: Profit & Loss statement over a date range grouped by MONTH or YEAR.
    @Transactional(readOnly = true)
    public List<ProfitAndLossReportLine> profitAndLoss(LocalDate from, LocalDate to, PeriodGrouping groupBy) {
        ListParameters.requireDateRange(from, to);
        PeriodGrouping grouping = groupBy != null ? groupBy : PeriodGrouping.MONTH;

        List<DeliveredItemRow> delivered = repo.deliveredItems(from, to);
        List<ExpenseRow> expenses = repo.businessExpenses(from, to);
        List<InterestRow> interests = repo.interestCosts(from, to);
        List<PurchaseCostRow> purchases = repo.purchases(from, to);
        List<WorkerWageRow> wages = repo.workerWages(from, to);
        List<OutsourcingJobCostRow> jobs = repo.outsourcingJobs(from, to);

        Map<String, PeriodSums> sums = initPeriodMap(from, to, grouping);

        for (DeliveredItemRow d : delivered) {
            String key = formatKey(d.deliveredOn(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).revenue =
                    sums.get(key).revenue.add(d.amount());
        }
        for (ExpenseRow e : expenses) {
            String key = formatKey(e.expenseDate(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).businessExpenses =
                    sums.get(key).businessExpenses.add(e.amount());
        }
        for (InterestRow i : interests) {
            String key = formatKey(i.transactionDate(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).interestCost =
                    sums.get(key).interestCost.add(i.amount());
        }
        for (PurchaseCostRow p : purchases) {
            String key = formatKey(p.purchaseDate(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).productionCost =
                    sums.get(key).productionCost.add(p.cost());
        }
        for (WorkerWageRow w : wages) {
            String key = formatKey(w.workDate(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).productionCost =
                    sums.get(key).productionCost.add(w.amount());
        }
        for (OutsourcingJobCostRow j : jobs) {
            String key = formatKey(j.jobDate(), grouping);
            sums.computeIfAbsent(key, k -> new PeriodSums()).productionCost =
                    sums.get(key).productionCost.add(j.cost());
        }

        return sums.entrySet().stream().map(e -> {
            String period = e.getKey();
            PeriodSums s = e.getValue();
            BigDecimal rev = Money.round(s.revenue);
            BigDecimal exp = Money.round(s.businessExpenses);
            BigDecimal intCost = Money.round(s.interestCost);
            BigDecimal prodCost = Money.round(s.productionCost);
            BigDecimal profit = Money.round(rev.subtract(exp).subtract(intCost).subtract(prodCost));
            return new ProfitAndLossReportLine(period, rev, exp, intCost, prodCost, profit);
        }).toList();
    }

    // E-RPT-16: Profitability report broken down by PRODUCT, CUSTOMER, or MANUFACTURER.
    @Transactional(readOnly = true)
    public List<ProfitabilityReportLine> profitability(LocalDate from, LocalDate to, ProfitabilityDimension dimension) {
        ListParameters.requireDateRange(from, to);
        ProfitabilityDimension dim = dimension != null ? dimension : ProfitabilityDimension.PRODUCT;

        List<DeliveredItemRow> delivered = repo.deliveredItems(from, to);
        List<PurchaseCostRow> purchases = repo.purchases(from, to);
        List<OutsourcingJobCostRow> jobs = repo.outsourcingJobs(from, to);

        Map<ProductType, BigDecimal> avgPurchases = repo.avgPurchaseRates().stream()
                .filter(r -> r.avgRate() != null)
                .collect(Collectors.toMap(AvgRateRow::productType, AvgRateRow::avgRate));
        Map<ProductType, BigDecimal> avgOutsources = repo.avgOutsourcingRates().stream()
                .filter(r -> r.avgRate() != null)
                .collect(Collectors.toMap(AvgOutsourceRateRow::productType, AvgOutsourceRateRow::avgRate));
        List<JobOrderItemLink> orderLinks = repo.outsourcingOrderLinks();

        BigDecimal rawSilkRate = avgPurchases.getOrDefault(ProductType.RAW_SILK, BigDecimal.ZERO);
        BigDecimal vudaWarpRate = avgPurchases.getOrDefault(ProductType.VUDA_WARP, BigDecimal.ZERO);
        BigDecimal silkWarpJobRate = avgOutsources.getOrDefault(ProductType.SILK_WARP, BigDecimal.ZERO);
        BigDecimal koraWarpJobRate = avgOutsources.getOrDefault(ProductType.KORA_WARP, BigDecimal.ZERO);

        Map<ProductType, BigDecimal> unitCosts = Map.of(
                ProductType.RAW_SILK, rawSilkRate,
                ProductType.VUDA_WARP, vudaWarpRate,
                ProductType.SILK_WARP, rawSilkRate.add(silkWarpJobRate),
                ProductType.KORA_WARP, rawSilkRate.add(koraWarpJobRate)
        );

        return switch (dim) {
            case PRODUCT -> {
                Map<ProductType, BigDecimal[]> byProduct = new LinkedHashMap<>();
                for (ProductType pt : ProductType.values()) {
                    byProduct.put(pt, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                }
                for (DeliveredItemRow item : delivered) {
                    BigDecimal[] vals = byProduct.get(item.productType());
                    if (vals != null) {
                        vals[0] = vals[0].add(item.amount());
                        BigDecimal unitCost = unitCosts.getOrDefault(item.productType(), BigDecimal.ZERO);
                        vals[1] = vals[1].add(item.requiredWeightKg().multiply(unitCost));
                    }
                }
                for (PurchaseCostRow p : purchases) {
                    BigDecimal[] vals = byProduct.get(p.productType());
                    if (vals != null && vals[0].signum() == 0) {
                        vals[1] = vals[1].add(p.cost());
                    }
                }
                for (OutsourcingJobCostRow j : jobs) {
                    BigDecimal[] vals = byProduct.get(j.productType());
                    if (vals != null && vals[0].signum() == 0) {
                        vals[1] = vals[1].add(j.cost());
                    }
                }
                yield byProduct.entrySet().stream()
                        .filter(e -> e.getValue()[0].signum() > 0 || e.getValue()[1].signum() > 0)
                        .map(e -> {
                            String key = e.getKey().name();
                            BigDecimal rev = Money.round(e.getValue()[0]);
                            BigDecimal cost = Money.round(e.getValue()[1]);
                            BigDecimal margin = Money.round(rev.subtract(cost));
                            BigDecimal pct = rev.signum() > 0
                                    ? margin.multiply(BigDecimal.valueOf(100)).divide(rev, 2, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                            return new ProfitabilityReportLine(key, null, rev, cost, margin, pct);
                        }).sorted(Comparator.comparing(ProfitabilityReportLine::key)).toList();
            }
            case CUSTOMER -> {
                Map<UUID, BigDecimal[]> byCustomer = new LinkedHashMap<>();
                for (DeliveredItemRow item : delivered) {
                    BigDecimal[] vals = byCustomer.computeIfAbsent(item.customerId(),
                            k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                    vals[0] = vals[0].add(item.amount());
                    BigDecimal unitCost = unitCosts.getOrDefault(item.productType(), BigDecimal.ZERO);
                    vals[1] = vals[1].add(item.requiredWeightKg().multiply(unitCost));
                }
                if (byCustomer.isEmpty()) {
                    yield List.of();
                }
                Map<UUID, PartyRef> refs = parties.getRefs(byCustomer.keySet());
                yield byCustomer.entrySet().stream().map(e -> {
                    PartyRef cust = refs.get(e.getKey());
                    String name = cust != null ? cust.name() : e.getKey().toString();
                    BigDecimal rev = Money.round(e.getValue()[0]);
                    BigDecimal cost = Money.round(e.getValue()[1]);
                    BigDecimal margin = Money.round(rev.subtract(cost));
                    BigDecimal pct = rev.signum() > 0
                            ? margin.multiply(BigDecimal.valueOf(100)).divide(rev, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    return new ProfitabilityReportLine(name, e.getKey(), rev, cost, margin, pct);
                }).sorted(Comparator.comparing(ProfitabilityReportLine::key, String.CASE_INSENSITIVE_ORDER)).toList();
            }
            case MANUFACTURER -> {
                Map<UUID, BigDecimal[]> byManufacturer = new LinkedHashMap<>();
                for (OutsourcingJobCostRow j : jobs) {
                    BigDecimal[] vals = byManufacturer.computeIfAbsent(j.manufacturerId(),
                            k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                    vals[1] = vals[1].add(j.cost());
                }
                Map<UUID, UUID> itemToMfg = orderLinks.stream()
                        .collect(Collectors.toMap(JobOrderItemLink::orderItemId,
                                JobOrderItemLink::manufacturerId, (a, b) -> a));
                for (DeliveredItemRow item : delivered) {
                    UUID mfgId = itemToMfg.get(item.itemId());
                    if (mfgId != null) {
                        BigDecimal[] vals = byManufacturer.computeIfAbsent(mfgId,
                                k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                        vals[0] = vals[0].add(item.amount());
                    }
                }
                if (byManufacturer.isEmpty()) {
                    yield List.of();
                }
                Map<UUID, PartyRef> refs = parties.getRefs(byManufacturer.keySet());
                yield byManufacturer.entrySet().stream().map(e -> {
                    PartyRef mfg = refs.get(e.getKey());
                    String name = mfg != null ? mfg.name() : e.getKey().toString();
                    BigDecimal rev = Money.round(e.getValue()[0]);
                    BigDecimal cost = Money.round(e.getValue()[1]);
                    BigDecimal margin = Money.round(rev.subtract(cost));
                    BigDecimal pct = rev.signum() > 0
                            ? margin.multiply(BigDecimal.valueOf(100)).divide(rev, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    return new ProfitabilityReportLine(name, e.getKey(), rev, cost, margin, pct);
                }).sorted(Comparator.comparing(ProfitabilityReportLine::key, String.CASE_INSENSITIVE_ORDER)).toList();
            }
        };
    }

    private static Map<String, PeriodSums> initPeriodMap(LocalDate from, LocalDate to, PeriodGrouping grouping) {
        Map<String, PeriodSums> map = new LinkedHashMap<>();
        if (grouping == PeriodGrouping.MONTH) {
            YearMonth cur = YearMonth.from(from);
            YearMonth end = YearMonth.from(to);
            while (!cur.isAfter(end)) {
                map.put(cur.toString(), new PeriodSums());
                cur = cur.plusMonths(1);
            }
        } else {
            int cur = from.getYear();
            int end = to.getYear();
            while (cur <= end) {
                map.put(String.valueOf(cur), new PeriodSums());
                cur++;
            }
        }
        return map;
    }

    private static String formatKey(LocalDate date, PeriodGrouping grouping) {
        return grouping == PeriodGrouping.MONTH
                ? YearMonth.from(date).toString()
                : String.valueOf(date.getYear());
    }

    private static class PeriodSums {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal businessExpenses = BigDecimal.ZERO;
        BigDecimal interestCost = BigDecimal.ZERO;
        BigDecimal productionCost = BigDecimal.ZERO;
    }
}

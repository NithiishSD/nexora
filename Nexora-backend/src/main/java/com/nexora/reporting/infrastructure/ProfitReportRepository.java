package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.inventory.domain.ProductType;

@Repository
public class ProfitReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfitReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeliveredItemRow> deliveredItems(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select o.id as order_id, o.customer_id, i.id as item_id, i.product_type,
                               o.delivered_on, i.required_weight_kg, i.amount
                        from order_items i
                        join customer_orders o on o.id = i.order_id
                        where o.status <> 'CANCELLED'
                          and o.delivered_on between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new DeliveredItemRow(
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getObject("item_id", UUID.class),
                        ProductType.valueOf(rs.getString("product_type")),
                        rs.getObject("delivered_on", LocalDate.class),
                        rs.getBigDecimal("required_weight_kg"),
                        rs.getBigDecimal("amount")));
    }

    public List<ExpenseRow> businessExpenses(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select expense_date, amount
                        from expenses
                        where status = 'RECORDED'
                          and expense_kind = 'BUSINESS'
                          and expense_date between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new ExpenseRow(
                        rs.getObject("expense_date", LocalDate.class),
                        rs.getBigDecimal("amount")));
    }

    public List<InterestRow> interestCosts(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select t.transaction_date, t.amount
                        from financial_transactions t
                        where t.transaction_type = 'LOAN_INTEREST_PAYMENT'
                          and t.reverses_transaction_id is null
                          and not exists (
                              select 1 from financial_transactions r
                              where r.reverses_transaction_id = t.id
                          )
                          and t.transaction_date between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new InterestRow(
                        rs.getObject("transaction_date", LocalDate.class),
                        rs.getBigDecimal("amount")));
    }

    public List<PurchaseCostRow> purchases(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select p.purchase_date, p.product_type,
                               case when pp.payable > 0 then pp.payable
                                    else round(p.ordered_weight_kg * p.rate_per_kg, 2) end as cost
                        from purchases p
                        join purchase_payables pp on pp.purchase_id = p.id
                        where p.status <> 'CANCELLED'
                          and p.purchase_date between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new PurchaseCostRow(
                        rs.getObject("purchase_date", LocalDate.class),
                        ProductType.valueOf(rs.getString("product_type")),
                        rs.getBigDecimal("cost")));
    }

    public List<WorkerWageRow> workerWages(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select work_date, amount, production_batch_id
                        from work_records
                        where status = 'RECORDED'
                          and work_date between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new WorkerWageRow(
                        rs.getObject("work_date", LocalDate.class),
                        rs.getBigDecimal("amount"),
                        rs.getObject("production_batch_id", UUID.class)));
    }

    public List<OutsourcingJobCostRow> outsourcingJobs(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select id as job_id, manufacturer_id, product_type,
                               coalesce(received_date, issue_date) as job_date,
                               case when received_weight_kg > 0 then round(received_weight_kg * rate_per_kg, 2)
                                    else round(issued_weight_kg * rate_per_kg, 2) end as cost
                        from outsourcing_jobs
                        where status <> 'CANCELLED'
                          and coalesce(received_date, issue_date) between :from and :to""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new OutsourcingJobCostRow(
                        rs.getObject("job_id", UUID.class),
                        rs.getObject("manufacturer_id", UUID.class),
                        ProductType.valueOf(rs.getString("product_type")),
                        rs.getObject("job_date", LocalDate.class),
                        rs.getBigDecimal("cost")));
    }

    public List<AvgRateRow> avgPurchaseRates() {
        return jdbc.query("""
                        select product_type,
                               sum(ordered_weight_kg * rate_per_kg) / nullif(sum(ordered_weight_kg), 0) as avg_rate
                        from purchases
                        where status <> 'CANCELLED'
                        group by product_type""",
                (rs, n) -> new AvgRateRow(
                        ProductType.valueOf(rs.getString("product_type")),
                        rs.getBigDecimal("avg_rate")));
    }

    public List<AvgOutsourceRateRow> avgOutsourcingRates() {
        return jdbc.query("""
                        select product_type,
                               sum(case when received_weight_kg > 0 then received_weight_kg * rate_per_kg else issued_weight_kg * rate_per_kg end) /
                               nullif(sum(case when received_weight_kg > 0 then received_weight_kg else issued_weight_kg end), 0) as avg_rate
                        from outsourcing_jobs
                        where status <> 'CANCELLED'
                        group by product_type""",
                (rs, n) -> new AvgOutsourceRateRow(
                        ProductType.valueOf(rs.getString("product_type")),
                        rs.getBigDecimal("avg_rate")));
    }

    public List<JobOrderItemLink> outsourcingOrderLinks() {
        return jdbc.query("""
                        select ooi.order_item_id, ooi.outsourcing_job_id, j.manufacturer_id
                        from outsourcing_order_items ooi
                        join outsourcing_jobs j on j.id = ooi.outsourcing_job_id
                        where j.status <> 'CANCELLED'""",
                (rs, n) -> new JobOrderItemLink(
                        rs.getObject("order_item_id", UUID.class),
                        rs.getObject("outsourcing_job_id", UUID.class),
                        rs.getObject("manufacturer_id", UUID.class)));
    }

    public record DeliveredItemRow(UUID orderId, UUID customerId, UUID itemId, ProductType productType,
                                  LocalDate deliveredOn, BigDecimal requiredWeightKg, BigDecimal amount) {}

    public record ExpenseRow(LocalDate expenseDate, BigDecimal amount) {}

    public record InterestRow(LocalDate transactionDate, BigDecimal amount) {}

    public record PurchaseCostRow(LocalDate purchaseDate, ProductType productType, BigDecimal cost) {}

    public record WorkerWageRow(LocalDate workDate, BigDecimal amount, UUID productionBatchId) {}

    public record OutsourcingJobCostRow(UUID jobId, UUID manufacturerId, ProductType productType,
                                       LocalDate jobDate, BigDecimal cost) {}

    public record AvgRateRow(ProductType productType, BigDecimal avgRate) {}

    public record AvgOutsourceRateRow(ProductType productType, BigDecimal avgRate) {}

    public record JobOrderItemLink(UUID orderItemId, UUID jobId, UUID manufacturerId) {}
}

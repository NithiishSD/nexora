package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// Read-only aggregates over the movement ledger, batches and jobs (FR-RPT-09..11), one query each.
@Repository
public class OperationsReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OperationsReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Per location and movement type for one product, up to `to`: the net kg before `from` (for the opening) and the
    // kg in and out within the period. A movement counts in where (to_product_type, to_location) is the product and
    // out where (product_type, from_location) is: the stock formula of DOMAIN_RULES section 6. An opening balance has
    // no from_location: it only arrives.
    public List<LedgerSums> ledgerSums(ProductType product, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select loc, movement_type,
                               sum(case when movement_date < :from then kg_in - kg_out else 0 end) as before_kg,
                               sum(case when movement_date >= :from then kg_in else 0 end) as in_kg,
                               sum(case when movement_date >= :from then kg_out else 0 end) as out_kg
                        from (select to_location as loc, movement_type, movement_date, quantity_kg as kg_in, 0 as kg_out
                              from material_movements where to_product_type = :product
                              union all
                              select from_location, movement_type, movement_date, 0, quantity_kg
                              from material_movements
                              where product_type = :product and from_location is not null) m
                        where movement_date <= :to
                        group by loc, movement_type""",
                new MapSqlParameterSource("product", product.name()).addValue("from", from).addValue("to", to),
                (rs, n) -> new LedgerSums(StockLocation.valueOf(rs.getString("loc")),
                        MovementType.valueOf(rs.getString("movement_type")), rs.getBigDecimal("before_kg"),
                        rs.getBigDecimal("in_kg"), rs.getBigDecimal("out_kg")));
    }

    // Batches completed within the period (completion day in the business time zone), oldest completion first.
    public List<BatchRow> completedBatches(LocalDate from, LocalDate to, ProductType product, String zone) {
        return jdbc.query("""
                        select batch_number, product_type, (completed_at at time zone :zone)::date as completed_on,
                               input_weight_kg, output_weight_kg, wastage_weight_kg, discrepancy_weight_kg
                        from production_batches
                        where status = 'COMPLETED' and (completed_at at time zone :zone)::date between :from and :to
                          and (cast(:product as varchar) is null or product_type = :product)
                        order by completed_at, batch_number""",
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("zone", zone)
                        .addValue("product", product == null ? null : product.name()),
                (rs, n) -> new BatchRow(rs.getString("batch_number"), ProductType.valueOf(rs.getString("product_type")),
                        rs.getObject("completed_on", LocalDate.class), rs.getBigDecimal("input_weight_kg"),
                        rs.getBigDecimal("output_weight_kg"), rs.getBigDecimal("wastage_weight_kg"),
                        rs.getBigDecimal("discrepancy_weight_kg")));
    }

    // Jobs issued within the period (never-issued and cancelled jobs moved nothing and are left out).
    public List<JobRow> issuedJobs(LocalDate from, LocalDate to, UUID manufacturerId) {
        return jdbc.query("""
                        select id, job_number, manufacturer_id, product_type, issue_date, status, issued_weight_kg,
                               received_weight_kg, wastage_weight_kg, round(received_weight_kg * rate_per_kg, 2) as payable
                        from outsourcing_jobs
                        where issue_date between :from and :to and status <> 'CANCELLED'
                          and (cast(:manufacturer as uuid) is null or manufacturer_id = :manufacturer)
                        order by issue_date, job_number""",
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("manufacturer", manufacturerId),
                (rs, n) -> new JobRow(rs.getObject("id", UUID.class), rs.getString("job_number"),
                        rs.getObject("manufacturer_id", UUID.class), ProductType.valueOf(rs.getString("product_type")),
                        rs.getObject("issue_date", LocalDate.class), rs.getString("status"),
                        rs.getBigDecimal("issued_weight_kg"), rs.getBigDecimal("received_weight_kg"),
                        rs.getBigDecimal("wastage_weight_kg"), rs.getBigDecimal("payable")));
    }

    public record LedgerSums(StockLocation location, MovementType movementType, BigDecimal beforeKg, BigDecimal inKg,
                             BigDecimal outKg) {
    }

    public record BatchRow(String batchNumber, ProductType productType, LocalDate completedOn, BigDecimal inputKg,
                           BigDecimal outputKg, BigDecimal wastageKg, BigDecimal discrepancyKg) {
    }

    public record JobRow(UUID id, String jobNumber, UUID manufacturerId, ProductType productType, LocalDate issueDate,
                         String status, BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg,
                         BigDecimal payable) {
    }
}

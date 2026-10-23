package com.nexora.inventory.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.inventory.domain.ProductType;

// Read-only SQL over the outsourcing module's open jobs (FR-INV-14): outsourcing calls the inventory service, so
// inventory reads the jobs instead of calling outsourcing back (no cycle). Never writes that table.
@Repository
public class ExternalWipRepository {

    public record OpenJob(UUID id, String jobNumber, UUID manufacturerId, ProductType productType, String status,
                          LocalDate issueDate, BigDecimal issuedKg, BigDecimal receivedKg, BigDecimal wastageKg) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ExternalWipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Jobs with material outside (CREATED and CANCELLED jobs hold nothing, COMPLETED ones nothing any more).
    public List<OpenJob> openJobs(UUID manufacturerId) {
        return jdbc.query("""
                        select id, job_number, manufacturer_id, product_type, status, issue_date, issued_weight_kg,
                               received_weight_kg, wastage_weight_kg
                        from outsourcing_jobs
                        where status in ('MATERIAL_ISSUED', 'PARTIALLY_RECEIVED')
                          and (cast(:manufacturer as uuid) is null or manufacturer_id = :manufacturer)
                        order by issue_date, job_number""",
                new MapSqlParameterSource("manufacturer", manufacturerId),
                (rs, n) -> new OpenJob(rs.getObject("id", UUID.class), rs.getString("job_number"),
                        rs.getObject("manufacturer_id", UUID.class), ProductType.valueOf(rs.getString("product_type")),
                        rs.getString("status"), rs.getObject("issue_date", LocalDate.class),
                        rs.getBigDecimal("issued_weight_kg"), rs.getBigDecimal("received_weight_kg"),
                        rs.getBigDecimal("wastage_weight_kg")));
    }
}

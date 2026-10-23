package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Read-only counts for the dashboard in one statement (NFR-PERF-07: no per-row queries). Only plain counts and one
// column sum live here; every derived money figure comes from the module that owns its formula.
@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts(LocalDate today) {
        return jdbc.queryForObject("""
                        select
                          (select count(*) from customer_orders where status = 'PLACED') as placed,
                          (select count(*) from customer_orders where status = 'CONFIRMED') as confirmed,
                          (select count(*) from customer_orders where status = 'IN_PROGRESS') as in_progress,
                          (select count(*) from customer_orders where status = 'READY') as ready,
                          (select count(*) from customer_orders
                           where status in ('PLACED', 'CONFIRMED', 'IN_PROGRESS', 'READY')
                             and required_date < :today) as late,
                          (select count(*) from production_batches where status = 'PLANNED') as batches_planned,
                          (select count(*) from production_batches where status = 'IN_PROGRESS') as batches_in_progress,
                          (select count(*) from outsourcing_jobs where status not in ('COMPLETED', 'CANCELLED'))
                              as open_jobs,
                          (select coalesce(sum(monthly_amount), 0) from chits where status = 'ACTIVE') as chit_monthly""",
                new MapSqlParameterSource("today", today),
                (rs, n) -> new Counts(rs.getLong("placed"), rs.getLong("confirmed"), rs.getLong("in_progress"),
                        rs.getLong("ready"), rs.getLong("late"), rs.getLong("batches_planned"),
                        rs.getLong("batches_in_progress"), rs.getLong("open_jobs"), rs.getBigDecimal("chit_monthly")));
    }

    public record Counts(long placed, long confirmed, long inProgress, long ready, long late, long batchesPlanned,
                         long batchesInProgress, long openJobs, BigDecimal chitMonthly) {
    }
}

package com.nexora.orders.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.orders.api.dto.LinkedWorkResponse;

// Read-only SQL over the production and outsourcing links to this order's items (FR-PROD-07, FR-OUT-08): those
// modules call the orders service, so orders reads the links instead of calling them back (no cycle).
// Never writes those tables.
@Repository
public class OrderWorkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OrderWorkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LinkedWorkResponse> productionBatches(UUID orderId) {
        return jdbc.query("""
                        select b.id, b.batch_number, b.status, sum(l.allocated_weight_kg) as allocated
                        from production_order_items l
                        join production_batches b on b.id = l.production_batch_id
                        join order_items i on i.id = l.order_item_id
                        where i.order_id = :order
                        group by b.id, b.batch_number, b.status
                        order by b.batch_number""",
                new MapSqlParameterSource("order", orderId),
                (rs, n) -> new LinkedWorkResponse(rs.getObject("id", UUID.class), rs.getString("batch_number"), null,
                        rs.getString("status"), rs.getBigDecimal("allocated")));
    }

    public List<LinkedWorkResponse> outsourcingJobs(UUID orderId) {
        return jdbc.query("""
                        select j.id, j.job_number, j.status, sum(l.allocated_weight_kg) as allocated
                        from outsourcing_order_items l
                        join outsourcing_jobs j on j.id = l.outsourcing_job_id
                        join order_items i on i.id = l.order_item_id
                        where i.order_id = :order
                        group by j.id, j.job_number, j.status
                        order by j.job_number""",
                new MapSqlParameterSource("order", orderId),
                (rs, n) -> new LinkedWorkResponse(rs.getObject("id", UUID.class), null, rs.getString("job_number"),
                        rs.getString("status"), rs.getBigDecimal("allocated")));
    }
}

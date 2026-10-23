package com.nexora.production.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.production.api.dto.BatchResponse;

// Read-only SQL over the workforce module's records linked to a batch (FR-WORK-08): workforce calls the production
// service, so production reads the records instead of calling workforce back (no cycle). Never writes that table.
@Repository
public class BatchWorkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public BatchWorkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Recorded (not reversed) work on one batch, oldest first.
    public List<BatchResponse.WorkRecord> workRecords(UUID batchId) {
        return jdbc.query("""
                        select w.id, p.name, w.work_type, w.amount
                        from work_records w
                        join parties p on p.id = w.worker_id
                        where w.production_batch_id = :batch and w.status = 'RECORDED'
                        order by w.work_date, w.created_at""",
                new MapSqlParameterSource("batch", batchId),
                (rs, n) -> new BatchResponse.WorkRecord(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("work_type"), rs.getBigDecimal("amount")));
    }
}

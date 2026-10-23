package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionType;
import com.nexora.workforce.domain.WorkType;

// Read-only aggregates over the money ledger and recorded work (FR-RPT-12, FR-RPT-13), one query each.
@Repository
public class FinanceReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FinanceReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every account (or one), and per transaction type and direction up to `to`: the net amount before `from` and the
    // amount within the period. Balance = Σ IN - Σ OUT (DOMAIN_RULES section 6). Accounts without rows still appear.
    public List<LedgerSums> ledgerSums(UUID accountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select a.id, a.account_name, a.account_type, t.transaction_type, t.direction,
                               coalesce(sum(case when t.transaction_date < :from then
                                   case when t.direction = 'IN' then t.amount else -t.amount end end), 0) as before_net,
                               coalesce(sum(case when t.transaction_date >= :from then t.amount end), 0) as in_period
                        from financial_accounts a
                        left join financial_transactions t on t.financial_account_id = a.id and t.transaction_date <= :to
                        where cast(:account as uuid) is null or a.id = :account
                        group by a.id, a.account_name, a.account_type, t.transaction_type, t.direction
                        order by a.account_name""",
                new MapSqlParameterSource("account", accountId).addValue("from", from).addValue("to", to),
                (rs, n) -> new LedgerSums(rs.getObject("id", UUID.class), rs.getString("account_name"),
                        AccountType.valueOf(rs.getString("account_type")),
                        rs.getString("transaction_type") == null ? null
                                : TransactionType.valueOf(rs.getString("transaction_type")),
                        rs.getString("direction") == null ? null : Direction.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("before_net"), rs.getBigDecimal("in_period")));
    }

    // Recorded (not reversed) work dated in the period, optionally one worker's.
    public List<WorkRow> work(LocalDate from, LocalDate to, UUID workerId) {
        return jdbc.query("""
                        select id, worker_id, work_type, amount from work_records
                        where status = 'RECORDED' and work_date between :from and :to
                          and (cast(:worker as uuid) is null or worker_id = :worker)""",
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("worker", workerId),
                (rs, n) -> new WorkRow(rs.getObject("id", UUID.class), rs.getObject("worker_id", UUID.class),
                        WorkType.valueOf(rs.getString("work_type")), rs.getBigDecimal("amount")));
    }

    public List<WorkerEfficiencyAggregate> workerEfficiency(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        select worker_id,
                               coalesce(sum(hours), 0) as total_hours,
                               coalesce(sum(quantity_kg), 0) as total_kg
                        from work_records
                        where status = 'RECORDED' and work_date between :from and :to
                        group by worker_id""",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new WorkerEfficiencyAggregate(
                        rs.getObject("worker_id", UUID.class),
                        rs.getBigDecimal("total_hours"),
                        rs.getBigDecimal("total_kg")));
    }

    // type and direction are null for an account with no rows up to `to`.
    public record LedgerSums(UUID accountId, String accountName, AccountType accountType, TransactionType type,
                             Direction direction, BigDecimal beforeNet, BigDecimal inPeriod) {
    }

    public record WorkRow(UUID id, UUID workerId, WorkType workType, BigDecimal amount) {
    }

    public record WorkerEfficiencyAggregate(UUID workerId, BigDecimal hours, BigDecimal kg) {
    }
}

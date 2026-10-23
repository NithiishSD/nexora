package com.nexora.finance.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.finance.domain.AllocationTargetType;
import com.nexora.finance.domain.CustomerBalance;
import com.nexora.finance.domain.Obligation;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.orders.domain.OrderStatus;

// Read-only SQL over the obligations other modules own (order items, purchases, opening obligations) and the
// allocations that pay them. Outstanding is always derived here: amount - allocations of RECORDED payments
// (DOMAIN_RULES section 6), in one aggregate query, never per row. Nothing here writes another module's tables.
@Repository
public class ObligationQueryRepository {

    // Allocated so far per target, counting only payments that are still RECORDED (a reversal releases them).
    private static final String ALLOCATED = """
            select coalesce(a.%1$s, a.opening_obligation_id) as target_id, sum(a.allocated_amount) as allocated
            from %2$s a
            join payments p on p.id = a.payment_id and p.status in ('RECORDED', 'PENDING')
            group by coalesce(a.%1$s, a.opening_obligation_id)""";

    // Every customer obligation with its owner and outstanding. Items count from confirmation on (assumption Q2):
    // PLACED and CANCELLED orders are not owed (payable = false).
    private static final String CUSTOMER_OBLIGATIONS = """
            with allocated as (%s),
                 returned as (
                     select order_item_id, sum(returned_weight_kg) as returned_kg
                     from customer_returns
                     group by order_item_id
                 )
            select 'ORDER_ITEM' as target_type, i.id as target_id, o.customer_id as party_id,
                   o.order_date as obligation_date, o.order_number as number, i.line_no as line_no,
                   o.order_number || ' line ' || i.line_no as label,
                   o.status in ('CONFIRMED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'COMPLETED') as payable,
                   round((i.required_weight_kg - coalesce(r.returned_kg, 0)) * i.rate_per_kg, 2) - coalesce(a.allocated, 0) as outstanding,
                   i.created_at as created_at
            from order_items i
            join customer_orders o on o.id = i.order_id
            left join allocated a on a.target_id = i.id
            left join returned r on r.order_item_id = i.id
            union all
            select 'OPENING_OBLIGATION', ob.id, ob.party_id, ob.obligation_date, '', 0,
                   'Opening CUS ' || to_char(ob.obligation_date, 'YYYY-MM-DD'), true,
                   ob.original_amount - coalesce(a.allocated, 0), ob.created_at
            from opening_obligations ob
            left join allocated a on a.target_id = ob.id
            where ob.purpose = 'CUSTOMER'""".formatted(ALLOCATED.formatted("order_item_id",
            "customer_payment_allocations"));

    // Every supplier obligation: purchases (payable per the purchase_payables view, assumption Q1) and opening payables.
    // A cancelled purchase is not owed.
    private static final String SUPPLIER_OBLIGATIONS = """
            with allocated as (%s)
            select 'PURCHASE' as target_type, pp.purchase_id as target_id, pp.supplier_id as party_id,
                   pp.purchase_date as obligation_date, pp.purchase_number as number, 0 as line_no,
                   pp.purchase_number as label, pp.status <> 'CANCELLED' as payable,
                   pp.payable - coalesce(a.allocated, 0) as outstanding, pu.created_at as created_at
            from purchase_payables pp
            join purchases pu on pu.id = pp.purchase_id
            left join allocated a on a.target_id = pp.purchase_id
            union all
            select 'OPENING_OBLIGATION', ob.id, ob.party_id, ob.obligation_date, '', 0,
                   'Opening SUP ' || to_char(ob.obligation_date, 'YYYY-MM-DD'), true,
                   ob.original_amount - coalesce(a.allocated, 0), ob.created_at
            from opening_obligations ob
            left join allocated a on a.target_id = ob.id
            where ob.purpose = 'SUPPLIER'""".formatted(ALLOCATED.formatted("purchase_id",
            "supplier_payment_allocations"));

    // Every manufacturer obligation: outsourcing jobs (payable = received kg x the job's historical rate, FR-OUT-05; owed
    // from the first receipt, oldest issue first) and opening payables.
    private static final String MANUFACTURER_OBLIGATIONS = """
            with allocated as (%s)
            select 'OUTSOURCING_JOB' as target_type, j.id as target_id, j.manufacturer_id as party_id,
                   j.issue_date as obligation_date, j.job_number as number, 0 as line_no, j.job_number as label,
                   j.received_weight_kg > 0 as payable,
                   round(j.received_weight_kg * j.rate_per_kg, 2) - coalesce(a.allocated, 0) as outstanding,
                   j.created_at as created_at
            from outsourcing_jobs j
            left join allocated a on a.target_id = j.id
            union all
            select 'OPENING_OBLIGATION', ob.id, ob.party_id, ob.obligation_date, '', 0,
                   'Opening MAN ' || to_char(ob.obligation_date, 'YYYY-MM-DD'), true,
                   ob.original_amount - coalesce(a.allocated, 0), ob.created_at
            from opening_obligations ob
            left join allocated a on a.target_id = ob.id
            where ob.purpose = 'MANUFACTURER'""".formatted(ALLOCATED.formatted("outsourcing_job_id",
            "manufacturer_payment_allocations"));

    // Every worker obligation: recorded (not reversed) work, oldest work date first (FR-PAY-10), and opening payables.
    private static final String WORKER_OBLIGATIONS = """
            with allocated as (%s)
            select 'WORK_RECORD' as target_type, w.id as target_id, w.worker_id as party_id,
                   w.work_date as obligation_date, '' as number, 0 as line_no,
                   'Work ' || w.work_type || ' ' || to_char(w.work_date, 'YYYY-MM-DD') as label,
                   w.status = 'RECORDED' as payable, w.amount - coalesce(a.allocated, 0) as outstanding,
                   w.created_at as created_at
            from work_records w
            left join allocated a on a.target_id = w.id
            union all
            select 'OPENING_OBLIGATION', ob.id, ob.party_id, ob.obligation_date, '', 0,
                   'Opening WRK ' || to_char(ob.obligation_date, 'YYYY-MM-DD'), true,
                   ob.original_amount - coalesce(a.allocated, 0), ob.created_at
            from opening_obligations ob
            left join allocated a on a.target_id = ob.id
            where ob.purpose = 'WORKER'""".formatted(ALLOCATED.formatted("work_record_id",
            "worker_payment_allocations"));

    private final NamedParameterJdbcTemplate jdbc;

    public ObligationQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // FR-PAY-24: lock the aggregate rows of the party's obligations in ascending id order (ADR-028: party -> aggregate),
    // so nothing that changes an amount (an order edit, a supplier return) can run between reading the outstanding and
    // storing the allocations. Read-only SQL on another module's rows: a lock, never a write.
    public void lockObligations(PaymentPurpose purpose, UUID partyId) {
        String sql = switch (purpose) {
            case CUSTOMER -> "select id from customer_orders where customer_id = :party and status in"
                    + " ('CONFIRMED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'COMPLETED') order by id for update";
            case SUPPLIER -> "select id from purchases where supplier_id = :party and status <> 'CANCELLED'"
                    + " order by id for update";
            case MANUFACTURER -> "select id from outsourcing_jobs where manufacturer_id = :party and status in"
                    + " ('PARTIALLY_RECEIVED', 'COMPLETED') order by id for update";
            case WORKER -> "select id from work_records where worker_id = :party and status = 'RECORDED'"
                    + " order by id for update";
        };
        jdbc.queryForList(sql, new MapSqlParameterSource("party", partyId), UUID.class);
    }

    // Settlement order (FR-PAY-02, FR-PAY-08): obligation date, then business number, then line; opening obligations
    // by date, creation, id.
    public List<Obligation> openObligations(PaymentPurpose purpose, UUID partyId) {
        return jdbc.query("select * from (" + obligations(purpose) + ") x where party_id = :party and payable"
                        + " and outstanding > 0 order by obligation_date, number, line_no, created_at, target_id",
                new MapSqlParameterSource("party", partyId),
                (rs, n) -> new Obligation(AllocationTargetType.valueOf(rs.getString("target_type")),
                        rs.getObject("target_id", UUID.class), rs.getObject("obligation_date", LocalDate.class),
                        rs.getString("label"), rs.getBigDecimal("outstanding")));
    }

    // One target as an explicit allocation sees it: whose it is and whether it is owed at all.
    public Optional<TargetState> target(PaymentPurpose purpose, AllocationTargetType type, UUID targetId) {
        return jdbc.query("select party_id, payable, outstanding, label from (" + obligations(purpose)
                        + ") x where target_id = :id and target_type = :type",
                new MapSqlParameterSource("id", targetId).addValue("type", type.name()),
                (rs, n) -> new TargetState(rs.getObject("party_id", UUID.class), rs.getBoolean("payable"),
                        rs.getBigDecimal("outstanding"), rs.getString("label"))).stream().findFirst();
    }

    // Everything one purpose still owes or is owed, summed by the database: the same rows a party's balance is built
    // from (owed obligations only), so the total equals the per-party balances added up (FR-RPT-01 AC1).
    public BigDecimal totalOutstanding(PaymentPurpose purpose) {
        return jdbc.queryForObject("select coalesce(sum(outstanding), 0) from (" + obligations(purpose)
                + ") x where payable", new MapSqlParameterSource(), BigDecimal.class);
    }

    // Unallocated part of every active customer payment (the advance of all customers together, FR-PAY-05) minus refunds.
    public BigDecimal totalCustomerAdvance() {
        return jdbc.queryForObject("""
                select coalesce(sum(unallocated), 0) from (
                    select p.amount - coalesce(sum(cpa.allocated_amount), 0) - coalesce(r.refunded, 0) as unallocated
                    from payments p
                    left join customer_payment_allocations cpa on cpa.payment_id = p.id
                    left join (select refund_of_payment_id, sum(amount) as refunded from payments where status = 'RECORDED' and refund_of_payment_id is not null group by refund_of_payment_id) r on r.refund_of_payment_id = p.id
                    where p.purpose = 'CUSTOMER' and p.status = 'RECORDED' and p.refund_of_payment_id is null
                    group by p.id, p.amount, r.refunded) x
                where unallocated > 0""", new MapSqlParameterSource(), BigDecimal.class);
    }

    // Per customer in one statement (FR-RPT-03): outstanding over owed obligations, the advance, how many orders still
    // have something to pay and the date of the oldest unpaid obligation. Customers with neither are left out.
    public List<CustomerBalance> customerBalances() {
        return jdbc.query("""
                with ob as (%s),
                per as (select party_id, target_type = 'ORDER_ITEM' as is_order,
                               case when target_type = 'ORDER_ITEM' then number else target_id::text end as grp,
                               min(obligation_date) as obligation_date, sum(outstanding) as outstanding
                        from ob where payable group by party_id, is_order, grp),
                owed as (select party_id, sum(outstanding) as outstanding,
                                count(*) filter (where is_order and outstanding > 0) as unpaid_orders,
                                min(obligation_date) filter (where outstanding > 0) as oldest
                         from per group by party_id),
                adv as (select party_id, sum(unallocated) as advance from (
                            select p.party_id, p.amount - coalesce(sum(cpa.allocated_amount), 0) - coalesce(r.refunded, 0) as unallocated
                            from payments p
                            left join customer_payment_allocations cpa on cpa.payment_id = p.id
                            left join (select refund_of_payment_id, sum(amount) as refunded from payments where status = 'RECORDED' and refund_of_payment_id is not null group by refund_of_payment_id) r on r.refund_of_payment_id = p.id
                            where p.purpose = 'CUSTOMER' and p.status = 'RECORDED' and p.refund_of_payment_id is null
                            group by p.id, p.party_id, p.amount, r.refunded) x
                        where unallocated > 0 group by party_id)
                select coalesce(o.party_id, a.party_id) as party_id, coalesce(o.outstanding, 0) as outstanding,
                       coalesce(a.advance, 0) as advance, coalesce(o.unpaid_orders, 0) as unpaid_orders, o.oldest
                from owed o full join adv a on a.party_id = o.party_id
                where coalesce(o.outstanding, 0) <> 0 or coalesce(a.advance, 0) > 0""".formatted(CUSTOMER_OBLIGATIONS),
                new MapSqlParameterSource(),
                (rs, n) -> new CustomerBalance(rs.getObject("party_id", UUID.class), rs.getBigDecimal("outstanding"),
                        rs.getBigDecimal("advance"), rs.getLong("unpaid_orders"),
                        rs.getObject("oldest", LocalDate.class)));
    }

    // Allocated amount per order item / per purchase (active payments only), one GROUP BY for the whole page.
    public Map<UUID, BigDecimal> allocatedByOrderItem(Collection<UUID> itemIds) {
        return allocated("customer_payment_allocations", "order_item_id", itemIds);
    }

    public Map<UUID, BigDecimal> allocatedByPurchase(Collection<UUID> purchaseIds) {
        return allocated("supplier_payment_allocations", "purchase_id", purchaseIds);
    }

    public Map<UUID, BigDecimal> allocatedByJob(Collection<UUID> jobIds) {
        return allocated("manufacturer_payment_allocations", "outsourcing_job_id", jobIds);
    }

    public Map<UUID, BigDecimal> allocatedByWorkRecord(Collection<UUID> recordIds) {
        return allocated("worker_payment_allocations", "work_record_id", recordIds);
    }

    // Allocated amount per payment (rows of every purpose), for list views.
    public Map<UUID, BigDecimal> allocatedByPayment(Collection<UUID> paymentIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (paymentIds.isEmpty()) {
            return result;
        }
        jdbc.query("select payment_id, sum(allocated_amount) from (select payment_id, allocated_amount from"
                        + " customer_payment_allocations union all select payment_id, allocated_amount from"
                        + " supplier_payment_allocations union all select payment_id, allocated_amount from"
                        + " manufacturer_payment_allocations union all select payment_id, allocated_amount from"
                        + " worker_payment_allocations) a where payment_id in (:ids) group by payment_id",
                new MapSqlParameterSource("ids", paymentIds),
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
                });
        return result;
    }

    // Allocated amount per opening obligation (any purpose's rows, active payments only).
    public Map<UUID, BigDecimal> allocatedByOpeningObligation(Collection<UUID> ids) {
        Map<UUID, BigDecimal> result = allocated("customer_payment_allocations", "opening_obligation_id", ids);
        for (String table : List.of("supplier_payment_allocations", "manufacturer_payment_allocations",
                "worker_payment_allocations")) {
            allocated(table, "opening_obligation_id", ids).forEach((k, v) -> result.merge(k, v, BigDecimal::add));
        }
        return result;
    }

    // Opening obligations not yet fully paid (for the outstandingOnly filter).
    public List<UUID> openOpeningObligationIds() {
        return jdbc.queryForList("""
                        select ob.id from opening_obligations ob
                        where ob.original_amount > coalesce((
                            select sum(a.allocated_amount) from (
                                select payment_id, opening_obligation_id, allocated_amount from customer_payment_allocations
                                union all
                                select payment_id, opening_obligation_id, allocated_amount from supplier_payment_allocations
                                union all
                                select payment_id, opening_obligation_id, allocated_amount
                                from manufacturer_payment_allocations
                                union all
                                select payment_id, opening_obligation_id, allocated_amount from worker_payment_allocations
                            ) a join payments p on p.id = a.payment_id and p.status in ('RECORDED', 'PENDING')
                            where a.opening_obligation_id = ob.id), 0)""",
                new MapSqlParameterSource(), UUID.class);
    }

    // The customer's payments that still have an unallocated part (the advance), oldest payment first (FR-PAY-05).
    // Reversed payments have no advance (AC3).
    public List<Advance> customerAdvances(UUID partyId) {
        return jdbc.query("""
                        select p.id, p.amount - coalesce(sum(cpa.allocated_amount), 0) - coalesce(r.refunded, 0) as unallocated
                        from payments p
                        left join customer_payment_allocations cpa on cpa.payment_id = p.id
                        left join (select refund_of_payment_id, sum(amount) as refunded from payments where status = 'RECORDED' and refund_of_payment_id is not null group by refund_of_payment_id) r on r.refund_of_payment_id = p.id
                        where p.party_id = :party and p.purpose = 'CUSTOMER' and p.status = 'RECORDED' and p.refund_of_payment_id is null
                        group by p.id, p.amount, p.payment_date, p.payment_number, r.refunded
                        having p.amount - coalesce(sum(cpa.allocated_amount), 0) - coalesce(r.refunded, 0) > 0
                        order by p.payment_date, p.payment_number""",
                new MapSqlParameterSource("party", partyId),
                (rs, n) -> new Advance(rs.getObject("id", UUID.class), rs.getBigDecimal("unallocated")));
    }

    // Per owed order of the customer (CONFIRMED..COMPLETED, assumption Q2): amount and allocations of active payments,
    // only orders with something outstanding, oldest first (FR-PAY-17).
    public List<OrderBalance> customerOrderBalances(UUID partyId) {
        return jdbc.query("""
                        select o.id, o.order_number, o.order_date, o.status,
                               sum(round((i.required_weight_kg - coalesce(r.returned_kg, 0)) * i.rate_per_kg, 2)) as amount,
                               coalesce(sum(a.allocated), 0) as allocated
                        from customer_orders o
                        join order_items i on i.order_id = o.id
                        left join (select order_item_id, sum(returned_weight_kg) as returned_kg
                                   from customer_returns
                                   group by order_item_id) r on r.order_item_id = i.id
                        left join (select cpa.order_item_id, sum(cpa.allocated_amount) as allocated
                                   from customer_payment_allocations cpa
                                   join payments p on p.id = cpa.payment_id and p.status in ('RECORDED', 'PENDING')
                                   where cpa.order_item_id is not null
                                   group by cpa.order_item_id) a on a.order_item_id = i.id
                        where o.customer_id = :party
                          and o.status in ('CONFIRMED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'COMPLETED')
                        group by o.id, o.order_number, o.order_date, o.status
                        having sum(round((i.required_weight_kg - coalesce(r.returned_kg, 0)) * i.rate_per_kg, 2)) - coalesce(sum(a.allocated), 0) <> 0
                        order by o.order_date, o.order_number""",
                new MapSqlParameterSource("party", partyId),
                (rs, n) -> new OrderBalance(rs.getObject("id", UUID.class), rs.getString("order_number"),
                        rs.getObject("order_date", LocalDate.class), OrderStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("allocated")));
    }

    // Opening obligations of a party for one purpose, with allocations of active payments, still open.
    public List<OpeningBalance> openingBalances(UUID partyId, String purpose) {
        String table = switch (purpose) {
            case "CUSTOMER" -> "customer_payment_allocations";
            case "SUPPLIER" -> "supplier_payment_allocations";
            case "MANUFACTURER" -> "manufacturer_payment_allocations";
            case "WORKER" -> "worker_payment_allocations";
            default -> throw new IllegalArgumentException("Unknown purpose " + purpose);
        };
        return jdbc.query("select ob.id, ob.obligation_date, ob.reference_note, ob.original_amount,"
                        + " coalesce(sum(a.allocated_amount) filter (where p.status in ('RECORDED', 'PENDING')), 0) as allocated"
                        + " from opening_obligations ob left join " + table + " a on a.opening_obligation_id = ob.id"
                        + " left join payments p on p.id = a.payment_id"
                        + " where ob.party_id = :party and ob.purpose = :purpose"
                        + " group by ob.id having ob.original_amount - coalesce(sum(a.allocated_amount)"
                        + " filter (where p.status in ('RECORDED', 'PENDING')), 0) <> 0 order by ob.obligation_date, ob.created_at",
                new MapSqlParameterSource("party", partyId).addValue("purpose", purpose),
                (rs, n) -> new OpeningBalance(rs.getObject("id", UUID.class),
                        rs.getObject("obligation_date", LocalDate.class), rs.getString("reference_note"),
                        rs.getBigDecimal("original_amount"), rs.getBigDecimal("allocated")));
    }

    // The allocations of some payments with a readable label ("ORD-2026-0001 line 1", "PUR-2026-0003", "JOB-2026-0002")
    // for responses.
    public List<AllocationRow> allocations(Collection<UUID> paymentIds) {
        if (paymentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                        select cpa.id, cpa.payment_id, coalesce(cpa.order_item_id, cpa.opening_obligation_id) as target_id,
                               case when cpa.order_item_id is not null then 'ORDER_ITEM' else 'OPENING_OBLIGATION' end
                                   as target_type,
                               coalesce(o.order_number || ' line ' || i.line_no,
                                        'Opening CUS ' || to_char(ob.obligation_date, 'YYYY-MM-DD')) as label,
                               coalesce(o.order_date, ob.obligation_date) as obligation_date, cpa.allocated_amount,
                               p.status in ('REVERSED', 'BOUNCED') as released
                        from customer_payment_allocations cpa
                        join payments p on p.id = cpa.payment_id
                        left join order_items i on i.id = cpa.order_item_id
                        left join customer_orders o on o.id = i.order_id
                        left join opening_obligations ob on ob.id = cpa.opening_obligation_id
                        where cpa.payment_id in (:ids)
                        union all
                        select spa.id, spa.payment_id, coalesce(spa.purchase_id, spa.opening_obligation_id),
                               case when spa.purchase_id is not null then 'PURCHASE' else 'OPENING_OBLIGATION' end,
                               coalesce(pu.purchase_number, 'Opening SUP ' || to_char(ob.obligation_date, 'YYYY-MM-DD')),
                               coalesce(pu.purchase_date, ob.obligation_date), spa.allocated_amount,
                               p.status in ('REVERSED', 'BOUNCED')
                        from supplier_payment_allocations spa
                        join payments p on p.id = spa.payment_id
                        left join purchases pu on pu.id = spa.purchase_id
                        left join opening_obligations ob on ob.id = spa.opening_obligation_id
                        where spa.payment_id in (:ids)
                        union all
                        select mpa.id, mpa.payment_id, coalesce(mpa.outsourcing_job_id, mpa.opening_obligation_id),
                               case when mpa.outsourcing_job_id is not null then 'OUTSOURCING_JOB'
                                    else 'OPENING_OBLIGATION' end,
                               coalesce(j.job_number, 'Opening MAN ' || to_char(ob.obligation_date, 'YYYY-MM-DD')),
                               coalesce(j.issue_date, ob.obligation_date), mpa.allocated_amount,
                               p.status in ('REVERSED', 'BOUNCED')
                        from manufacturer_payment_allocations mpa
                        join payments p on p.id = mpa.payment_id
                        left join outsourcing_jobs j on j.id = mpa.outsourcing_job_id
                        left join opening_obligations ob on ob.id = mpa.opening_obligation_id
                        where mpa.payment_id in (:ids)
                        union all
                        select wpa.id, wpa.payment_id, coalesce(wpa.work_record_id, wpa.opening_obligation_id),
                               case when wpa.work_record_id is not null then 'WORK_RECORD' else 'OPENING_OBLIGATION' end,
                               coalesce('Work ' || w.work_type || ' ' || to_char(w.work_date, 'YYYY-MM-DD'),
                                        'Opening WRK ' || to_char(ob.obligation_date, 'YYYY-MM-DD')),
                               coalesce(w.work_date, ob.obligation_date), wpa.allocated_amount, p.status in ('REVERSED', 'BOUNCED')
                        from worker_payment_allocations wpa
                        join payments p on p.id = wpa.payment_id
                        left join work_records w on w.id = wpa.work_record_id
                        left join opening_obligations ob on ob.id = wpa.opening_obligation_id
                        where wpa.payment_id in (:ids)
                        order by obligation_date, label""",
                new MapSqlParameterSource("ids", paymentIds),
                (rs, n) -> new AllocationRow(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                        AllocationTargetType.valueOf(rs.getString("target_type")), rs.getObject("target_id", UUID.class),
                        rs.getString("label"), rs.getObject("obligation_date", LocalDate.class),
                        rs.getBigDecimal("allocated_amount"), rs.getBoolean("released")));
    }

    private static String obligations(PaymentPurpose purpose) {
        return switch (purpose) {
            case CUSTOMER -> CUSTOMER_OBLIGATIONS;
            case SUPPLIER -> SUPPLIER_OBLIGATIONS;
            case MANUFACTURER -> MANUFACTURER_OBLIGATIONS;
            case WORKER -> WORKER_OBLIGATIONS;
        };
    }

    private Map<UUID, BigDecimal> allocated(String table, String column, Collection<UUID> ids) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("select a." + column + ", sum(a.allocated_amount) from " + table + " a"
                        + " join payments p on p.id = a.payment_id and p.status in ('RECORDED', 'PENDING')"
                        + " where a." + column + " in (:ids) group by a." + column,
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
                });
        return result;
    }

    public record Advance(UUID paymentId, BigDecimal unallocated) {
    }

    public record OrderBalance(UUID orderId, String orderNumber, LocalDate orderDate, OrderStatus status,
                               BigDecimal amount, BigDecimal allocated) {
    }

    public record OpeningBalance(UUID id, LocalDate obligationDate, String referenceNote, BigDecimal originalAmount,
                                 BigDecimal allocated) {
    }

    public record TargetState(UUID partyId, boolean payable, BigDecimal outstanding, String label) {
    }

    public record AllocationRow(UUID id, UUID paymentId, AllocationTargetType targetType, UUID targetId, String label,
                                LocalDate obligationDate, BigDecimal amount, boolean released) {
    }
}

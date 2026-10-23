package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.orders.domain.OrderStatus;

// Read-only SQL for the customer statement (FR-RPT-04): every debit and credit of one customer up to a date, in one
// query. Owed orders follow OrderStatus.isObligation (assumption Q2), so the rule stays in one place.
@Repository
public class CustomerReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CustomerReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Orders and opening obligations are debits, payments credits; a reversed payment adds a REVERSAL debit on the
    // day it was reversed (business time zone). Oldest first; same day: in the order they were recorded.
    public List<Entry> entries(UUID customerId, LocalDate to, String zone) {
        List<String> owed = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isObligation).map(Enum::name)
                .toList();
        return jdbc.query("""
                        select entry_date, kind, number, debit, credit from (
                            select o.order_date as entry_date, 'ORDER' as kind, o.order_number as number,
                                   sum(i.amount) as debit, 0 as credit, o.created_at as recorded_at
                            from customer_orders o join order_items i on i.order_id = o.id
                            where o.customer_id = :customer and o.status in (:owed)
                            group by o.id, o.order_date, o.order_number, o.created_at
                            union all
                            select ob.obligation_date, 'OPENING_OBLIGATION', null, ob.original_amount, 0, ob.created_at
                            from opening_obligations ob where ob.party_id = :customer and ob.purpose = 'CUSTOMER'
                            union all
                            select cr.return_date, 'RETURN', cr.return_number, 0,
                                   round(cr.returned_weight_kg * i.rate_per_kg, 2), cr.created_at
                            from customer_returns cr
                            join order_items i on i.id = cr.order_item_id
                            join customer_orders o on o.id = cr.order_id
                            where o.customer_id = :customer
                            union all
                            select p.payment_date, 'PAYMENT', p.payment_number, 0, p.amount, p.created_at
                            from payments p where p.party_id = :customer and p.purpose = 'CUSTOMER'
                            union all
                            select (p.reversed_at at time zone :zone)::date, 'REVERSAL', p.payment_number, p.amount, 0,
                                   p.reversed_at
                            from payments p
                            where p.party_id = :customer and p.purpose = 'CUSTOMER' and p.status = 'REVERSED') x
                        where entry_date <= :to
                        order by entry_date, recorded_at, kind""",
                new MapSqlParameterSource("customer", customerId).addValue("owed", owed).addValue("to", to)
                        .addValue("zone", zone),
                (rs, n) -> new Entry(rs.getObject("entry_date", LocalDate.class), rs.getString("kind"),
                        rs.getString("number"), rs.getBigDecimal("debit"), rs.getBigDecimal("credit")));
    }

    public record Entry(LocalDate date, String kind, String number, BigDecimal debit, BigDecimal credit) {
    }
}

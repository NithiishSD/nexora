package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.domain.OrderStatus;

// Order items placed or delivered within a period, in one query (sales report, FR-RPT-07). Cancelled orders are out.
@Repository
public class SalesReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SalesReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ItemRow> items(LocalDate from, LocalDate to, UUID customerId, ProductType productType) {
        return jdbc.query("""
                        select i.id, o.customer_id, i.product_type, o.order_date, o.delivered_on, o.status, i.amount,
                               i.required_weight_kg
                        from order_items i join customer_orders o on o.id = i.order_id
                        where o.status <> 'CANCELLED'
                          and (o.order_date between :from and :to or o.delivered_on between :from and :to)
                          and (cast(:customer as uuid) is null or o.customer_id = :customer)
                          and (cast(:product as varchar) is null or i.product_type = :product)""",
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("customer", customerId)
                        .addValue("product", productType == null ? null : productType.name()),
                (rs, n) -> new ItemRow(rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class),
                        ProductType.valueOf(rs.getString("product_type")), rs.getObject("order_date", LocalDate.class),
                        rs.getObject("delivered_on", LocalDate.class), OrderStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("required_weight_kg")));
    }

    // deliveredOn is set exactly when the goods left (DELIVERED or COMPLETED).
    public record ItemRow(UUID itemId, UUID customerId, ProductType productType, LocalDate orderDate,
                          LocalDate deliveredOn, OrderStatus status, BigDecimal amount, BigDecimal weightKg) {
    }
}

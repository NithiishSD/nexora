package com.nexora.reporting.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.inventory.domain.ProductType;

// Purchase report (FR-RPT-06) in one GROUP BY. Weights are summed from receipts and returns; the payable comes from
// the purchase_payables view, the single home of assumption Q1.
@Repository
public class SupplierReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SupplierReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PurchaseGroup> purchases(LocalDate from, LocalDate to, UUID supplierId, ProductType productType) {
        return jdbc.query("""
                        select p.supplier_id, p.product_type, count(*) as purchases,
                               sum(p.ordered_weight_kg) as ordered_kg, coalesce(sum(r.received), 0) as received_kg,
                               coalesce(sum(r.accepted), 0) as accepted_kg, coalesce(sum(r.rejected), 0) as rejected_kg,
                               coalesce(sum(t.returned), 0) as returned_kg,
                               sum(round(p.ordered_weight_kg * p.rate_per_kg, 2)) as ordered_value,
                               sum(pp.payable) as payable_value
                        from purchases p
                        join purchase_payables pp on pp.purchase_id = p.id
                        left join (select purchase_id, sum(received_weight_kg) as received,
                                          sum(accepted_weight_kg) as accepted, sum(rejected_weight_kg) as rejected
                                   from material_receipts group by purchase_id) r on r.purchase_id = p.id
                        left join (select purchase_id, sum(returned_weight_kg) as returned
                                   from supplier_returns group by purchase_id) t on t.purchase_id = p.id
                        where p.status <> 'CANCELLED' and p.purchase_date between :from and :to
                          and (cast(:supplier as uuid) is null or p.supplier_id = :supplier)
                          and (cast(:product as varchar) is null or p.product_type = :product)
                        group by p.supplier_id, p.product_type""",
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("supplier", supplierId)
                        .addValue("product", productType == null ? null : productType.name()),
                (rs, n) -> new PurchaseGroup(rs.getObject("supplier_id", UUID.class),
                        ProductType.valueOf(rs.getString("product_type")), rs.getLong("purchases"),
                        rs.getBigDecimal("ordered_kg"), rs.getBigDecimal("received_kg"),
                        rs.getBigDecimal("accepted_kg"), rs.getBigDecimal("rejected_kg"),
                        rs.getBigDecimal("returned_kg"), rs.getBigDecimal("ordered_value"),
                        rs.getBigDecimal("payable_value")));
    }

    public record PurchaseGroup(UUID supplierId, ProductType productType, long purchases, BigDecimal orderedKg,
                                BigDecimal receivedKg, BigDecimal acceptedKg, BigDecimal rejectedKg,
                                BigDecimal returnedKg, BigDecimal orderedValue, BigDecimal payableValue) {
    }
}

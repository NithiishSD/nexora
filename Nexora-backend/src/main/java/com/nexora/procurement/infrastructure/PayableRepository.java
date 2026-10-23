package com.nexora.procurement.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexora.procurement.domain.PurchaseStatus;

// Supplier payables by SQL aggregation (DOMAIN_RULES section 6): the purchase_payables view (assumption Q1) and
// opening payables, minus allocations of RECORDED payments. Read only.
@Repository
public class PayableRepository {

    private static final String PURCHASES = """
            select pp.purchase_id, pp.supplier_id, pp.purchase_number, pp.purchase_date, pp.due_date, pp.status,
                   pp.payable, coalesce(a.allocated, 0) as allocated
            from purchase_payables pp
            left join (select spa.purchase_id, sum(spa.allocated_amount) as allocated
                       from supplier_payment_allocations spa
                       join payments p on p.id = spa.payment_id and p.status = 'RECORDED'
                       where spa.purchase_id is not null
                       group by spa.purchase_id) a on a.purchase_id = pp.purchase_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PayableRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every purchase, or one supplier's, with payable and allocated.
    public List<PurchaseRow> purchases(UUID supplierId) {
        String where = supplierId == null ? "" : " where pp.supplier_id = :supplier";
        return jdbc.query(PURCHASES + where + " order by pp.due_date, pp.purchase_number",
                new MapSqlParameterSource("supplier", supplierId),
                (rs, n) -> new PurchaseRow(rs.getObject("purchase_id", UUID.class), rs.getObject("supplier_id", UUID.class),
                        rs.getString("purchase_number"), rs.getObject("purchase_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class), PurchaseStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("payable"), rs.getBigDecimal("allocated")));
    }

    // Opening payables carried over at go-live (FR-PAY-22), or one supplier's.
    public List<OpeningRow> openingPayables(UUID supplierId) {
        String where = supplierId == null ? "" : " and ob.party_id = :supplier";
        return jdbc.query("""
                        select ob.id, ob.party_id, ob.obligation_date, ob.due_date, ob.reference_note, ob.original_amount,
                               coalesce(sum(spa.allocated_amount) filter (where p.status = 'RECORDED'), 0) as allocated
                        from opening_obligations ob
                        left join supplier_payment_allocations spa on spa.opening_obligation_id = ob.id
                        left join payments p on p.id = spa.payment_id
                        where ob.purpose = 'SUPPLIER'""" + where + """

                        group by ob.id order by ob.obligation_date, ob.created_at""",
                new MapSqlParameterSource("supplier", supplierId),
                (rs, n) -> new OpeningRow(rs.getObject("id", UUID.class), rs.getObject("party_id", UUID.class),
                        rs.getObject("obligation_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
                        rs.getString("reference_note"), rs.getBigDecimal("original_amount"),
                        rs.getBigDecimal("allocated")));
    }

    public record PurchaseRow(UUID purchaseId, UUID supplierId, String purchaseNumber, LocalDate purchaseDate,
                              LocalDate dueDate, PurchaseStatus status, BigDecimal payable, BigDecimal allocated) {
    }

    public record OpeningRow(UUID id, UUID supplierId, LocalDate obligationDate, LocalDate dueDate, String referenceNote,
                             BigDecimal originalAmount, BigDecimal allocated) {
    }
}

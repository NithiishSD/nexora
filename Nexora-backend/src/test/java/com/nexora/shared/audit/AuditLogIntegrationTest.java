package com.nexora.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.support.AbstractApiTest;

class AuditLogIntegrationTest extends AbstractApiTest {

    @Autowired
    AuditService audit;

    @Test
    @DisplayName("NFR-OBS-06 audit service writes entry in callers transaction")
    void auditService_writesEntryInCallersTransaction() {
        UUID entity = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // committed with the caller: one row; no user in the context = a system action (actor NULL)
        tx.executeWithoutResult(s -> audit.record(AuditAction.PAYMENT_REVERSED, "PAYMENT", entity, "wrong customer",
                Map.of("amount", "500.00")));
        Map<String, Object> row = jdbc.queryForMap("select action, entity_type, reason, actor_user_id,"
                + " details->>'amount' as amount from audit_log where entity_id = ?", entity);
        assertThat(row).containsEntry("action", "PAYMENT_REVERSED").containsEntry("entity_type", "PAYMENT")
                .containsEntry("reason", "wrong customer").containsEntry("amount", "500.00");
        assertThat(row.get("actor_user_id")).isNull();

        // rolled back with the caller: no row
        UUID rolledBack = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            audit.record(AuditAction.PAYMENT_REVERSED, "PAYMENT", rolledBack, "x", null);
            s.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Integer.class, rolledBack))
                .isZero();
        // outside a use case it refuses to run (MANDATORY)
        assertThatThrownBy(() -> audit.record(AuditAction.PAYMENT_REVERSED, "PAYMENT", entity, "x", null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("NFR-OBS-06 FR-INV-12 stock adjustment and wastage write audit entry with reason")
    void stockAdjustmentAndWastage_writeAuditEntryWithReason() throws Exception {
        data.rawSilkInStock("100.000");
        String wastage = body(asOwner(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":1.500,
                 "movementDate":"%s","reason":"moisture loss"}""".formatted(today())))
                .andExpect(status().isCreated())).get("id").asString();
        String adjustment = body(asOwner(postJson("/api/v1/inventory/adjustments", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","toLocation":"WASTAGE","quantityKg":2.000,
                 "movementDate":"%s","reason":"recount 2026-10"}""".formatted(today())))
                .andExpect(status().isCreated())).get("id").asString();

        // one INVENTORY_ADJUSTED row each, with the reason, the movement and the logged-in owner as actor
        List<Map<String, Object>> rows = jdbc.queryForList("select entity_id::text as entity, reason, actor_user_id,"
                + " details->>'movementType' as type from audit_log where action = 'INVENTORY_ADJUSTED' order by occurred_at");
        assertThat(rows).extracting(r -> r.get("entity"), r -> r.get("reason"), r -> r.get("type")).containsExactly(
                org.assertj.core.groups.Tuple.tuple(wastage, "moisture loss", "WASTAGE"),
                org.assertj.core.groups.Tuple.tuple(adjustment, "recount 2026-10", "ADJUSTMENT"));
        assertThat(rows).allSatisfy(r -> assertThat(r.get("actor_user_id")).isNotNull());

        // a refused adjustment (more than the stock) writes no audit row
        asOwner(postJson("/api/v1/inventory/adjustments", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","toLocation":"WASTAGE","quantityKg":500.000,
                 "movementDate":"%s","reason":"too much"}""".formatted(today()))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("NFR-OBS-06 cancellations and party status changes write audit entries")
    void cancellationsAndPartyStatusChanges_writeAuditEntries() throws Exception {
        String customer = data.customer().id().toString();
        asOwner(postJson("/api/v1/parties/" + customer + "/deactivate", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/parties/" + customer + "/activate", "")).andExpect(status().isOk());
        String order = createOrder(UUID.fromString(customer), "RAW_SILK", "1.000", "1.00");
        asOwner(postJson("/api/v1/orders/" + order + "/cancel", "{\"reason\":\"customer withdrew\"}"))
                .andExpect(status().isOk());
        String purchase = createPurchase(data.supplier().id(), "RAW_SILK", "10.000", "100.00");
        asOwner(postJson("/api/v1/purchases/" + purchase + "/cancel", "{\"reason\":\"supplier out of stock\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList("select action || ':' || coalesce(reason, '-') from audit_log order by occurred_at",
                String.class)).containsExactly("PARTY_DEACTIVATED:-", "PARTY_ACTIVATED:-",
                "ORDER_CANCELLED:customer withdrew", "PURCHASE_CANCELLED:supplier out of stock");
        assertThat(jdbc.queryForObject("select entity_id::text from audit_log where action = 'ORDER_CANCELLED'",
                String.class)).isEqualTo(order);

        // the fifth wrong password locks the account: a system action (no actor) on the user
        String username = tokens.createUser("STAFF");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(postJson("/api/v1/auth/login", """
                    {"username":"%s","password":"wrong-password-1"}""".formatted(username)))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_log a join users u on u.id = a.entity_id"
                + " where a.action = 'LOGIN_LOCKED' and u.username = ? and a.actor_user_id is null", Integer.class,
                username)).isEqualTo(1);
    }
}

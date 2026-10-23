package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.nexora.support.AbstractApiTest;

// ADR-032, 04-DATA-MODEL section 14: every insert-only table refuses UPDATE and DELETE in the database itself, whatever
// the user (here the test's superuser). Each table first gets a real row through the API, so the row trigger fires.
class ImmutabilityTriggerIntegrationTest extends AbstractApiTest {

    private static final List<String> INSERT_ONLY = List.of("material_movements", "financial_transactions", "audit_log",
            "material_receipts", "supplier_returns", "account_transfers", "opening_obligations",
            "customer_payment_allocations", "supplier_payment_allocations", "manufacturer_payment_allocations",
            "worker_payment_allocations");

    @Test
    @DisplayName("FR-PROC-14 FR-OUT-11 NFR-OBS-06 update or delete of ledger row is rejected by trigger")
    void updateOrDeleteOfLedgerRow_isRejectedByTrigger() throws Exception {
        seedEveryInsertOnlyTable();
        for (String table : INSERT_ONLY) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).as(table).isPositive();
            assertThatThrownBy(() -> jdbc.update("update " + table + " set id = id"))
                    .as(table).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("Rows of " + table + " are never changed or deleted");
            assertThatThrownBy(() -> jdbc.update("delete from " + table)).as(table)
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("never changed or deleted");
        }
        // an editable table is not affected
        assertThat(jdbc.update("update parties set name = name")).isPositive();
    }

    private void seedEveryInsertOnlyTable() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "500000.00");
        String cash = createAccount("Cash", "CASH", "50000.00");
        // procurement: receipt and return (movements, receipts, returns), a supplier payment (ledger, allocation)
        String supplier = data.supplier().id().toString();
        String purchase = createPurchase(java.util.UUID.fromString(supplier), "RAW_SILK", "100.000", "800.00");
        receive(purchase, "100.000", "100.000", "0.000").andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/purchases/" + purchase + "/returns", """
                {"returnDate":"%s","returnedWeightKg":5.000,"reason":"knots"}""".formatted(today())))
                .andExpect(status().isCreated());
        pay("SUPPLIER", supplier, "1000.00", "BANK_TRANSFER", bank);
        // a confirmed order paid by the customer; a transfer; an opening obligation (audited)
        String customer = data.customer().id().toString();
        confirmedOrder(java.util.UUID.fromString(customer), "RAW_SILK", "10.000", "1000.00");
        pay("CUSTOMER", customer, "2000.00", "UPI", bank);
        asOwner(postWithKey("/api/v1/finance/transfers", """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":100.00,"transferDate":"%s"}"""
                .formatted(bank, cash, today()))).andExpect(status().isCreated());
        asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"CUSTOMER","partyId":"%s","obligationDate":"%s","originalAmount":300.00,
                 "referenceNote":"old book"}""".formatted(customer, today()))).andExpect(status().isCreated());
        // a worker and a manufacturer paid for their work
        String worker = data.worker().id().toString();
        asOwner(postJson("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":2.00,"rate":100.00}"""
                .formatted(worker, today()))).andExpect(status().isCreated());
        pay("WORKER", worker, "100.00", "CASH", cash);
        String manufacturer = data.manufacturer().id().toString();
        String job = body(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":10.000,"ratePerKg":50.00}"""
                .formatted(manufacturer))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + job + "/issue", "{}")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":9.000,"wastageWeightKg":1.000}""".formatted(today())))
                .andExpect(status().isOk());
        pay("MANUFACTURER", manufacturer, "100.00", "CASH", cash);
    }

    private void pay(String purpose, String party, String amount, String method, String account) throws Exception {
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"%s","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                .formatted(purpose, party, amount, method, account))).andExpect(status().isCreated());
    }
}

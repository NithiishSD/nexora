package com.nexora.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

// The go-live import (deploy/import/README.md, 10-DEPLOYMENT section 12) done through the normal endpoints, then
// reconciled the way the owner signs it off: every figure the system reports equals the notebook figure entered, no
// opening record became a purchase, revenue or expense, and the reconciliation queries find nothing.
class GoLiveImportIntegrationTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-DATA-04 FR-INV-13 FR-PAY-22 opening data totals match reconciliation queries")
    void openingDataTotals_matchReconciliationQueries() throws Exception {
        LocalDate goLive = today();
        // 1. parties
        String c1 = party("Kumar Silks", "CUSTOMER", "");
        String c2 = party("Lakshmi Weavers", "CUSTOMER", "");
        String supplier = party("Murugan Yarns", "SUPPLIER", ",\"defaultCreditDays\":30");
        String manufacturer = party("Arun Warping", "MANUFACTURER", "");
        String worker = party("Selvi", "WORKER", "");
        String lender = party("Canara Bank", "LENDER", "");

        // 2. opening stock counted on go-live day, per product and internal location (stock in progress included)
        stock("RAW_SILK", "RAW_STOCK", "120.000", "counted on go-live day").andExpect(status().isCreated());
        stock("VUDA_WARP", "RAW_STOCK", "15.500", null).andExpect(status().isCreated());
        stock("KORA_WARP", "FINISHED_STOCK", "40.250", null).andExpect(status().isCreated());
        stock("SILK_WARP", "EXTERNAL_WIP", "30.000", "with Arun Warping, notebook job 14").andExpect(status().isCreated());
        stock("KORA_WARP", "INTERNAL_WIP", "10.000", "batch on the machine").andExpect(status().isCreated());
        // FR-INV-13 AC2: a second opening balance for the same product and location is refused
        stock("RAW_SILK", "RAW_STOCK", "1.000", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        // 3. accounts with their counted / statement balances
        String cash = createAccount("Cash", "CASH", "25000.00");
        String bank = createAccount("SBI Current", "BANK", "340000.00");

        // 4. opening receivables and payables, dated with the original documents
        String oldest = obligation("CUSTOMER", c1, goLive.minusDays(190), null, "25000.00", "ledger p.12");
        obligation("CUSTOMER", c1, goLive.minusDays(30), null, "5000.00", "ledger p.40");
        obligation("CUSTOMER", c2, goLive.minusDays(10), null, "12000.00", "ledger p.41");
        obligation("SUPPLIER", supplier, goLive.minusDays(20), goLive.plusDays(10), "40000.00", "bill 771");
        obligation("MANUFACTURER", manufacturer, goLive.minusDays(5), null, "7500.00", "job 14 balance");
        obligation("WORKER", worker, goLive.minusDays(3), null, "1200.00", "last week's wages");

        // 5. an existing loan (no cash moves: the money is already in the balances) and a chit with prior contributions
        asOwner(postJson("/api/v1/loans/existing", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":300000.00,"startDate":"%s",
                 "outstandingPrincipal":180000.00}""".formatted(lender, goLive.minusDays(400))))
                .andExpect(status().isCreated());
        asOwner(postJson("/api/v1/chits", """
                {"chitName":"Chit A","totalAmount":500000.00,"monthlyAmount":25000.00,"durationMonths":20,
                 "startDate":"%s","priorContributionsCount":6,"priorContributionsAmount":150000.00}"""
                .formatted(goLive.minusMonths(6)))).andExpect(status().isCreated());

        // ---- reconciliation: every reported figure equals the notebook figure ----
        asOwner(get("/api/v1/inventory/summary")).andExpect(jsonPath("$.rawSilkAvailableKg").value(120.000))
                .andExpect(jsonPath("$.vudaWarpKg").value(15.500))
                .andExpect(jsonPath("$.finishedKoraWarpKg").value(40.250))
                .andExpect(jsonPath("$.externalWipKg").value(30.000))
                .andExpect(jsonPath("$.internalWipKg").value(10.000));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(25000.00));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(340000.00));
        asOwner(get("/api/v1/customers/" + c1 + "/outstanding")).andExpect(jsonPath("$.totalOutstanding").value(30000.00));
        asOwner(get("/api/v1/customers/" + c2 + "/outstanding")).andExpect(jsonPath("$.totalOutstanding").value(12000.00));
        asOwner(get("/api/v1/suppliers/" + supplier + "/payables")).andExpect(jsonPath("$.totalPayable").value(40000.00));
        asOwner(get("/api/v1/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvailable").value(365000.00))
                .andExpect(jsonPath("$.customerReceivables").value(42000.00))
                .andExpect(jsonPath("$.supplierPayables").value(40000.00))
                .andExpect(jsonPath("$.manufacturerPayable").value(7500.00))
                .andExpect(jsonPath("$.workerPayable").value(1200.00))
                .andExpect(jsonPath("$.loanOutstandingPrincipal").value(180000.00));
        asOwner(get("/api/v1/chits")).andExpect(jsonPath("$.content[0].totalContributed").value(150000.00))
                .andExpect(jsonPath("$.content[0].remainingExpectedContributions").value(14));

        // opening records are not purchases, sales, revenue or expenses, and move no money beyond the two openings
        assertThat(count("select count(*) from purchases")).isZero();
        assertThat(count("select count(*) from customer_orders")).isZero();
        assertThat(count("select count(*) from expenses")).isZero();
        assertThat(count("select count(*) from financial_transactions where transaction_type <> 'OPENING_BALANCE'"))
                .isZero();
        assertThat(count("select count(*) from material_movements where movement_type <> 'OPENING_BALANCE'")).isZero();
        asOwner(get("/api/v1/reports/sales?from=" + goLive.minusYears(1) + "&to=" + goLive))
                .andExpect(jsonPath("$.totals.revenueDelivered").value(0.0));
        // the reconciliation queries of the restore drill and the parallel run find nothing
        for (String query : reconciliationQueries()) {
            assertThat(jdbc.queryForList(query)).as(query).isEmpty();
        }

        // FR-PAY-22 AC1: the customer's first payment settles the oldest opening receivable first
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":10000.00,"paymentMethod":"UPI","financialAccountId":"%s"}"""
                .formatted(c1, bank))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations[0].targetId").value(oldest));
        asOwner(get("/api/v1/customers/" + c1 + "/outstanding")).andExpect(jsonPath("$.totalOutstanding").value(20000.00));
    }

    private String party(String name, String role, String extra) throws Exception {
        return body(asOwner(postJson("/api/v1/parties", "{\"name\":\"" + name + "\",\"roles\":[\"" + role + "\"]"
                + extra + "}")).andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions stock(String product, String location, String kg, String notes) throws Exception {
        return asOwner(postJson("/api/v1/inventory/opening-balances", """
                {"productType":"%s","location":"%s","quantityKg":%s,"movementDate":"%s"%s}"""
                .formatted(product, location, kg, today(), notes == null ? "" : ",\"notes\":\"" + notes + "\"")));
    }

    private String obligation(String purpose, String party, LocalDate date, LocalDate due, String amount, String note)
            throws Exception {
        return body(asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"%s","partyId":"%s","obligationDate":"%s"%s,"originalAmount":%s,"referenceNote":"%s"}"""
                .formatted(purpose, party, date, due == null ? "" : ",\"dueDate\":\"" + due + "\"", amount, note)))
                .andExpect(status().isCreated())).get("id").asString();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // deploy/scripts/reconciliation.sql, one SELECT per statement, comments dropped
    private static List<String> reconciliationQueries() throws Exception {
        String sql = Files.readString(Path.of("../deploy/scripts/reconciliation.sql")).replaceAll("(?m)^--.*$", "");
        return Arrays.stream(sql.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}

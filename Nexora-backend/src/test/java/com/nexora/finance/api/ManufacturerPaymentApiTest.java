package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

class ManufacturerPaymentApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-09 payment allocates oldest job first")
    void payment_allocatesOldestJobFirst() throws Exception {
        data.rawSilkInStock("500.000");
        UUID manufacturer = data.manufacturer().id();
        String cash = createAccount("Cash", "CASH", "100000.00");
        String j2 = receivedJob(manufacturer, "100.000", 1);              // payable 12000, issued yesterday
        String j1 = receivedJob(manufacturer, "150.000", 2);              // payable 18000, issued the day before

        // AC1: 20000 -> J1 (oldest issue) settled, J2 gets 2000; the cash goes OUT through the ledger
        String id = body(pay(manufacturer, "20000.00", cash).andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PAID"))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].targetType").value("OUTSOURCING_JOB"))
                .andExpect(jsonPath("$.allocations[0].targetId").value(j1))
                .andExpect(jsonPath("$.allocations[0].amount").value(18000.00))
                .andExpect(jsonPath("$.allocations[1].targetId").value(j2))
                .andExpect(jsonPath("$.allocations[1].amount").value(2000.00))).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(80000.00));
        asOwner(get("/api/v1/finance/transactions?referenceId=" + id))
                .andExpect(jsonPath("$.content[0].transactionType").value("MANUFACTURER_PAYMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"));
        asOwner(get("/api/v1/outsourcing-jobs/" + j1)).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        asOwner(get("/api/v1/outsourcing-jobs/" + j2)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(10000.00));

        // AC2: 10000 still payable -> 15000 is refused, nothing stored, no money moves
        pay(manufacturer, "15000.00", cash).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(80000.00));
        // an issued job without receipts is not owed yet
        String notYet = body(asOwner(postJson("/api/v1/outsourcing-jobs", job(manufacturer, "10.000"))))
                .get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + notYet + "/issue", "{}")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"MANUFACTURER","partyId":"%s","amount":100.00,"paymentMethod":"CASH",
                 "financialAccountId":"%s","allocations":[{"targetType":"OUTSOURCING_JOB","targetId":"%s",
                 "amount":100.00}]}""".formatted(manufacturer, cash, notYet)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ALLOCATION"));
        // reversing the payment releases both allocations
        asOwner(postWithKey("/api/v1/payments/" + id + "/reverse", "{\"reason\":\"wrong party\"}"))
                .andExpect(status().isOk());
        asOwner(get("/api/v1/outsourcing-jobs/" + j1)).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(100000.00));
    }

    @Test
    @DisplayName("FR-OUT-05 FR-PAY-20 payable is received kg times historical rate")
    void payable_isReceivedKgTimesHistoricalRate() throws Exception {
        data.rawSilkInStock("300.000");
        UUID manufacturer = data.manufacturer().id();
        String cash = createAccount("Cash", "CASH", "50000.00");
        String job = receivedJob(manufacturer, "150.000", 0);               // 200 issued, 150 received x 120.00
        asOwner(postJson("/api/v1/opening-obligations", """
                {"purpose":"MANUFACTURER","partyId":"%s","obligationDate":"%s","originalAmount":5000.00,
                 "referenceNote":"Old dues"}""".formatted(manufacturer, today().minusYears(1))))
                .andExpect(status().isCreated());
        // the opening payable is older, so 15000 settles it first and then 10000 of the job
        pay(manufacturer, "15000.00", cash).andExpect(status().isCreated());

        // AC1: payable 18000, 10000 paid -> 8000 outstanding; the settled opening is no longer listed
        asOwner(get("/api/v1/manufacturers/" + manufacturer + "/payable")).andExpect(status().isOk())
                .andExpect(jsonPath("$.manufacturer.id").value(manufacturer.toString()))
                .andExpect(jsonPath("$.totalPayable").value(8000.00))
                .andExpect(jsonPath("$.jobs.length()").value(1))
                .andExpect(jsonPath("$.jobs[0].jobId").value(job))
                .andExpect(jsonPath("$.jobs[0].receivedKg").value(150.000))
                .andExpect(jsonPath("$.jobs[0].ratePerKg").value(120.00))
                .andExpect(jsonPath("$.jobs[0].payable").value(18000.00))
                .andExpect(jsonPath("$.jobs[0].allocated").value(10000.00))
                .andExpect(jsonPath("$.jobs[0].outstanding").value(8000.00))
                .andExpect(jsonPath("$.jobs[0].paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.openingObligations.length()").value(0));
        // the rest of the warp arrives: payable grows by received kg x the same rate, nothing is recalculated
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":45.000,"wastageWeightKg":5.000}""".formatted(today())))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        asOwner(get("/api/v1/manufacturers/" + manufacturer + "/payable"))
                .andExpect(jsonPath("$.jobs[0].payable").value(23400.00))
                .andExpect(jsonPath("$.totalPayable").value(13400.00));
        // AC2: not a manufacturer 422; unknown 404
        asOwner(get("/api/v1/manufacturers/" + data.supplier().id() + "/payable"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        asOwner(get("/api/v1/manufacturers/" + UUID.randomUUID() + "/payable")).andExpect(status().isNotFound());
    }

    // A KORA_WARP job of 200 kg at 120.00 issued daysAgo days ago with this much received (no wastage).
    private String receivedJob(UUID manufacturer, String receivedKg, int daysAgo) throws Exception {
        String id = body(asOwner(postJson("/api/v1/outsourcing-jobs", job(manufacturer, "200.000")))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/issue", "{\"issueDate\":\"" + today().minusDays(daysAgo)
                + "\"}")).andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + id + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":%s,"wastageWeightKg":0.000}""".formatted(today(), receivedKg)))
                .andExpect(status().isOk());
        return id;
    }

    private static String job(UUID manufacturer, String kg) {
        return """
                {"manufacturerId":"%s","productType":"KORA_WARP","issuedWeightKg":%s,"ratePerKg":120.00}"""
                .formatted(manufacturer, kg);
    }

    private ResultActions pay(UUID manufacturer, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"MANUFACTURER","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(manufacturer, amount, account)));
    }
}

package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.parties.domain.PartyRole;
import com.nexora.support.AbstractApiTest;

class OutsourcingReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-11 FR-OUT-12 report per manufacturer and job")
    void report_perManufacturerAndJob() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        data.rawSilkInStock("300.000");
        String arun = data.party("Arun", List.of(PartyRole.MANUFACTURER), null, null).id().toString();
        String bhavani = data.party("Bhavani", List.of(PartyRole.MANUFACTURER), null, null).id().toString();
        // Arun: 200 issued at 50.00, 150 received with 10 lost -> 40 still outside, payable 7500, 5000 paid
        String job = issuedJob(arun, "200.000", "50.00");
        asOwner(postWithKey("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":150.000,"wastageWeightKg":10.000}""".formatted(today())))
                .andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"MANUFACTURER","partyId":"%s","amount":5000.00,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s"}""".formatted(arun, bank))).andExpect(status().isCreated());
        // Bhavani: 30 issued, nothing back yet; a job never issued is not in the report
        issuedJob(bhavani, "30.000", "60.00");
        asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":10.000,"ratePerKg":60.00}"""
                .formatted(bhavani))).andExpect(status().isCreated());

        String period = "?from=" + today() + "&to=" + today();
        // AC1: still outside = issued - received - wastage; FR-OUT-12 AC1 (first release): per manufacturer and job
        asOwner(get("/api/v1/reports/outsourcing" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.manufacturers.length()").value(2))
                .andExpect(jsonPath("$.manufacturers[0].manufacturer.id").value(arun))
                .andExpect(jsonPath("$.manufacturers[0].jobs.length()").value(1))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].jobId").value(job))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].issuedKg").value(200.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].receivedKg").value(150.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].wastageKg").value(10.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].outsideKg").value(40.000))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].payable").value(7500.00))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].paid").value(5000.00))
                .andExpect(jsonPath("$.manufacturers[0].jobs[0].pending").value(2500.00))
                .andExpect(jsonPath("$.manufacturers[1].jobs.length()").value(1))
                .andExpect(jsonPath("$.manufacturers[1].totals.outsideKg").value(30.000))
                .andExpect(jsonPath("$.totals.outsideKg").value(70.000))
                .andExpect(jsonPath("$.totals.pending").value(2500.00));
        // still outside matches the external WIP balance
        asOwner(get("/api/v1/inventory/summary")).andExpect(jsonPath("$.externalWipKg").value(70.000));
        asOwner(get("/api/v1/reports/outsourcing" + period + "&manufacturerId=" + bhavani))
                .andExpect(jsonPath("$.manufacturers.length()").value(1));
        // FR-OUT-12 AC2: a period with no jobs is 200 with empty rows
        asOwner(get("/api/v1/reports/outsourcing?from=" + today().minusDays(30) + "&to=" + today().minusDays(20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.manufacturers.length()").value(0));

        // AC2: an unknown manufacturer is 404
        asOwner(get("/api/v1/reports/outsourcing" + period + "&manufacturerId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private String issuedJob(String manufacturer, String kg, String rate) throws Exception {
        String id = body(asOwner(postJson("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":%s,"ratePerKg":%s}"""
                .formatted(manufacturer, kg, rate))).andExpect(status().isCreated())).get("id").asString();
        asOwner(postJson("/api/v1/outsourcing-jobs/" + id + "/issue", "{}")).andExpect(status().isOk());
        return id;
    }
}

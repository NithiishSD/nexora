package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.parties.domain.PartyRole;
import com.nexora.support.AbstractApiTest;

class WorkerPayableReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-13 FR-WORK-10 report splits rolling and warping")
    void report_splitsRollingAndWarping() throws Exception {
        String cash = createAccount("Cash", "CASH", "10000.00");
        String anitha = data.party("Anitha", List.of(PartyRole.WORKER), null, null).id().toString();
        String bhuvana = data.party("Bhuvana", List.of(PartyRole.WORKER), null, null).id().toString();
        // Anitha this week: rolling 8 h at 100 = 800, warping 50 kg at 5 = 250; paid 500; a reversed record is out
        work(anitha, today().minusDays(2), "ROLLING", "\"hours\":8.00", "100.00");
        work(anitha, today().minusDays(1), "WARPING", "\"quantityKg\":50.000", "5.00");
        String wrong = work(anitha, today(), "ROLLING", "\"hours\":2.00", "100.00");
        asOwner(postWithKey("/api/v1/work-records/" + wrong + "/reverse", "{\"reason\":\"wrong worker\"}"))
                .andExpect(status().isOk());
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"WORKER","partyId":"%s","amount":500.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(anitha, cash))).andExpect(status().isCreated());
        // Bhuvana: 4 h rolling this week (unpaid), and older work outside the week
        work(bhuvana, today(), "ROLLING", "\"hours\":4.00", "100.00");
        work(bhuvana, today().minusDays(20), "ROLLING", "\"hours\":4.00", "100.00");

        // AC1: earned, paid and pending per worker for the week, rolling and warping apart
        String week = "?from=" + today().minusDays(6) + "&to=" + today();
        asOwner(get("/api/v1/reports/worker-payable" + week)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].worker.id").value(anitha))
                .andExpect(jsonPath("$[0].rollingEarned").value(800.00))
                .andExpect(jsonPath("$[0].warpingEarned").value(250.00))
                .andExpect(jsonPath("$[0].earned").value(1050.00))
                .andExpect(jsonPath("$[0].paid").value(500.00))
                .andExpect(jsonPath("$[0].pending").value(550.00))
                .andExpect(jsonPath("$[1].worker.id").value(bhuvana))
                .andExpect(jsonPath("$[1].earned").value(400.00))
                .andExpect(jsonPath("$[1].pending").value(400.00));
        // the same figures as the worker's payable for that range
        asOwner(get("/api/v1/workers/" + anitha + "/payable" + week)).andExpect(jsonPath("$.workValue").value(1050.00))
                .andExpect(jsonPath("$.paid").value(500.00)).andExpect(jsonPath("$.pending").value(550.00));
        asOwner(get("/api/v1/reports/worker-payable" + week + "&workerId=" + bhuvana))
                .andExpect(jsonPath("$.length()").value(1));

        // AC2: from after to is 400
        asOwner(get("/api/v1/reports/worker-payable?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-WORK-10 E-WORK-05 worker efficiency calculates kg per hour")
    void workerEfficiency_calculatesKgPerHour() throws Exception {
        String anitha = data.party("Anitha", List.of(PartyRole.WORKER), null, null).id().toString();
        String bhuvana = data.party("Bhuvana", List.of(PartyRole.WORKER), null, null).id().toString();

        // Anitha: 8 hours rolling, 40 kg warping -> 40 / 8 = 5.00 kg/hr
        work(anitha, today().minusDays(2), "ROLLING", "\"hours\":8.00", "100.00");
        work(anitha, today().minusDays(1), "WARPING", "\"quantityKg\":40.000", "5.00");

        // Bhuvana: only 10 hours rolling -> 0 kg -> 0.00 kg/hr
        work(bhuvana, today().minusDays(1), "ROLLING", "\"hours\":10.00", "100.00");

        // Reversed work should not count
        String wrong = work(anitha, today(), "WARPING", "\"quantityKg\":100.000", "5.00");
        asOwner(postWithKey("/api/v1/work-records/" + wrong + "/reverse", "{\"reason\":\"mistake\"}"))
                .andExpect(status().isOk());

        String range = "?from=" + today().minusDays(6) + "&to=" + today();
        asOwner(get("/api/v1/reports/worker-efficiency" + range)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].worker.id").value(anitha))
                .andExpect(jsonPath("$[0].hours").value(8.00))
                .andExpect(jsonPath("$[0].kg").value(40.000))
                .andExpect(jsonPath("$[0].kgPerHour").value(5.00))
                .andExpect(jsonPath("$[1].worker.id").value(bhuvana))
                .andExpect(jsonPath("$[1].hours").value(10.00))
                .andExpect(jsonPath("$[1].kg").value(0.000))
                .andExpect(jsonPath("$[1].kgPerHour").value(0.00));
    }

    @Test
    @DisplayName("FR-WORK-10 E-WORK-05 worker efficiency invalid date range returns 400")
    void workerEfficiency_invalidDateRange_returns400() throws Exception {
        asOwner(get("/api/v1/reports/worker-efficiency?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String work(String worker, LocalDate date, String type, String measure, String rate) throws Exception {
        return body(asOwner(postJson("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"%s",%s,"rate":%s}"""
                .formatted(worker, date, type, measure, rate))).andExpect(status().isCreated())).get("id").asString();
    }
}

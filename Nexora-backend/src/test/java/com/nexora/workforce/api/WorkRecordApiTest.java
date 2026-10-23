package com.nexora.workforce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class WorkRecordApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-WORK-01 FR-WORK-03 FR-PARTY-14 record copies rate from worker defaults")
    void record_copiesRateFromWorkerDefaults() throws Exception {
        UUID worker = data.worker().id();                                  // rolling 100.00 per hour, no warping rate

        // FR-PARTY-14 AC1 / FR-WORK-01 AC1: rate omitted -> 100.00 copied, 8 h -> 800.00, UNPAID; client amount and
        // status ignored (FR-WORK-04 AC2)
        String id = body(record("""
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":8.00,"amount":1.00,"status":"REVERSED"}"""
                .formatted(worker, today())).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/work-records/")))
                .andExpect(jsonPath("$.worker.id").value(worker.toString()))
                .andExpect(jsonPath("$.rate").value(100.00))
                .andExpect(jsonPath("$.amount").value(800.00))
                .andExpect(jsonPath("$.pendingAmount").value(800.00))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.quantityKg").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.productionBatch").value(Matchers.nullValue()))).get("id").asString();
        // AC5: no warping rate on the worker and none sent -> 400 on rate; with a rate it works (25.5 x 40 = 1020)
        record(warping(worker, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rate"));
        record(warping(worker, "40.00")).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(1020.00));
        // FR-WORK-02: the other measure 400
        record("""
                {"workerId":"%s","workDate":"%s","workType":"WARPING","quantityKg":1.000,"hours":1.00,"rate":1.00}"""
                .formatted(worker, today())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hours"));

        // FR-WORK-03 AC1: the worker's default changes to 110.00; the old record keeps 100.00 / 800.00
        asOwner(patch("/api/v1/parties/" + worker).contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultRollingRatePerHour\":110.00}")).andExpect(status().isOk());
        asOwner(get("/api/v1/work-records/" + id)).andExpect(jsonPath("$.rate").value(100.00))
                .andExpect(jsonPath("$.amount").value(800.00));
        record("""
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":1.00}""".formatted(worker, today()))
                .andExpect(jsonPath("$.rate").value(110.00));
        // FR-WORK-03 AC2 / FR-WORK-07: never edited or deleted
        asOwner(patch("/api/v1/work-records/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":1.00}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(put("/api/v1/work-records/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/work-records/" + id)).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/work-records/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-WORK-01 record non worker returns422 invalid party role")
    void record_nonWorker_returns422InvalidPartyRole() throws Exception {
        record(warping(data.manufacturer().id(), "40.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        record(warping(UUID.randomUUID(), "40.00")).andExpect(status().isNotFound());
        UUID worker = data.worker().id();
        asOwner(postJson("/api/v1/parties/" + worker + "/deactivate", "")).andExpect(status().isOk());
        record(warping(worker, "40.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARTY_INACTIVE"));
        assertThat(jdbc.queryForObject("select count(*) from work_records", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-WORK-08 record linked to production batch")
    void record_linkedToProductionBatch() throws Exception {
        UUID worker = data.worker().id();
        String batch = body(asOwner(postJson("/api/v1/production-batches", """
                {"productType":"KORA_WARP","productionDate":"%s","inputWeightKg":50.000}""".formatted(today()))))
                .get("id").asString();

        // AC1: the record shows the batch and the batch lists the record
        String id = body(record("""
                {"workerId":"%s","workDate":"%s","workType":"WARPING","quantityKg":25.500,"rate":40.00,
                 "productionBatchId":"%s"}""".formatted(worker, today(), batch)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.productionBatch.id").value(batch))
                .andExpect(jsonPath("$.productionBatch.batchNumber").value(Matchers.startsWith("BAT-"))))
                .get("id").asString();
        asOwner(get("/api/v1/production-batches/" + batch)).andExpect(jsonPath("$.workRecords.length()").value(1))
                .andExpect(jsonPath("$.workRecords[0].id").value(id))
                .andExpect(jsonPath("$.workRecords[0].workType").value("WARPING"))
                .andExpect(jsonPath("$.workRecords[0].amount").value(1020.00))
                .andExpect(jsonPath("$.workRecords[0].workerName").value(Matchers.startsWith("Worker")));
        asOwner(get("/api/v1/production-batches")).andExpect(jsonPath("$.content[0].workRecords").value(Matchers.nullValue()));
        // AC2: unknown batch 404, nothing stored
        record("""
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":1.00,"productionBatchId":"%s"}"""
                .formatted(worker, today(), UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from work_records", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-WORK-09 record without permission returns403")
    void record_withoutPermission_returns403() throws Exception {
        UUID worker = data.worker().id();
        // AC1: Staff has no WORK_RECORD_MANAGE
        mockMvc.perform(postJson("/api/v1/work-records", warping(worker, "40.00"))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(postJson("/api/v1/work-records", warping(worker, "40.00")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from work_records", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-WORK-05 list filtered by worker type and date")
    void list_filteredByWorkerTypeAndDate() throws Exception {
        UUID selvi = data.worker().id();
        UUID kumar = data.worker().id();
        work(selvi, today().minusDays(9), "ROLLING", "8.00", "100.00");      // last week: 800
        work(selvi, today().minusDays(2), "ROLLING", "6.00", "100.00");      // this week: 600
        work(selvi, today().minusDays(1), "WARPING", "25.500", "40.00");     // this week: 1020
        work(kumar, today().minusDays(1), "ROLLING", "5.00", "120.00");      // other worker: 600

        // AC1: one worker's week -> only those rows, and the total of every matching row
        String week = "&from=" + today().minusDays(6) + "&to=" + today();
        asOwner(get("/api/v1/work-records?workerId=" + selvi + week)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[0].workDate").value(today().minusDays(1).toString()))
                .andExpect(jsonPath("$.totalAmount").value(1620.00));
        asOwner(get("/api/v1/work-records?workerId=" + selvi + "&workType=ROLLING"))
                .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.totalAmount").value(1400.00));
        asOwner(get("/api/v1/work-records?size=1" + week)).andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.content.length()").value(1))
                .andExpect(jsonPath("$.totalAmount").value(2220.00));
        asOwner(get("/api/v1/work-records?paymentStatus=PAID")).andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0));
        asOwner(get("/api/v1/work-records?paymentStatus=UNPAID&status=RECORDED"))
                .andExpect(jsonPath("$.page.totalElements").value(4));
        // AC2: from after to 400; unknown sort 400
        asOwner(get("/api/v1/work-records?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest());
        asOwner(get("/api/v1/work-records?sort=notes")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-WORK-06 reverse with allocations is rejected")
    void reverse_withAllocations_isRejected() throws Exception {
        UUID worker = data.worker().id();
        String cash = createAccount("Cash", "CASH", "10000.00");
        String paid = work(worker, today().minusDays(2), "ROLLING", "8.00", "100.00");
        String mistake = work(worker, today().minusDays(1), "ROLLING", "2.00", "100.00");
        String payment = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"WORKER","partyId":"%s","amount":500.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(worker, cash))).andExpect(status().isCreated())).get("id").asString();

        // AC3: blank reason 400; AC2: a record with an allocation 409
        asOwner(postJson("/api/v1/work-records/" + mistake + "/reverse", "{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        asOwner(postJson("/api/v1/work-records/" + paid + "/reverse", "{\"reason\":\"wrong worker\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // AC1: the unpaid record is reversed, stays visible, leaves the payable
        asOwner(postJson("/api/v1/work-records/" + mistake + "/reverse", "{\"reason\":\"Recorded twice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("Recorded twice"))
                .andExpect(jsonPath("$.reversedAt").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.pendingAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'WORK_RECORD_REVERSED'",
                Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/workers/" + worker + "/payable")).andExpect(jsonPath("$.workValue").value(800.00))
                .andExpect(jsonPath("$.pending").value(300.00));
        asOwner(postJson("/api/v1/work-records/" + mistake + "/reverse", "{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());
        // once the payment is reversed, the first record can be reversed too
        asOwner(postWithKey("/api/v1/payments/" + payment + "/reverse", "{\"reason\":\"wrong worker\"}"))
                .andExpect(status().isOk());
        asOwner(postJson("/api/v1/work-records/" + paid + "/reverse", "{\"reason\":\"wrong worker\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-WORK-07 put or delete is not supported")
    void putOrDelete_isNotSupported() throws Exception {
        String id = work(data.worker().id(), today(), "ROLLING", "8.00", "100.00");

        // AC1: no PUT, PATCH or DELETE on a record or the collection; the record is unchanged
        for (String path : java.util.List.of("/api/v1/work-records/" + id, "/api/v1/work-records")) {
            asOwner(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1.00}"))
                    .andExpect(status().isMethodNotAllowed());
            asOwner(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1.00}"))
                    .andExpect(status().isMethodNotAllowed());
            asOwner(delete(path)).andExpect(status().isMethodNotAllowed());
        }
        asOwner(get("/api/v1/work-records/" + id)).andExpect(jsonPath("$.hours").value(8.00))
                .andExpect(jsonPath("$.amount").value(800.00)).andExpect(jsonPath("$.status").value("RECORDED"));
    }

    private String work(UUID worker, java.time.LocalDate date, String type, String units, String rate)
            throws Exception {
        String measure = type.equals("ROLLING") ? "\"hours\":" + units : "\"quantityKg\":" + units;
        return body(record("""
                {"workerId":"%s","workDate":"%s","workType":"%s",%s,"rate":%s}""".formatted(worker, date, type, measure,
                rate)).andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions record(String body) throws Exception {
        return asOwner(postJson("/api/v1/work-records", body));
    }

    private String warping(UUID worker, String rate) {
        return """
                {"workerId":"%s","workDate":"%s","workType":"WARPING","quantityKg":25.500%s}""".formatted(worker, today(),
                rate == null ? "" : ",\"rate\":" + rate);
    }
}

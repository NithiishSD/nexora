package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

class WorkerPaymentApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-10 payment allocates oldest work first")
    void payment_allocatesOldestWorkFirst() throws Exception {
        UUID worker = data.worker().id();
        String cash = createAccount("Cash", "CASH", "20000.00");
        String w2 = work(worker, today().minusDays(2), "25.00", "100.00");   // 2500
        String w1 = work(worker, today().minusDays(3), "30.00", "100.00");   // 3000, oldest
        String w3 = work(worker, today().minusDays(1), "32.00", "100.00");   // 3200

        // AC1: 5500 -> the first two in full, by work date; one WORKER_PAYMENT OUT row
        String id = body(pay(worker, "5500.00", cash).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].targetType").value("WORK_RECORD"))
                .andExpect(jsonPath("$.allocations[0].targetId").value(w1))
                .andExpect(jsonPath("$.allocations[0].amount").value(3000.00))
                .andExpect(jsonPath("$.allocations[1].targetId").value(w2))
                .andExpect(jsonPath("$.allocations[1].amount").value(2500.00))).get("id").asString();
        asOwner(get("/api/v1/finance/transactions?referenceId=" + id))
                .andExpect(jsonPath("$.content[0].transactionType").value("WORKER_PAYMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(14500.00));
        asOwner(get("/api/v1/work-records/" + w3)).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        // AC2: 3200 left -> 3500 refused, nothing stored
        pay(worker, "3500.00", cash).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
        // AC3: a worker with only a reversed record owes nothing: any payment is refused
        UUID other = data.worker().id();
        String reversed = work(other, today(), "1.00", "100.00");
        asOwner(postJson("/api/v1/work-records/" + reversed + "/reverse", "{\"reason\":\"mistake\"}"))
                .andExpect(status().isOk());
        pay(other, "100.00", cash).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        asOwner(get("/api/v1/workers/" + other + "/payable")).andExpect(jsonPath("$.pending").value(0));
    }

    @Test
    @DisplayName("FR-WORK-04 status derived from allocations")
    void status_derivedFromAllocations() throws Exception {
        UUID worker = data.worker().id();
        String cash = createAccount("Cash", "CASH", "5000.00");
        String id = work(worker, today(), "8.00", "100.00");                 // 800

        // AC1: 500 of 800 -> PARTIALLY_PAID, 300 pending
        pay(worker, "500.00", cash).andExpect(status().isCreated());
        asOwner(get("/api/v1/work-records/" + id)).andExpect(jsonPath("$.allocatedAmount").value(500.00))
                .andExpect(jsonPath("$.pendingAmount").value(300.00))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        asOwner(get("/api/v1/work-records?paymentStatus=PARTIALLY_PAID"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        // the rest -> PAID; a reversed payment counts no longer
        String last = body(pay(worker, "300.00", cash)).get("id").asString();
        asOwner(get("/api/v1/work-records/" + id)).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.pendingAmount").value(0));
        asOwner(postWithKey("/api/v1/payments/" + last + "/reverse", "{\"reason\":\"paid twice\"}"))
                .andExpect(status().isOk());
        asOwner(get("/api/v1/work-records/" + id)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        // explicit allocation to another worker's record is refused
        String others = work(data.worker().id(), today(), "1.00", "100.00");
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"WORKER","partyId":"%s","amount":100.00,"paymentMethod":"CASH","financialAccountId":"%s",
                 "allocations":[{"targetType":"WORK_RECORD","targetId":"%s","amount":100.00}]}"""
                .formatted(worker, cash, others))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ALLOCATION"));
    }

    @Test
    @DisplayName("FR-PAY-21 payable for payroll week")
    void payable_forPayrollWeek() throws Exception {
        UUID worker = data.worker().id();
        String cash = createAccount("Cash", "CASH", "20000.00");
        LocalDate monday = today().minusDays(6);
        work(worker, monday.minusDays(3), "10.00", "100.00");                 // before the week: 1000
        work(worker, monday, "30.00", "100.00");                              // 3000
        work(worker, monday.plusDays(1), "25.00", "100.00");                  // 2500
        work(worker, monday.plusDays(2), "32.00", "100.00");                  // 3200 -> week 8700
        pay(worker, "6500.00", cash).andExpect(status().isCreated());         // 1000 old + 5500 of the week

        // AC1: the week: 8700 of work, 5500 paid, 3200 pending
        asOwner(get("/api/v1/workers/" + worker + "/payable?from=" + monday + "&to=" + today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worker.id").value(worker.toString()))
                .andExpect(jsonPath("$.workValue").value(8700.00))
                .andExpect(jsonPath("$.paid").value(5500.00))
                .andExpect(jsonPath("$.pending").value(3200.00))
                .andExpect(jsonPath("$.records.length()").value(3))
                .andExpect(jsonPath("$.records[2].paymentStatus").value("UNPAID"));
        asOwner(get("/api/v1/workers/" + worker + "/payable")).andExpect(jsonPath("$.workValue").value(9700.00))
                .andExpect(jsonPath("$.pending").value(3200.00));
        // AC2: from after to 400; not a worker 422; unknown 404
        asOwner(get("/api/v1/workers/" + worker + "/payable?from=" + today() + "&to=" + monday))
                .andExpect(status().isBadRequest());
        asOwner(get("/api/v1/workers/" + data.supplier().id() + "/payable")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        asOwner(get("/api/v1/workers/" + UUID.randomUUID() + "/payable")).andExpect(status().isNotFound());
    }

    private String work(UUID worker, LocalDate date, String hours, String rate) throws Exception {
        return body(asOwner(postJson("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":%s,"rate":%s}""".formatted(worker, date,
                hours, rate))).andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions pay(UUID worker, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"WORKER","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(worker, amount, account)));
    }
}

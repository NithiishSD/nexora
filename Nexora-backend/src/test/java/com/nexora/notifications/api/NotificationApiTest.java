package com.nexora.notifications.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.nexora.notifications.NotificationTestSupport;
import com.nexora.support.AuthTokens;

class NotificationApiTest extends NotificationTestSupport {

    @Test
    @DisplayName("FR-NTF-05 list filtered by status and type")
    void list_filteredByStatusAndType() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String supplier = data.supplier().id().toString();
        String overdue = duePurchase(supplier, today().minusDays(10), 7, "100.00");
        duePurchase(supplier, today(), 0, "100.00");
        String other = data.supplier().id().toString();
        duePurchase(other, today(), 3, "100.00");
        job.run(today());
        // the overdue one gets paid: RESOLVED; the other two stay PENDING
        String paid = jdbc.queryForObject("select id::text from notifications where reference_id = ?::uuid",
                String.class, overdue);
        paySupplier(supplier, "1000.00", bank);

        // AC1: only pending ones, newest first, with the supplier and the purchase number
        asOwner(get("/api/v1/notifications?status=PENDING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].notificationType").value("SUPPLIER_DUE"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].party.id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].referenceType").value("PURCHASE"))
                .andExpect(jsonPath("$.content[0].referenceNumber").isNotEmpty());
        asOwner(get("/api/v1/notifications?status=RESOLVED&type=SUPPLIER_DUE"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(paid))
                .andExpect(jsonPath("$.content[0].kind").value("OVERDUE"));
        asOwner(get("/api/v1/notifications?kind=DUE_SOON")).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].party.id").value(other));
        asOwner(get("/api/v1/notifications?from=" + today() + "&to=" + today()))
                .andExpect(jsonPath("$.totalElements").value(3));
        asOwner(get("/api/v1/notifications?from=" + today().plusDays(1) + "&to=" + today().plusDays(2)))
                .andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/notifications?type=LOW_STOCK")).andExpect(jsonPath("$.totalElements").value(0));

        // errors: unknown status 400 MALFORMED_REQUEST, unknown sort 400 VALIDATION_FAILED; AC2: STAFF 403
        asOwner(get("/api/v1/notifications?status=LOST")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        asOwner(get("/api/v1/notifications?sort=message")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.staff()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-NTF-08 FR-NTF-09 list filtered by loan and chit due type")
    void list_filteredByLoanAndChitType() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String lender = data.lender().id().toString();

        String loanId = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":50000.00,"startDate":"%s","dueDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(lender, today(), today(), bank)))).get("id").asString();
        loanJob.run(today());

        asOwner(postWithKey("/api/v1/chits", """
                {"chitName":"Gold Scheme","totalAmount":100000.00,"monthlyAmount":5000.00,
                 "durationMonths":20,"startDate":"%s"}""".formatted(today()))).andExpect(status().isCreated());
        chitJob.run(today());

        asOwner(get("/api/v1/notifications?type=LOAN_DUE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notificationType").value("LOAN_DUE"))
                .andExpect(jsonPath("$.content[0].referenceType").value("LOAN"))
                .andExpect(jsonPath("$.content[0].referenceId").value(loanId))
                .andExpect(jsonPath("$.content[0].kind").value("DUE_TODAY"));

        asOwner(get("/api/v1/notifications?type=CHIT_DUE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notificationType").value("CHIT_DUE"))
                .andExpect(jsonPath("$.content[0].referenceType").value("CHIT"))
                .andExpect(jsonPath("$.content[0].kind").value("DUE_TODAY"));
    }

    @Test
    @DisplayName("E-NTF-04 retry resets failed notification to pending")
    void retry_failedNotification_resetsToPending() throws Exception {
        String supplier = data.supplier().id().toString();
        duePurchase(supplier, today(), 0, "100.00");
        job.run(today());

        String id = jdbc.queryForObject("select id::text from notifications where notification_type = 'SUPPLIER_DUE'", String.class);

        // Cannot retry while PENDING -> 409 INVALID_STATE_TRANSITION
        asOwner(postJson("/api/v1/notifications/" + id + "/retry", "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // Simulate notification failure after dispatch attempts
        jdbc.update("update notifications set status = 'FAILED', attempts = 3, last_error = 'Timeout' where id = ?::uuid",
                java.util.UUID.fromString(id));

        // Retry as owner -> 200 OK with PENDING status
        asOwner(postJson("/api/v1/notifications/" + id + "/retry", "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));

        // Verify in DB
        String status = jdbc.queryForObject("select status from notifications where id = ?::uuid", String.class, java.util.UUID.fromString(id));
        Integer attempts = jdbc.queryForObject("select attempts from notifications where id = ?::uuid", Integer.class, java.util.UUID.fromString(id));
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(attempts).isEqualTo(0);

        // Unknown id -> 404
        asOwner(postJson("/api/v1/notifications/" + java.util.UUID.randomUUID() + "/retry", "{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}

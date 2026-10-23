package com.nexora.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nexora.notifications.NotificationTestSupport;

class LoanDueReminderJobIntegrationTest extends NotificationTestSupport {

    @Autowired
    private LoanDueReminderJob loanDueJob;

    @Test
    @DisplayName("FR-NTF-08 job creates loan due notifications with dedupe key and prevents duplicate on same day")
    void job_createsLoanDueNotifications_withDedupe() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        UUID lender = data.lender().id();

        // Create an active loan due in 3 days (DUE_SOON window)
        String loanId = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":100000.00,"startDate":"%s","dueDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(lender, today(), today().plusDays(3), bank))).andExpect(status().isCreated()))
                .get("id").asString();

        // Run on today: creates 1 DUE_SOON reminder
        int created = loanDueJob.run(today());
        assertThat(created).isEqualTo(1);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM notifications WHERE notification_type = 'LOAN_DUE'");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("notification_type")).isEqualTo("LOAN_DUE");
        assertThat(row.get("reminder_kind")).isEqualTo("DUE_SOON");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((String) row.get("dedupe_key")).isEqualTo("LOAN_DUE:" + loanId + ":DUE_SOON");

        // Second run on the same day creates 0
        int secondRun = loanDueJob.run(today());
        assertThat(secondRun).isZero();

        // Run on due date: creates DUE_TODAY reminder
        int dueDateRun = loanDueJob.run(today().plusDays(3));
        assertThat(dueDateRun).isEqualTo(1);

        rows = jdbc.queryForList("SELECT * FROM notifications WHERE notification_type = 'LOAN_DUE' AND reminder_kind = 'DUE_TODAY'");
        assertThat(rows).hasSize(1);
        assertThat((String) rows.getFirst().get("dedupe_key")).isEqualTo("LOAN_DUE:" + loanId + ":DUE_TODAY");

        // Repaying principal in full and closing loan resolves open notifications
        asOwner(postWithKey("/api/v1/loans/" + loanId + "/principal-repayments", """
                {"amount":100000.00,"paymentDate":"%s","financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(today().plusDays(3), bank))).andExpect(status().isCreated());
        asOwner(postJson("/api/v1/loans/" + loanId + "/close", "{}")).andExpect(status().isOk());

        loanDueJob.run(today().plusDays(4));

        rows = jdbc.queryForList("SELECT * FROM notifications WHERE notification_type = 'LOAN_DUE'");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("RESOLVED"));
    }
}

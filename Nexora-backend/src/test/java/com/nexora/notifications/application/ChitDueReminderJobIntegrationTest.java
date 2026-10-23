package com.nexora.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nexora.notifications.NotificationTestSupport;

class ChitDueReminderJobIntegrationTest extends NotificationTestSupport {

    @Autowired
    private ChitDueReminderJob chitDueJob;

    @Test
    @DisplayName("FR-NTF-09 job creates chit installment due notifications and resolves on contribution")
    void job_createsChitDueNotifications_andResolvesOnContribution() throws Exception {
        String cash = createAccount("Cash Box", "CASH", "100000.00");

        // Create an active chit with startDate = today() + 3 days (DUE_SOON window for installment #1)
        String chitId = body(asOwner(postJson("/api/v1/chits", """
                {"chitName":"Gold Chit","totalAmount":200000.00,"monthlyAmount":10000.00,"durationMonths":20,
                 "startDate":"%s"}""".formatted(today().plusDays(3)))).andExpect(status().isCreated()))
                .get("id").asString();

        // Run today: installment #1 is due in 3 days -> DUE_SOON created
        int created = chitDueJob.run(today());
        assertThat(created).isEqualTo(1);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM notifications WHERE notification_type = 'CHIT_DUE'");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("notification_type")).isEqualTo("CHIT_DUE");
        assertThat(row.get("reminder_kind")).isEqualTo("DUE_SOON");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((String) row.get("dedupe_key")).isEqualTo("CHIT_DUE:" + chitId + ":M1:DUE_SOON");

        // Repeat run on same day creates 0
        int secondRun = chitDueJob.run(today());
        assertThat(secondRun).isZero();

        // Run on due date (today + 3): creates DUE_TODAY for installment #1
        int dueDateRun = chitDueJob.run(today().plusDays(3));
        assertThat(dueDateRun).isEqualTo(1);

        rows = jdbc.queryForList("SELECT * FROM notifications WHERE notification_type = 'CHIT_DUE' AND reminder_kind = 'DUE_TODAY'");
        assertThat(rows).hasSize(1);
        assertThat((String) rows.getFirst().get("dedupe_key")).isEqualTo("CHIT_DUE:" + chitId + ":M1:DUE_TODAY");

        // Pay installment #1 contribution
        asOwner(postWithKey("/api/v1/chits/" + chitId + "/contributions", """
                {"amount":10000.00,"financialAccountId":"%s","paymentMethod":"CASH","entryDate":"%s"}"""
                .formatted(cash, today().plusDays(3)))).andExpect(status().isCreated());

        // Next job run resolves installment #1 notifications
        chitDueJob.run(today().plusDays(4));

        rows = jdbc.queryForList("SELECT * FROM notifications WHERE notification_type = 'CHIT_DUE' AND dedupe_key LIKE '%:M1:%'");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("RESOLVED"));
    }
}

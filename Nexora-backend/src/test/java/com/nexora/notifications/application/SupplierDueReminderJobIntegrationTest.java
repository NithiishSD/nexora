package com.nexora.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.nexora.notifications.NotificationTestSupport;

class SupplierDueReminderJobIntegrationTest extends NotificationTestSupport {

    @Test
    @DisplayName("FR-NTF-02 job creates due soon due today and overdue reminder kinds")
    void job_createsDueSoonDueTodayAndOverdueReminderKinds() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String supplier = data.supplier().id().toString();
        // AC1: due in 3 days (3 configured) -> DUE_SOON; due today -> DUE_TODAY; AC2: due 2 days ago -> OVERDUE
        String soon = duePurchase(supplier, today(), 3, "100.00");
        String dueToday = duePurchase(supplier, today(), 0, "100.00");
        String overdue = duePurchase(supplier, today().minusDays(10), 8, "100.00");
        // nothing for a purchase due in 10 days, nor (AC4) for one fully paid
        String later = duePurchase(supplier, today(), 10, "100.00");
        String other = data.supplier().id().toString();
        String paid = duePurchase(other, today(), 0, "100.00");
        paySupplier(other, "1000.00", bank);

        assertThat(job.run(today())).isEqualTo(3);
        Map<String, Object> soonRow = reminders(soon).getFirst();
        assertThat(soonRow.get("reminder_kind")).isEqualTo("DUE_SOON");
        assertThat(soonRow.get("status")).isEqualTo("PENDING");
        assertThat(soonRow.get("party")).isEqualTo(supplier);
        assertThat((String) soonRow.get("message")).contains("Pay Rs 1000.00").contains("due in 3 days")
                .contains((String) soonRow.get("reference_number"));
        assertThat(reminders(dueToday)).singleElement().satisfies(r -> assertThat(r.get("reminder_kind"))
                .isEqualTo("DUE_TODAY"));
        assertThat(reminders(overdue)).singleElement().satisfies(r -> {
            assertThat(r.get("reminder_kind")).isEqualTo("OVERDUE");
            assertThat((String) r.get("dedupe_key")).isEqualTo("SUPPLIER_DUE:" + overdue + ":OVERDUE:" + today());
            assertThat((String) r.get("message")).contains("overdue by 2 days");
        });
        assertThat(reminders(later)).isEmpty();
        assertThat(reminders(paid)).isEmpty();
    }

    @Test
    @DisplayName("FR-NTF-02 job run twice same day creates no duplicate by dedupe key")
    void jobRunTwiceSameDay_createsNoDuplicateByDedupeKey() throws Exception {
        String supplier = data.supplier().id().toString();
        String soon = duePurchase(supplier, today(), 3, "100.00");
        String overdue = duePurchase(supplier, today().minusDays(5), 1, "100.00");

        // AC3: the second run on the same day finds every key taken
        assertThat(job.run(today())).isEqualTo(2);
        assertThat(job.run(today())).isZero();
        assertThat(reminders(soon)).hasSize(1);
        assertThat(reminders(overdue)).hasSize(1);
        // the next day is a new overdue reminder (repeat 1) but no second DUE_SOON
        assertThat(job.run(today().plusDays(1))).isEqualTo(1);
        assertThat(reminders(overdue)).hasSize(2);
        assertThat(reminders(soon)).hasSize(1);
    }

    @Test
    @DisplayName("FR-NTF-01 FR-NTF-02 overdue reminder repeats every overdue repeat days")
    void overdueReminder_repeatsEveryOverdueRepeatDays() throws Exception {
        asOwner(put("/api/v1/notifications/configurations/SUPPLIER_DUE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"daysBeforeDue\":3,\"overdueRepeatDays\":2,\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk());
        String purchase = duePurchase(data.supplier().id().toString(), today(), 0, "100.00");

        // due today, then overdue every second day: day 2 and day 4, not days 1 and 3
        for (int day = 0; day <= 4; day++) {
            job.run(today().plusDays(day));
        }
        List<Map<String, Object>> rows = reminders(purchase);
        assertThat(rows).extracting(r -> r.get("dedupe_key")).containsExactly(
                "SUPPLIER_DUE:" + purchase + ":DUE_TODAY",
                "SUPPLIER_DUE:" + purchase + ":OVERDUE:" + today().plusDays(2),
                "SUPPLIER_DUE:" + purchase + ":OVERDUE:" + today().plusDays(4));
        assertThat(jdbc.queryForList("select distinct priority from notifications where reference_id = ?::uuid",
                String.class, purchase)).containsExactly("MEDIUM");

        // switched off: nothing new
        asOwner(put("/api/v1/notifications/configurations/SUPPLIER_DUE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"daysBeforeDue\":3,\"overdueRepeatDays\":2,\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk());
        assertThat(job.run(today().plusDays(6))).isZero();
    }
}

package com.nexora.notifications;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.nexora.notifications.application.ChitDueReminderJob;
import com.nexora.notifications.application.LoanDueReminderJob;
import com.nexora.notifications.application.SupplierDueReminderJob;
import com.nexora.support.AbstractApiTest;

// Shared setup for the notification tests: credit purchases with a chosen due date, received in full, and the
// reminder rows as the database holds them.
public abstract class NotificationTestSupport extends AbstractApiTest {

    @Autowired
    protected SupplierDueReminderJob job;

    @Autowired
    protected LoanDueReminderJob loanJob;

    @Autowired
    protected ChitDueReminderJob chitJob;

    // 10 kg at the given rate on credit, dated `date` with `creditDays`, fully received today; returns its id.
    protected String duePurchase(String supplier, LocalDate date, int creditDays, String rate) throws Exception {
        String id = body(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":10.000,"ratePerKg":%s,"paymentType":"CREDIT",
                 "creditDays":%d}""".formatted(supplier, date, rate, creditDays))).andExpect(status().isCreated()))
                .get("id").asString();
        asOwner(postWithKey("/api/v1/purchases/" + id + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":10.000,"acceptedWeightKg":10.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated());
        return id;
    }

    protected void paySupplier(String supplier, String amount, String account) throws Exception {
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s"}""".formatted(supplier, amount, account))).andExpect(status().isCreated());
    }

    protected List<Map<String, Object>> reminders(String purchaseId) {
        return jdbc.queryForList("select reminder_kind, status, dedupe_key, message, recipient_party_id::text as party,"
                + " reference_number from notifications where reference_id = ?::uuid order by created_at, dedupe_key",
                purchaseId);
    }
}

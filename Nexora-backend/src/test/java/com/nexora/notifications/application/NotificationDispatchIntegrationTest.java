package com.nexora.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.nexora.notifications.NotificationTestSupport;
import com.nexora.notifications.infrastructure.LoggingNotificationProvider;
import com.nexora.notifications.infrastructure.NotificationRepository;

class NotificationDispatchIntegrationTest extends NotificationTestSupport {

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("FR-NTF-04 NFR-REL-05 provider failure does not roll back business transaction")
    void providerFailure_doesNotRollBackBusinessTransaction() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String supplier = data.supplier().id().toString();
        String first = duePurchase(supplier, today().minusDays(10), 7, "100.00");     // 1000.00 overdue
        String second = duePurchase(supplier, today(), 0, "100.00");                  // 1000.00 due today
        job.run(today());

        // AC1: a provider failing twice then succeeding -> SENT after 3 attempts (no delay in the test)
        AtomicInteger calls = new AtomicInteger();
        NotificationDispatcher flaky = dispatcher(n -> {
            if (calls.incrementAndGet() % 3 != 0) {
                throw new IllegalStateException("provider down");
            }
        });
        assertThat(flaky.dispatchPending()).isEqualTo(2);
        assertThat(state(first)).containsEntry("status", "SENT").containsEntry("attempts", 3);
        assertThat(state(second)).containsEntry("status", "SENT").containsEntry("attempts", 3);

        // AC2: a provider that is down -> FAILED after 3 attempts; the payment recorded meanwhile stands
        String third = duePurchase(supplier, today(), 0, "100.00");
        job.run(today());
        paySupplier(supplier, "500.00", bank);
        assertThat(dispatcher(n -> {
            throw new IllegalStateException("provider down");
        }).dispatchPending()).isZero();
        assertThat(state(third)).containsEntry("status", "FAILED").containsEntry("attempts", 3)
                .containsEntry("last_error", "provider down");
        assertThat(jdbc.queryForObject("select count(*) from payments where status = 'RECORDED'", Integer.class))
                .isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(99500.00));
        // NFR-REL-05 AC1: recording a payment never calls the provider at all, so its outage cannot fail one
        paySupplier(supplier, "100.00", bank);
    }

    @Test
    @DisplayName("FR-NTF-07 only supplier due type is scheduled in first release")
    void onlySupplierDueTypeIsScheduledInFirstRelease() throws Exception {
        // material for the post-MVP alerts: almost no raw silk, a customer owing money, an active loan
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        data.rawSilkInStock("1.000");
        confirmedOrder(data.customer().id(), "RAW_SILK", "100.000", "1000.00");
        asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":10000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(data.lender().id(), today(), bank)));
        duePurchase(data.supplier().id().toString(), today(), 0, "100.00");

        job.run(today());
        assertThat(jdbc.queryForList("select distinct notification_type from notifications", String.class))
                .containsExactly("SUPPLIER_DUE");
        asOwner(get("/api/v1/notifications/configurations")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationType").value("SUPPLIER_DUE"));
    }

    @Test
    @DisplayName("FR-NTF-08 logging provider is the only provider")
    void loggingProviderIsTheOnlyProvider() throws Exception {
        assertThat(context.getBeansOfType(NotificationProvider.class).values()).singleElement()
                .isInstanceOf(LoggingNotificationProvider.class);
        // the real dispatcher with the logging provider sends at the first attempt
        String purchase = duePurchase(data.supplier().id().toString(), today(), 0, "100.00");
        job.run(today());
        assertThat(context.getBean(NotificationDispatcher.class).dispatchPending()).isEqualTo(1);
        assertThat(state(purchase)).containsEntry("status", "SENT").containsEntry("attempts", 1);
    }

    private NotificationDispatcher dispatcher(NotificationProvider provider) {
        return new NotificationDispatcher(notifications, transactionManager, provider, clock, 0);
    }

    private Map<String, Object> state(String purchaseId) {
        return jdbc.queryForMap("select status, attempts, last_error from notifications where reference_id = ?::uuid",
                purchaseId);
    }
}

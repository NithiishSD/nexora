package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class ChequeApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-25 AC1 cheque recorded as pending does not affect account balance until cleared")
    void chequePending_doesNotAffectAccountBalance_untilCleared() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String orderId = confirmedOrder(customer, "SILK_WARP", "10.000", "5000.00");

        // Record a pending cheque of 20000 against the order
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":20000.00,"paymentMethod":"CHEQUE",
                 "financialAccountId":"%s","referenceNumber":"CHQ-123456","pendingCheque":true}"""
                .formatted(customer, bank)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(20000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(20000.00))).get("id").asString();

        // 1. Account balance is still 100,000 (money did not change yet)
        asOwner(get("/api/v1/finance/accounts/" + bank))
                .andExpect(jsonPath("$.balance").value(100000.00));

        // 2. Order outstanding is 30,000 (50,000 - 20,000 allocated) so customer cannot be double-settled
        asOwner(get("/api/v1/orders/" + orderId))
                .andExpect(jsonPath("$.outstandingAmount").value(30000.00));

        // 3. Clear the cheque
        asOwner(postJson("/api/v1/payments/" + paymentId + "/cheque-clearance", """
                {"date":"%s","notes":"Cleared by SBI"}""".formatted(today())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDED"));

        // 4. Now bank balance reflects the cleared 20,000 -> 120,000
        asOwner(get("/api/v1/finance/accounts/" + bank))
                .andExpect(jsonPath("$.balance").value(120000.00));

        // 5. Audit log recorded
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CHEQUE_CLEARED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAY-25 AC2 bounced cheque releases its allocations back to outstanding")
    void chequeBounce_releasesAllocationsBackToOutstanding() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("HDFC Bank", "BANK", "50000.00");
        String orderId = confirmedOrder(customer, "SILK_WARP", "10.000", "5000.00");

        // Record a pending cheque of 25000
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":25000.00,"paymentMethod":"CHEQUE",
                 "financialAccountId":"%s","referenceNumber":"CHQ-999999","pendingCheque":true}"""
                .formatted(customer, bank)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asString();

        // Order has 25,000 outstanding while pending
        asOwner(get("/api/v1/orders/" + orderId))
                .andExpect(jsonPath("$.outstandingAmount").value(25000.00));

        // Cheque bounces
        asOwner(postJson("/api/v1/payments/" + paymentId + "/cheque-bounce", """
                {"date":"%s","notes":"Insufficient funds in customer account"}""".formatted(today())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOUNCED"))
                .andExpect(jsonPath("$.reversalReason").value("Insufficient funds in customer account"));

        // Order returns to full 50,000 outstanding!
        asOwner(get("/api/v1/orders/" + orderId))
                .andExpect(jsonPath("$.outstandingAmount").value(50000.00));

        // Bank balance never changed (still 50,000)
        asOwner(get("/api/v1/finance/accounts/" + bank))
                .andExpect(jsonPath("$.balance").value(50000.00));
    }

    @Test
    @DisplayName("FR-PAY-25 cheque bounce on already cleared cheque reverses ledger transaction")
    void chequeBounce_onClearedCheque_reversesLedgerTransaction() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("Axis Bank", "BANK", "10000.00");
        String orderId = confirmedOrder(customer, "SILK_WARP", "10.000", "5000.00");

        // Immediately recorded cheque (standard clearance)
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":15000.00,"paymentMethod":"CHEQUE",
                 "financialAccountId":"%s","referenceNumber":"CHQ-777777"}"""
                .formatted(customer, bank)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECORDED"))).get("id").asString();

        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(25000.00));

        // Cheque later reported bounced by bank
        asOwner(postJson("/api/v1/payments/" + paymentId + "/cheque-bounce", """
                {"notes":"Drawer signature mismatch"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOUNCED"));

        // Bank balance reversed back to 10000!
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(10000.00));

        // Order outstanding restored back to 50000
        asOwner(get("/api/v1/orders/" + orderId)).andExpect(jsonPath("$.outstandingAmount").value(50000.00));
    }

    @Test
    @DisplayName("FR-PAY-25 cheque actions reject invalid state transitions")
    void chequeActions_rejectInvalidStateTransitions() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("ICICI Bank", "BANK", "10000.00");

        // 1. Cannot mark CASH payment as pending
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":1000.00,"paymentMethod":"CASH",
                 "financialAccountId":"%s","pendingCheque":true}""".formatted(customer, bank)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));

        // 2. Clear on non-pending payment -> 409
        String recordedPaymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":1000.00,"paymentMethod":"CHEQUE",
                 "financialAccountId":"%s"}""".formatted(customer, bank)))).get("id").asString();

        asOwner(postJson("/api/v1/payments/" + recordedPaymentId + "/cheque-clearance", "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
}

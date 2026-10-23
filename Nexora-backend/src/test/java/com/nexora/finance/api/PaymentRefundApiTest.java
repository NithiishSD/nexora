package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class PaymentRefundApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-25 E-PAY-13 customer refund reduces unallocated advance and moves money out")
    void customerRefund_reducesAdvance_andMovesMoneyOut() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "50000.00");

        // Customer pays 10,000.00 advance (no open orders)
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":10000.00,"paymentMethod":"UPI",
                 "financialAccountId":"%s"}""".formatted(customer, bank)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unallocatedAmount").value(10000.00))).get("id").asString();

        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(60000.00));

        // Refund 4,000.00
        String refundId = body(asOwner(postWithKey("/api/v1/payments/" + paymentId + "/refunds", """
                {"amount":4000.00,"paymentMethod":"UPI","financialAccountId":"%s",
                 "refundDate":"%s","reason":"Customer requested partial advance refund"}"""
                .formatted(bank, today())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PAID"))
                .andExpect(jsonPath("$.purpose").value("CUSTOMER"))
                .andExpect(jsonPath("$.amount").value(4000.00))
                .andExpect(jsonPath("$.refundOfPaymentId").value(paymentId))).get("id").asString();

        // 1. Bank balance reduced by 4000 -> 56000
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(56000.00));

        // 2. Original payment's unallocated amount is now 6000.00
        asOwner(get("/api/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.unallocatedAmount").value(6000.00));

        // 3. Financial transaction is CUSTOMER_REFUND with direction OUT
        asOwner(get("/api/v1/finance/transactions?referenceId=" + refundId))
                .andExpect(jsonPath("$.content[0].transactionType").value("CUSTOMER_REFUND"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(4000.00));

        // 4. Audit log recorded
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PAYMENT_REFUNDED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAY-25 E-PAY-13 refund exceeding advance returns 409 allocation exceeded")
    void refund_exceedingAdvance_returns409() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash Box", "CASH", "20000.00");

        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":5000.00,"paymentMethod":"CASH",
                 "financialAccountId":"%s"}""".formatted(customer, cash)))).get("id").asString();

        // Try to refund 6000 when only 5000 was paid
        asOwner(postWithKey("/api/v1/payments/" + paymentId + "/refunds", """
                {"amount":6000.00,"paymentMethod":"CASH","financialAccountId":"%s",
                 "reason":"Excess refund"}""".formatted(cash)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
    }

    @Test
    @DisplayName("FR-PAY-25 E-PAY-13 refund with insufficient account funds returns 409")
    void refund_withInsufficientFunds_returns409() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash In Hand", "CASH", "0.00");
        String bank = createAccount("Bank Account", "BANK", "50000.00");

        // Customer paid into bank
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":5000.00,"paymentMethod":"UPI",
                 "financialAccountId":"%s"}""".formatted(customer, bank)))).get("id").asString();

        // Try to refund 5000 from cash (which has 0.00 balance)
        asOwner(postWithKey("/api/v1/payments/" + paymentId + "/refunds", """
                {"amount":5000.00,"paymentMethod":"CASH","financialAccountId":"%s",
                 "reason":"Refund in cash"}""".formatted(cash)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }
}

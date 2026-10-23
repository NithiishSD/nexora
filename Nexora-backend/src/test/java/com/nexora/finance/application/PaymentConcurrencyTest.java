package com.nexora.finance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;

// FR-PAY-24: two payments of one party at the same moment never allocate more than is outstanding. Mutation check (by
// hand, never committed): without the party lock and the obligation row locks in PaymentService, AC1 allocates more
// than the item amount.
class PaymentConcurrencyTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-24 NFR-TEST-05 parallel payments never over allocate")
    void parallelPayments_neverOverAllocate() throws Exception {
        String token = tokens.owner();
        for (int round = 0; round < 3; round++) {
            // AC1: one item of 40000, two customer payments of 30000 -> exactly 40000 allocated, 20000 advance
            UUID customer = data.customer().id();
            // two different accounts, so the account row lock cannot be what keeps the payments apart
            String cash = createAccount("Cash " + round, "CASH", "100000.00");
            String drawer = createAccount("Drawer " + round, "CASH", "100000.00");
            confirmedOrder(customer, "KORA_WARP", "40.000", "1000.00");
            List<Outcome<MockHttpServletResponse>> paid = ConcurrentRunner.runTogether(List.of(
                    pay(token, "CUSTOMER", customer, "30000.00", cash), pay(token, "CUSTOMER", customer, "30000.00", drawer)));
            assertThat(paid).allSatisfy(o -> assertThat(o.value().getStatus()).isEqualTo(201));
            assertThat(allocatedTo("customer_payment_allocations", customer)).isEqualByComparingTo("40000.00");

            // AC2: supplier payable 50000, two payments of 30000 -> one succeeds, the other is refused
            UUID supplier = data.supplier().id();
            String purchase = createPurchase(supplier, "RAW_SILK", "100.000", "500.00");
            receive(purchase, "100.000", "100.000", "0.000").andExpect(org.springframework.test.web.servlet.result
                    .MockMvcResultMatchers.status().isCreated());
            List<Outcome<MockHttpServletResponse>> out = ConcurrentRunner.runTogether(List.of(
                    pay(token, "SUPPLIER", supplier, "30000.00", cash), pay(token, "SUPPLIER", supplier, "30000.00", drawer)));
            assertThat(out).extracting(o -> o.value().getStatus()).containsExactlyInAnyOrder(201, 409);
            assertThat(out.stream().filter(o -> o.value().getStatus() == 409).findFirst().orElseThrow().value()
                    .getContentAsString()).contains("PAYMENT_ALLOCATION_EXCEEDED");
            // AC3: no obligation is ever allocated more than its amount
            assertThat(allocatedTo("supplier_payment_allocations", supplier)).isEqualByComparingTo("30000.00");
        }
    }

    private Callable<MockHttpServletResponse> pay(String token, String purpose, UUID party, String amount, String account) {
        return () -> mockMvc.perform(postWithKey("/api/v1/payments", """
                {"purpose":"%s","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(purpose, party, amount, account)).header("Authorization", AuthTokens.bearer(token)))
                .andReturn().getResponse();
    }

    private java.math.BigDecimal allocatedTo(String table, UUID party) {
        return jdbc.queryForObject("select coalesce(sum(a.allocated_amount), 0) from " + table + " a join payments p"
                + " on p.id = a.payment_id where p.party_id = ?", java.math.BigDecimal.class, party);
    }
}

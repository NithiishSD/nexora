package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

class SupplierPaymentApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-08 supplier payment allocates oldest purchase first")
    void supplierPayment_allocatesOldestPurchaseFirst() throws Exception {
        UUID supplier = data.supplier().id();
        String cash = createAccount("Cash", "CASH", "100000.00");
        String p1 = receivedPurchase(supplier, "100.000", "500.00");       // payable 50000, older number
        String p2 = receivedPurchase(supplier, "60.000", "500.00");        // payable 30000

        // AC1: 60000 -> P1 settled, P2 10000; the cash goes OUT through the ledger
        String id = body(pay(supplier, "60000.00", cash, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PAID"))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].targetType").value("PURCHASE"))
                .andExpect(jsonPath("$.allocations[0].amount").value(50000.00))
                .andExpect(jsonPath("$.allocations[1].amount").value(10000.00))
                .andExpect(jsonPath("$.unallocatedAmount").value(0.0))).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(40000.00));
        asOwner(get("/api/v1/finance/transactions?referenceId=" + id))
                .andExpect(jsonPath("$.content[0].transactionType").value("SUPPLIER_PAYMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"));
        asOwner(get("/api/v1/purchases/" + p1)).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(0.0));
        asOwner(get("/api/v1/purchases/" + p2)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.allocatedAmount").value(10000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(20000.00));

        // AC2: 20000 still payable -> paying 30000 is refused, nothing stored (no supplier advance in the MVP)
        pay(supplier, "30000.00", cash, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        // explicit allocations must cover the whole payment too
        pay(supplier, "5000.00", cash, "[{\"targetType\":\"PURCHASE\",\"targetId\":\"" + p2 + "\",\"amount\":4000.00}]")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        pay(supplier, "5000.00", cash, "[{\"targetType\":\"PURCHASE\",\"targetId\":\"" + p2 + "\",\"amount\":5000.00}]")
                .andExpect(status().isCreated());
        // AC3: more than the cash account holds -> 409 INSUFFICIENT_FUNDS, nothing stored
        String petty = createAccount("Petty cash", "CASH", "5000.00");
        pay(supplier, "6000.00", petty, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(2);
        asOwner(get("/api/v1/finance/accounts/" + petty)).andExpect(jsonPath("$.balance").value(5000.00));
        // AC4: a customer is not a supplier; a purchase with payments cannot be cancelled
        pay(data.customer().id(), "1.00", cash, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        assertThat(jdbc.queryForObject("select count(*) from supplier_payment_allocations", Integer.class)).isEqualTo(3);
    }

    private String receivedPurchase(UUID supplier, String kg, String rate) throws Exception {
        String id = createPurchase(supplier, "RAW_SILK", kg, rate);
        receive(id, kg, kg, "0.000").andExpect(status().isCreated());
        return id;
    }

    private ResultActions pay(UUID supplier, String amount, String account, String allocations) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s"%s}"""
                .formatted(supplier, amount, account, allocations == null ? "" : ",\"allocations\":" + allocations)));
    }
}

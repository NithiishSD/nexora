package com.nexora.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

// The MVP loop end to end through the API only, as the desktop client drives it (NFR-TEST-06): the owner logs in,
// buys raw silk, receives it in two lots, sells part of it to a customer, delivers, gets paid, pays the supplier, and
// every derived figure (stock, money, outstanding, payable) is read back from the server.
class MvpScenarioTest extends AbstractApiTest {

    @Value("${nexora.bootstrap.owner-username}")
    String username;

    @Value("${nexora.bootstrap.owner-password}")
    String password;

    private String token;

    @Test
    @DisplayName("NFR-TEST-06 login supplier purchase receive order deliver pay outstanding")
    void loginSupplierPurchaseReceiveOrderDeliverPayOutstanding() throws Exception {
        // 1. login
        token = body(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s","password":"%s"}""".formatted(username, password))).andExpect(status().isOk()))
                .get("accessToken").asString();

        // 2. accounts: cash 20000 and a bank account with 100000 at go-live
        String cash = id(call(postJson("/api/v1/finance/accounts", """
                {"accountName":"Cash","accountType":"CASH","openingBalance":20000.00,"openingDate":"%s"}"""
                .formatted(today()))).andExpect(status().isCreated()));
        String bank = id(call(postJson("/api/v1/finance/accounts", """
                {"accountName":"SBI Current","accountType":"BANK","bankName":"State Bank of India",
                 "accountNumberLast4":"4821","openingBalance":100000.00,"openingDate":"%s"}""".formatted(today())))
                .andExpect(status().isCreated()));

        // 3. supplier and a credit purchase of 100 kg raw silk at 800.00
        String supplier = id(call(postJson("/api/v1/parties", """
                {"name":"Murugan Silks","roles":["SUPPLIER"],"defaultCreditDays":30,
                 "phones":[{"phoneNumber":"+91 94430 12345","label":"mobile","primary":true}]}"""))
                .andExpect(status().isCreated()));
        String purchase = id(call(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":100.000,"ratePerKg":800.00,
                 "paymentType":"CREDIT"}""".formatted(supplier, today()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(today().plusDays(30).toString())));

        // 4. partial receipts: 40 kg (all accepted), then 60 kg with 2 kg rejected -> 98 kg in raw stock
        call(postWithKey("/api/v1/purchases/" + purchase + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":40.000,"acceptedWeightKg":40.000,"rejectedWeightKg":0.000}"""
                .formatted(today()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
        call(postWithKey("/api/v1/purchases/" + purchase + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":60.000,"acceptedWeightKg":58.000,"rejectedWeightKg":2.000}"""
                .formatted(today()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FULLY_RECEIVED"))
                .andExpect(jsonPath("$.payableAmount").value(78400.00));                 // 98 kg x 800 (Q1)

        // 5. stock: 98 kg raw silk in RAW_STOCK, from two RECEIPT movements
        assertThat(rawStock()).isEqualTo("98.000");
        call(get("/api/v1/inventory/movements?movementType=RECEIPT")).andExpect(jsonPath("$.totalElements").value(2));

        // 6. customer and an order for 50 kg raw silk at 1000.00 (a direct sale, Q7)
        String customer = id(call(postJson("/api/v1/parties", """
                {"name":"Lakshmi Weavers","roles":["CUSTOMER"]}""")).andExpect(status().isCreated()));
        String order = id(call(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":50.000,"ratePerKg":1000.00}]}"""
                .formatted(customer, today(), today().plusDays(7)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(50000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0.0)));                 // PLACED: not owed yet (Q2)

        // 7. confirm (now owed), ready from stock, deliver: a DIRECT_SALE takes 50 kg out of raw stock
        call(postJson("/api/v1/orders/" + order + "/confirm", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingAmount").value(50000.00));
        call(postJson("/api/v1/orders/" + order + "/ready", "")).andExpect(status().isOk());
        call(postWithKey("/api/v1/orders/" + order + "/deliver", """
                {"deliveryDate":"%s"}""".formatted(today()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(rawStock()).isEqualTo("48.000");

        // 8. the customer pays 30000 by UPI into the bank -> 20000 still outstanding
        call(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":30000.00,"paymentMethod":"UPI",
                 "financialAccountId":"%s","referenceNumber":"UTR 4471"}""".formatted(customer, bank)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedAmount").value(30000.00));
        call(get("/api/v1/customers/" + customer + "/outstanding")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(20000.00))
                .andExpect(jsonPath("$.advance").value(0.0))
                .andExpect(jsonPath("$.orders[0].outstanding").value(20000.00));
        call(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));

        // 9. the supplier is paid 70000 from the bank -> 8400 still payable
        call(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":70000.00,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s"}""".formatted(supplier, bank))).andExpect(status().isCreated());
        call(get("/api/v1/suppliers/" + supplier + "/payables"))
                .andExpect(jsonPath("$.totalPayable").value(8400.00))
                .andExpect(jsonPath("$.purchases[0].paymentStatus").value("PARTIALLY_PAID"));

        // 10. money: bank 100000 + 30000 - 70000 = 60000, cash untouched, 80000 available in total
        call(get("/api/v1/finance/accounts")).andExpect(jsonPath("$.totalAvailable").value(80000.00));
        call(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(60000.00));
        call(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(20000.00));
        // the ledgers explain every figure: 2 opening balances + 2 payments; 2 receipts + 1 direct sale
        call(get("/api/v1/finance/transactions")).andExpect(jsonPath("$.totalElements").value(4));
        call(get("/api/v1/inventory/movements")).andExpect(jsonPath("$.totalElements").value(3));
        // 11. the dashboard shows the same figures in one call: available money apart from what is owed (FR-RPT-02);
        // the purchase is due in 30 days, so it is not among the upcoming dues yet
        call(get("/api/v1/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(20000.00))
                .andExpect(jsonPath("$.banks[0].balance").value(60000.00))
                .andExpect(jsonPath("$.totalAvailable").value(80000.00))
                .andExpect(jsonPath("$.customerReceivables").value(20000.00))
                .andExpect(jsonPath("$.supplierPayables").value(8400.00))
                .andExpect(jsonPath("$.supplierOverdue").value(0.0))
                .andExpect(jsonPath("$.stock.rawSilkKg").value(48.000))
                .andExpect(jsonPath("$.ordersByStatus.READY").value(0))
                .andExpect(jsonPath("$.upcomingSupplierDues.length()").value(0))
                .andExpect(jsonPath("$.recentTransactions.length()").value(4));
        // and the nightly check finds the stock projection equal to the movement ledger
        assertThat(jdbc.queryForObject("select count(*) from inventory_balances b where b.quantity_kg <> coalesce(("
                + " select sum(case when m.to_location = b.location and m.to_product_type = b.product_type then"
                + " m.quantity_kg else 0 end) - sum(case when m.from_location = b.location and m.product_type ="
                + " b.product_type then m.quantity_kg else 0 end) from material_movements m), 0)", Integer.class))
                .isZero();
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String id(ResultActions result) throws Exception {
        return body(result).get("id").asString();
    }

    private String rawStock() throws Exception {
        JsonNode summary = body(call(get("/api/v1/inventory/summary")).andExpect(status().isOk()));
        return summary.get("rawSilkAvailableKg").decimalValue().setScale(3).toPlainString();
    }
}

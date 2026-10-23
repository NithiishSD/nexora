package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class PaymentApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PAY-07 NFR-USE-05 record replay with same key returns stored response")
    void record_replayWithSameKey_returnsStoredResponse() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00");

        // a double-click or a retry on a slow line: one payment, one allocation, one ledger row, same answer
        String first = replayTwice("/api/v1/payments", body(customer, "4000.00", "CASH", cash, null), 201);

        assertThat(first).contains("PAY-");
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(4000.00));
        // the same key with another amount is refused
        String key = UUID.randomUUID().toString();
        asOwner(postWithKey("/api/v1/payments", body(customer, "1.00", "CASH", cash, null), key))
                .andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/payments", body(customer, "2.00", "CASH", cash, null), key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("FR-PAY-01 FR-PAY-06 customer payment allocates oldest first and records transaction")
    void customerPayment_allocatesOldestFirstAndRecordsTransaction() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "1000.00");
        String placed = createOrder(customer, "KORA_WARP", "10.000", "1000.00");          // oldest, but not owed (AC4)
        String a = confirmedOrder(customer, "KORA_WARP", "20.000", "1000.00");            // 20000
        String b = confirmedOrder(customer, "KORA_WARP", "30.000", "1000.00");            // 30000
        String c = confirmedOrder(customer, "KORA_WARP", "40.000", "1000.00");            // 40000

        // AC1: 45000 by UPI -> A settled, B 25000, C untouched; the account rises by 45000
        String id = body(pay(customer, "45000.00", "UPI", bank, null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/payments/")))
                .andExpect(jsonPath("$.paymentNumber").value("PAY-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.direction").value("RECEIVED"))
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.allocatedAmount").value(45000.00))
                .andExpect(jsonPath("$.unallocatedAmount").value(0.0))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].targetType").value("ORDER_ITEM"))
                .andExpect(jsonPath("$.allocations[0].targetLabel").value(Matchers.endsWith("-0002 line 1")))
                .andExpect(jsonPath("$.allocations[0].amount").value(20000.00))
                .andExpect(jsonPath("$.allocations[1].amount").value(25000.00))
                .andExpect(jsonPath("$.allocations[1].released").value(false))
                .andExpect(jsonPath("$.financialTransactionId").isString()))
                .get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(46000.00));
        asOwner(get("/api/v1/finance/transactions?referenceType=PAYMENT&referenceId=" + id))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("CUSTOMER_PAYMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].amount").value(45000.00));
        // the orders show it: A paid, B part paid with 5000 open, C and the placed order unpaid
        asOwner(get("/api/v1/orders/" + a)).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(0.0));
        asOwner(get("/api/v1/orders/" + b)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(5000.00));
        asOwner(get("/api/v1/orders/" + c)).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        asOwner(get("/api/v1/orders/" + placed)).andExpect(jsonPath("$.allocatedAmount").value(0.0));
        asOwner(get("/api/v1/payments/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.party.id").value(customer.toString()))
                .andExpect(jsonPath("$.account.accountName").value("SBI Current"));
        // an order with payments allocated can no longer be cancelled or edited
        asOwner(postJson("/api/v1/orders/" + a + "/cancel", "{\"reason\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // AC4: a closed account -> 409 and nothing is stored (FR-PAY-06: no payment, no allocation, no ledger row)
        jdbc.update("update financial_accounts set status = 'CLOSED' where id = ?::uuid", bank);
        pay(customer, "100.00", "UPI", bank, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations", Integer.class)).isEqualTo(2);
        // AC3: an inactive customer; AC5: STAFF
        String cash = createAccount("Cash", "CASH", "0.00");
        asOwner(postJson("/api/v1/parties/" + customer + "/deactivate", "")).andExpect(status().isOk());
        pay(customer, "100.00", "CASH", cash, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARTY_INACTIVE"));
        mockMvc.perform(postWithKey("/api/v1/payments", body(customer, "1.00", "CASH", cash, null))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        asOwner(get("/api/v1/payments/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PAY-04 customer payment excess becomes advance")
    void customerPayment_excess_becomesAdvance() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        confirmedOrder(customer, "RAW_SILK", "60.000", "1000.00");                       // 60000 owed

        // AC1: 100000 against 60000 owed -> 60000 allocated, 40000 advance (not dropped, not revenue)
        pay(customer, "100000.00", "CASH", cash, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedAmount").value(60000.00))
                .andExpect(jsonPath("$.unallocatedAmount").value(40000.00));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(100000.00));
        // AC2: a customer who owes nothing -> the whole amount is advance
        UUID other = data.party("Selvi", java.util.List.of(com.nexora.parties.domain.PartyRole.CUSTOMER), null, null).id();
        pay(other, "5000.00", "CASH", cash, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations.length()").value(0))
                .andExpect(jsonPath("$.unallocatedAmount").value(5000.00));
    }

    @Test
    @DisplayName("FR-PAY-13 amount with three decimals returns400")
    void amountWithThreeDecimals_returns400() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");

        pay(customer, "10.005", "CASH", cash, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        pay(customer, "0.00", "CASH", cash, null).andExpect(status().isBadRequest());     // FR-PAY-01 AC2
        pay(customer, "-5.00", "CASH", cash, null).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();
        // AC1: 1234.50 is stored exactly
        pay(customer, "1234.50", "CASH", cash, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1234.50));
        assertThat(jdbc.queryForObject("select amount::text from payments", String.class)).isEqualTo("1234.50");
    }

    @Test
    @DisplayName("FR-PAY-12 cash method with bank account returns422 invalid payment method")
    void cashMethod_withBankAccount_returns422InvalidPaymentMethod() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "0.00");
        String cash = createAccount("Cash", "CASH", "0.00");

        pay(customer, "100.00", "CASH", bank, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        pay(customer, "100.00", "UPI", cash, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        pay(customer, "100.00", "CHEQUE", bank, null).andExpect(status().isCreated());     // AC1
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAY-11 direction in for supplier returns422 invalid party role")
    void directionIn_forSupplier_returns422InvalidPartyRole() throws Exception {
        String cash = createAccount("Cash", "CASH", "0.00");

        pay(data.supplier().id(), "100.00", "CASH", cash, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        pay(UUID.randomUUID(), "100.00", "CASH", cash, null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();
    }

    @Test
    @DisplayName("NFR-USE-01 record without date defaults to today")
    void record_withoutDate_defaultsToToday() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");

        // the daily path: party, amount, method, account (4 inputs); the date is today
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":500.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(customer, cash)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDate").value(today().toString()));
        assertThat(jdbc.queryForObject("select transaction_date from financial_transactions where transaction_type ="
                + " 'CUSTOMER_PAYMENT'", java.time.LocalDate.class)).isEqualTo(today());
        // an explicit date is kept
        pay(customer, "1.00", "CASH", cash, today().minusDays(3).toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDate").value(today().minusDays(3).toString()));
    }

    @Test
    @DisplayName("FR-PAY-25 refund direction is not offered in first release")
    void refundDirection_isNotOfferedInFirstRelease() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "1000.00");

        // a client trying to pay a customer out: the direction is derived from the purpose, money comes IN
        String id = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","direction":"PAID","partyId":"%s","amount":100.00,"paymentMethod":"CASH",
                 "financialAccountId":"%s"}""".formatted(customer, cash)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("RECEIVED"))).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(1100.00));
        // and there is no refund route
        asOwner(postWithKey("/api/v1/payments/" + id + "/refund", "{}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PAY-03 explicit allocation above outstanding returns409 allocation exceeded")
    void explicitAllocation_aboveOutstanding_returns409AllocationExceeded() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        String x = itemOf(confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00"));    // 10000
        String y = itemOf(confirmedOrder(customer, "RAW_SILK", "8.000", "1000.00"));     // 8000

        // AC2: above the item's outstanding; AC3: together above the payment; nothing stored
        payWith(customer, "20000.00", cash, alloc(x, "10000.01")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        payWith(customer, "12000.00", cash, alloc(x, "10000.00") + "," + alloc(y, "5000.00"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        // the same target twice is a malformed request; an unknown target is 404; a purchase is not a customer's
        payWith(customer, "20000.00", cash, alloc(x, "1.00") + "," + alloc(x, "2.00")).andExpect(status().isBadRequest());
        payWith(customer, "20000.00", cash, alloc(UUID.randomUUID().toString(), "1.00")).andExpect(status().isNotFound());
        payWith(customer, "20000.00", cash, """
                {"targetType":"PURCHASE","targetId":"%s","amount":1.00}""".formatted(UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ALLOCATION"));
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();

        // AC1: 10000 to X and 5000 to Y of a 20000 payment -> stored as asked (Y before X is fine), 5000 advance
        payWith(customer, "20000.00", cash, alloc(y, "5000.00") + "," + alloc(x, "10000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedAmount").value(15000.00))
                .andExpect(jsonPath("$.unallocatedAmount").value(5000.00));
        // X is now fully paid: any further allocation to it is above its outstanding
        payWith(customer, "1.00", cash, alloc(x, "1.00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
    }

    @Test
    @DisplayName("FR-PAY-03 explicit allocation other customers item returns422 invalid allocation")
    void explicitAllocation_otherCustomersItem_returns422InvalidAllocation() throws Exception {
        UUID customer = data.customer().id();
        UUID other = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        String theirs = itemOf(confirmedOrder(other, "RAW_SILK", "10.000", "1000.00"));
        String placed = itemOf(createOrder(customer, "RAW_SILK", "10.000", "1000.00"));  // not owed yet (Q2)

        payWith(customer, "500.00", cash, alloc(theirs, "500.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ALLOCATION"));
        payWith(customer, "500.00", cash, alloc(placed, "500.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ALLOCATION"));
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-PAY-05 confirm order applies available advance oldest first")
    void confirmOrder_appliesAvailableAdvanceOldestFirst() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        // 40000 of advance in two payments: the older (10000, three days ago) is used first
        String older = body(pay(customer, "10000.00", "CASH", cash, today().minusDays(3).toString())
                .andExpect(status().isCreated())).get("id").asString();
        String newer = body(pay(customer, "30000.00", "CASH", cash, today().minusDays(1).toString())
                .andExpect(status().isCreated())).get("id").asString();
        // AC3: a reversed payment's advance is never applied
        String reversed = body(pay(customer, "99999.00", "CASH", cash, today().minusDays(5).toString())
                .andExpect(status().isCreated())).get("id").asString();
        jdbc.update("update payments set status = 'REVERSED', reversed_at = now(), reversal_reason = 'test'"
                + " where id = ?::uuid", reversed);

        // AC1: confirming a 25000 order applies 25000 at once: older pays 10000, newer 15000; the order is PAID
        String order = createOrder(customer, "KORA_WARP", "25.000", "1000.00");
        asOwner(postJson("/api/v1/orders/" + order + "/confirm", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.allocatedAmount").value(25000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(0.0));
        asOwner(get("/api/v1/payments/" + older)).andExpect(jsonPath("$.unallocatedAmount").value(0.0));
        asOwner(get("/api/v1/payments/" + newer)).andExpect(jsonPath("$.unallocatedAmount").value(15000.00))
                .andExpect(jsonPath("$.allocations[0].amount").value(15000.00));
        asOwner(get("/api/v1/payments/" + reversed)).andExpect(jsonPath("$.allocations.length()").value(0));
        // no money moved: only allocations were added (cash is still 10000 + 30000 + 99999)
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(139999.00));

        // AC2: without advance a confirmation creates no allocation
        UUID fresh = data.customer().id();
        String unpaid = createOrder(fresh, "KORA_WARP", "1.000", "1000.00");
        asOwner(postJson("/api/v1/orders/" + unpaid + "/confirm", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-PAY-14 get returns allocations and transaction id")
    void get_returnsAllocationsAndTransactionId() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00");
        confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00");
        String id = body(pay(customer, "15000.00", "CASH", cash, null).andExpect(status().isCreated()))
                .get("id").asString();
        String tx = jdbc.queryForObject("select id::text from financial_transactions where payment_id = ?::uuid",
                String.class, id);

        // AC1: both allocations with their order numbers, and the ledger row
        asOwner(get("/api/v1/payments/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financialTransactionId").value(tx))
                .andExpect(jsonPath("$.reversalTransactionId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].targetLabel").value(Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.allocations[1].amount").value(5000.00));
        // the list, filtered and sorted
        pay(data.customer().id(), "100.00", "CASH", cash, today().minusDays(2).toString()).andExpect(status().isCreated());
        asOwner(get("/api/v1/payments?partyId=" + customer)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].unallocatedAmount").value(0.0))
                .andExpect(jsonPath("$.content[0].account.accountName").value("Cash"));
        asOwner(get("/api/v1/payments?sort=amount,asc")).andExpect(jsonPath("$.content[0].amount").value(100.00));
        asOwner(get("/api/v1/payments?from=" + today() + "&to=" + today())).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/payments?status=REVERSED")).andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/payments?sort=notes")).andExpect(status().isBadRequest());
        // AC3: STAFF cannot list payments
        mockMvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PAY-23 allocations listed after reversal as released")
    void allocations_listedAfterReversalAsReleased() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        String order = confirmedOrder(customer, "RAW_SILK", "20.000", "1000.00");
        String id = body(pay(customer, "20000.00", "CASH", cash, null).andExpect(status().isCreated()))
                .get("id").asString();
        asOwner(get("/api/v1/payments/" + id + "/allocations")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].released").value(false));

        asOwner(postWithKey("/api/v1/payments/" + id + "/reverse", "{\"reason\":\"entered twice\"}"))
                .andExpect(status().isOk());

        // AC1: the allocation is still there, marked released; the order is owed again
        asOwner(get("/api/v1/payments/" + id + "/allocations")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].released").value(true))
                .andExpect(jsonPath("$[0].amount").value(20000.00));
        asOwner(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.outstandingAmount").value(20000.00))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        // AC2: allocations cannot be deleted
        asOwner(delete("/api/v1/payments/" + id + "/allocations")).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/payments/" + UUID.randomUUID() + "/allocations")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PAY-16 put or delete is not supported")
    void putOrDelete_isNotSupported() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        String id = body(pay(customer, "100.00", "CASH", cash, null).andExpect(status().isCreated())).get("id").asString();

        asOwner(put("/api/v1/payments/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1.00}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        asOwner(patch("/api/v1/payments/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1.00}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/payments/" + id)).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/payments/" + id)).andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("RECORDED"));
    }

    private String itemOf(String orderId) throws Exception {
        return body(asOwner(get("/api/v1/orders/" + orderId))).get("items").get(0).get("id").asString();
    }

    private static String alloc(String itemId, String amount) {
        return """
                {"targetType":"ORDER_ITEM","targetId":"%s","amount":%s}""".formatted(itemId, amount);
    }

    private ResultActions payWith(UUID party, String amount, String account, String allocations) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"CASH","financialAccountId":"%s",
                 "allocations":[%s]}""".formatted(party, amount, account, allocations)));
    }

    private ResultActions pay(UUID party, String amount, String method, String account, String date) throws Exception {
        return asOwner(postWithKey("/api/v1/payments", body(party, amount, method, account, date)));
    }

    private static String body(UUID party, String amount, String method, String account, String date) {
        return """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"%s}"""
                .formatted(party, amount, method, account, date == null ? "" : ",\"paymentDate\":\"" + date + "\"");
    }
}

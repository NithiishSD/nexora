package com.nexora.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Year;
import java.util.UUID;
import java.util.function.Consumer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.support.AbstractApiTest;

import tools.jackson.databind.JsonNode;

class OrderApiTest extends AbstractApiTest {

    @Autowired
    CustomerOrderRepository orderRepository;

    @Test
    @DisplayName("FR-ORD-01 FR-ORD-02 FR-ORD-18 create with items returns201 with order number and amounts")
    void create_withItems_returns201WithOrderNumberAndAmounts() throws Exception {
        UUID customer = data.customer().id();

        // FR-ORD-01 AC1 + FR-ORD-02 AC1/AC2/AC3: amounts computed by the backend, a client "amount" is ignored
        JsonNode created = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s","amount":1.00,"status":"DELIVERED",
                 "items":[{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00,"amount":1.00},
                          {"productType":"SILK_WARP","requiredWeightKg":12.345,"ratePerKg":333.33,
                           "warpSpecification":"48 inch, 2400 ends"}]}"""
                .formatted(customer, today(), today().plusDays(13))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/orders/")))
                .andExpect(jsonPath("$.orderNumber").value("ORD-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items[0].lineNo").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(85000.00))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[1].amount").value(4114.96))
                .andExpect(jsonPath("$.items[1].warpSpecification").value("48 inch, 2400 ends"))
                .andExpect(jsonPath("$.totalAmount").value(89114.96))
                // assumption Q2: a PLACED order owes nothing yet
                .andExpect(jsonPath("$.outstandingAmount").value(0.0))
                .andExpect(jsonPath("$.customer.id").value(customer.toString())));
        assertThat(jdbc.queryForObject("select sum(amount) from order_items", java.math.BigDecimal.class))
                .isEqualByComparingTo("89114.96");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("89114.96");

        // FR-ORD-18: the next order gets the next number
        createOrder(customer, "RAW_SILK", "1.000", "1.00");
        assertThat(jdbc.queryForList("select order_number from customer_orders order by order_number", String.class))
                .containsExactly("ORD-" + Year.now(clock) + "-0001", "ORD-" + Year.now(clock) + "-0002");

        // FR-ORD-01 AC2/AC3 and FR-ORD-02 AC4: no items, weight 0, four decimals -> 400, nothing stored
        order(customer, "[]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        order(customer, item("0.000", "1.00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].requiredWeightKg"));
        order(customer, item("12.3456", "1.00")).andExpect(status().isBadRequest());
        order(customer, item("1.000", "-1.00")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-ORD-01 create for non customer returns422 invalid party role")
    void create_forNonCustomer_returns422InvalidPartyRole() throws Exception {
        // AC4: a supplier is not a customer
        order(data.supplier().id(), item("1.000", "1.00"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        // AC5: an inactive customer
        UUID inactive = data.customer().id();
        asOwner(postJson("/api/v1/parties/" + inactive + "/deactivate", "{}")).andExpect(status().isOk());
        order(inactive, item("1.000", "1.00"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARTY_INACTIVE"));
        // unknown party
        order(UUID.randomUUID(), item("1.000", "1.00")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Integer.class)).isZero();
        // the number was not used up by the rejected requests
        createOrder(data.customer().id(), "RAW_SILK", "1.000", "1.00");
        assertThat(jdbc.queryForObject("select order_number from customer_orders", String.class))
                .endsWith("-0001");
    }

    @Test
    @DisplayName("FR-ORD-01 create expected date before order date returns400")
    void create_expectedDateBeforeOrderDate_returns400() throws Exception {
        asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":"%s",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":1.00}]}"""
                .formatted(data.customer().id(), today(), today().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("requiredDate"));
        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-ORD-03 rate is stored on item and never recalculated")
    void rate_isStoredOnItemAndNeverRecalculated() throws Exception {
        UUID customer = data.customer().id();
        String first = createOrder(customer, "KORA_WARP", "100.000", "850.00");
        // AC1: a later order at a new price does not touch the first one
        createOrder(customer, "KORA_WARP", "100.000", "900.00");
        asOwner(get("/api/v1/orders/" + first))
                .andExpect(jsonPath("$.items[0].ratePerKg").value(850.00))
                .andExpect(jsonPath("$.items[0].amount").value(85000.00))
                .andExpect(jsonPath("$.totalAmount").value(85000.00));
        assertThat(jdbc.queryForObject("select rate_per_kg from order_items i join customer_orders o on o.id = i.order_id"
                + " where o.id = ?::uuid", java.math.BigDecimal.class, first)).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("FR-ORD-04 NFR-USE-02 get returns items payment status and outstanding")
    void get_returnsItemsPaymentStatusAndOutstanding() throws Exception {
        UUID customer = data.customer().id();
        String id = createOrder(customer, "KORA_WARP", "100.000", "850.00");
        transition(id, CustomerOrder::confirm);

        // every figure the client shows comes from the server: total, allocated, outstanding, statuses, lock flags
        asOwner(get("/api/v1/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(85000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(0.0))
                .andExpect(jsonPath("$.outstandingAmount").value(85000.00))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.modifiable").value(true))
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.items[0].outstandingAmount").value(85000.00))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.version").doesNotExist());
        // AC2: unknown id
        asOwner(get("/api/v1/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        // a staff user may read orders (ORDER_VIEW)
        mockMvc.perform(get("/api/v1/orders/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.staff()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-ORD-05 list filtered by customer status and pending")
    void list_filteredByCustomerStatusAndPending() throws Exception {
        UUID customer = data.customer().id();
        UUID other = data.customer().id();
        String placed = createOrder(customer, "RAW_SILK", "1.000", "1.00");
        String confirmed = createOrder(customer, "KORA_WARP", "1.000", "1.00");
        String delivered = createOrder(customer, "RAW_SILK", "1.000", "1.00");
        String cancelled = createOrder(customer, "RAW_SILK", "1.000", "1.00");
        String othersOrder = createOrder(other, "RAW_SILK", "1.000", "1.00");
        transition(confirmed, CustomerOrder::confirm);
        transition(delivered, o -> {
            o.confirm();
            o.markReady();
            o.deliver(today());
        });
        transition(cancelled, o -> o.cancel(false));

        // AC1: pending = PLACED, CONFIRMED, IN_PROGRESS, READY only
        assertThat(ids("/api/v1/orders?pending=true&customerId=" + customer)).containsExactlyInAnyOrder(placed, confirmed);
        assertThat(ids("/api/v1/orders?pending=false&customerId=" + customer)).containsExactlyInAnyOrder(delivered, cancelled);
        assertThat(ids("/api/v1/orders?status=CONFIRMED")).containsExactly(confirmed);
        assertThat(ids("/api/v1/orders?customerId=" + other)).containsExactly(othersOrder);
        assertThat(ids("/api/v1/orders?productType=KORA_WARP")).containsExactly(confirmed);
        assertThat(ids("/api/v1/orders?from=" + today() + "&to=" + today())).hasSize(5);
        asOwner(get("/api/v1/orders?customerId=" + other))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.totalElements").value(1));
        // AC2: an unknown status is malformed; an unknown sort property and a backwards range are validation errors
        asOwner(get("/api/v1/orders?status=UNKNOWN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        asOwner(get("/api/v1/orders?sort=version,asc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        asOwner(get("/api/v1/orders?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    @DisplayName("FR-ORD-16 list late flag when expected date passed")
    void list_lateFlag_whenExpectedDatePassed() throws Exception {
        UUID customer = data.customer().id();
        // AC1: expected date in the past, still in progress -> late
        String late = createOrder(customer, today().minusDays(5), today().minusDays(2));
        transition(late, o -> {
            o.confirm();
            o.startWork();
        });
        // AC2: delivered -> not late; AC3: cancelled past its date -> not late; no date -> never late
        String delivered = createOrder(customer, today().minusDays(5), today().minusDays(2));
        transition(delivered, o -> {
            o.confirm();
            o.markReady();
            o.deliver(today());
        });
        String cancelled = createOrder(customer, today().minusDays(5), today().minusDays(2));
        transition(cancelled, o -> o.cancel(false));
        String onTime = createOrder(customer, today(), today().plusDays(3));
        String noDate = createOrder(customer, today(), null);

        asOwner(get("/api/v1/orders/" + late)).andExpect(jsonPath("$.late").value(true));
        asOwner(get("/api/v1/orders/" + delivered)).andExpect(jsonPath("$.late").value(false));
        asOwner(get("/api/v1/orders/" + cancelled)).andExpect(jsonPath("$.late").value(false));
        assertThat(ids("/api/v1/orders?late=true")).containsExactly(late);
        assertThat(ids("/api/v1/orders?late=false")).containsExactlyInAnyOrder(delivered, cancelled, onTime, noDate);
    }

    @Test
    @DisplayName("FR-ORD-06 patch placed order recalculates amounts")
    void patch_placedOrder_recalculatesAmounts() throws Exception {
        UUID customer = data.customer().id();
        String id = createOrder(customer, "KORA_WARP", "100.000", "850.00");

        // AC1: the weight changes, the backend recomputes the amount; customerId/status/amount in the body are ignored
        asOwner(patchJson("/api/v1/orders/" + id, """
                {"customerId":"%s","status":"DELIVERED","totalAmount":1.00,"notes":"call before dispatch",
                 "items":[{"productType":"KORA_WARP","requiredWeightKg":110.000,"ratePerKg":850.00}]}"""
                .formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amount").value(93500.00))
                .andExpect(jsonPath("$.totalAmount").value(93500.00))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.notes").value("call before dispatch"))
                .andExpect(jsonPath("$.customer.id").value(customer.toString()));
        // only the notes: the items stay; "" clears the notes; a second line is added and the line keeps its id
        String itemId = body(asOwner(get("/api/v1/orders/" + id))).get("items").get(0).get("id").asString();
        asOwner(patchJson("/api/v1/orders/" + id, """
                {"notes":"","items":[{"productType":"KORA_WARP","requiredWeightKg":110.000,"ratePerKg":850.00},
                                     {"productType":"RAW_SILK","requiredWeightKg":1.500,"ratePerKg":900.00}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].id").value(itemId))
                .andExpect(jsonPath("$.items[1].lineNo").value(2))
                .andExpect(jsonPath("$.totalAmount").value(94850.00));
        // an empty item list and a bad weight are 400, nothing changes
        asOwner(patchJson("/api/v1/orders/" + id, "{\"items\":[]}")).andExpect(status().isBadRequest());
        asOwner(patchJson("/api/v1/orders/" + id, "{\"items\":" + item("0.000", "1.00") + "}"))
                .andExpect(status().isBadRequest());
        asOwner(get("/api/v1/orders/" + id)).andExpect(jsonPath("$.totalAmount").value(94850.00));
        asOwner(patchJson("/api/v1/orders/" + UUID.randomUUID(), "{}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-ORD-06 FR-ORD-08 patch locked order returns409 order locked")
    void patch_lockedOrder_returns409OrderLocked() throws Exception {
        String id = createOrder(data.customer().id(), "KORA_WARP", "100.000", "850.00");
        transition(id, o -> {
            o.confirm();
            o.startWork();
        });

        // AC2: in progress -> ORDER_LOCKED for edit and cancel, nothing changes
        asOwner(patchJson("/api/v1/orders/" + id, "{\"items\":" + item("1.000", "1.00") + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_LOCKED"));
        asOwner(postJson("/api/v1/orders/" + id + "/cancel", "{\"reason\":\"customer withdrew\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_LOCKED"));
        asOwner(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.modifiable").value(false))
                .andExpect(jsonPath("$.totalAmount").value(85000.00));
        // a cancelled order cannot be edited at all
        String cancelled = createOrder(data.customer().id(), "RAW_SILK", "1.000", "1.00");
        transition(cancelled, o -> o.cancel(false));
        asOwner(patchJson("/api/v1/orders/" + cancelled, "{\"notes\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-ORD-07 confirm placed order becomes obligation")
    void confirm_placedOrder_becomesObligation() throws Exception {
        String id = createOrder(data.customer().id(), "KORA_WARP", "100.000", "850.00");

        // AC1: PLACED -> CONFIRMED and the order is owed from now on (assumption Q2)
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.outstandingAmount").value(85000.00))
                .andExpect(jsonPath("$.items[0].outstandingAmount").value(85000.00));
        // AC2: confirming again (or a delivered order) is an illegal transition
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postJson("/api/v1/orders/" + UUID.randomUUID() + "/confirm", "")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-ORD-10 ready from confirmed when filled from stock")
    void ready_fromConfirmed_whenFilledFromStock() throws Exception {
        String id = createOrder(data.customer().id(), "RAW_SILK", "10.000", "900.00");

        // AC2: PLACED cannot be marked ready
        asOwner(postJson("/api/v1/orders/" + id + "/ready", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // plan decision R5: CONFIRMED -> READY when filled from stock; the items follow
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/ready", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("READY"))
                .andExpect(jsonPath("$.locked").value(true));
        // AC1: IN_PROGRESS -> READY
        String inProgress = createOrder(data.customer().id(), "SILK_WARP", "10.000", "900.00");
        transition(inProgress, o -> {
            o.confirm();
            o.startWork();
        });
        asOwner(postJson("/api/v1/orders/" + inProgress + "/ready", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @DisplayName("FR-ORD-13 complete after delivery independent of payment")
    void complete_afterDelivery_independentOfPayment() throws Exception {
        String id = createOrder(data.customer().id(), "RAW_SILK", "10.000", "900.00");
        transition(id, o -> {
            o.confirm();
            o.markReady();
        });
        // AC2: READY cannot be completed
        asOwner(postJson("/api/v1/orders/" + id + "/complete", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        transition(id, o -> o.deliver(today()));
        // AC1: completed although nothing is paid; the payment status stays what it was and the order is still owed
        asOwner(postJson("/api/v1/orders/" + id + "/complete", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(9000.00));
    }

    @Test
    @DisplayName("FR-ORD-09 cancel with allocations returns409 invalid state transition")
    void cancel_withAllocations_returns409InvalidStateTransition() throws Exception {
        // AC1: a confirmed order without allocations is cancelled, its items too, and it owes nothing any more
        String id = createOrder(data.customer().id(), "RAW_SILK", "10.000", "900.00");
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());
        asOwner(postJson("/api/v1/orders/" + id + "/cancel", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        asOwner(postJson("/api/v1/orders/" + id + "/cancel", "{\"reason\":\"Customer withdrew\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.outstandingAmount").value(0.0));
        // AC4: a delivered order cannot be cancelled
        String delivered = createOrder(data.customer().id(), "RAW_SILK", "1.000", "1.00");
        transition(delivered, o -> {
            o.confirm();
            o.markReady();
            o.deliver(today());
        });
        asOwner(postJson("/api/v1/orders/" + delivered + "/cancel", "{\"reason\":\"too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-ORD-14 payment status derived from allocations")
    void paymentStatus_derivedFromAllocations() throws Exception {
        UUID customer = data.customer().id();
        String cash = createAccount("Cash", "CASH", "0.00");
        // AC2: a paymentStatus in the body has no effect on create or patch
        String id = body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","paymentStatus":"PAID",
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":10.000,"ratePerKg":100.00},
                          {"productType":"VUDA_WARP","requiredWeightKg":5.000,"ratePerKg":100.00}]}"""
                .formatted(customer, today()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))).get("id").asString();
        asOwner(patchJson("/api/v1/orders/" + id, "{\"paymentStatus\":\"PAID\",\"notes\":\"x\"}"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        asOwner(postJson("/api/v1/orders/" + id + "/confirm", "")).andExpect(status().isOk());

        // 1000 settles line 1 (PAID) and leaves line 2 open: the order is PARTIALLY_PAID
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":1000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(customer, cash))).andExpect(status().isCreated());
        asOwner(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.items[1].paymentStatus").value("UNPAID"));
        // AC1: allocations equal to the amount -> PAID, on the list too
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":500.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(customer, cash))).andExpect(status().isCreated());
        asOwner(get("/api/v1/orders/" + id)).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.items[1].paymentStatus").value("PAID"));
        asOwner(get("/api/v1/orders?customerId=" + customer))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("FR-ORD-17 delete is not supported")
    void delete_isNotSupported() throws Exception {
        String id = createOrder(data.customer().id(), "RAW_SILK", "1.000", "1.00");
        asOwner(delete("/api/v1/orders/" + id))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        asOwner(put("/api/v1/orders/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Integer.class)).isEqualTo(1);
    }

    // ---- helpers -------------------------------------------------------------------------

    private String createOrder(UUID customerId, LocalDate orderDate, LocalDate requiredDate) throws Exception {
        String required = requiredDate == null ? "null" : "\"" + requiredDate + "\"";
        return body(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","requiredDate":%s,
                 "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":1.00}]}"""
                .formatted(customerId, orderDate, required))).andExpect(status().isCreated())).get("id").asString();
    }

    private org.springframework.test.web.servlet.ResultActions order(UUID customerId, String items) throws Exception {
        return asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":%s}""".formatted(customerId, today(), items)));
    }

    private static String item(String kg, String rate) {
        return "[{\"productType\":\"RAW_SILK\",\"requiredWeightKg\":" + kg + ",\"ratePerKg\":" + rate + "}]";
    }

    // Moves an order through domain transitions whose endpoints are tested elsewhere (list filters need the states).
    private void transition(String orderId, Consumer<CustomerOrder> change) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            CustomerOrder order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
            change.accept(order);
        });
    }

    private java.util.List<String> ids(String path) throws Exception {
        JsonNode content = body(asOwner(get(path)).andExpect(status().isOk())).get("content");
        java.util.List<String> ids = new java.util.ArrayList<>();
        content.forEach(n -> ids.add(n.get("id").asString()));
        return ids;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchJson(String path,
                                                                                                    String body) {
        return patch(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

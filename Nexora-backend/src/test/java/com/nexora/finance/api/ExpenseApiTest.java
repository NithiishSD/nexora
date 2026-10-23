package com.nexora.finance.api;

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
import java.time.YearMonth;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class ExpenseApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-11 record creates out transaction")
    void record_createsOutTransaction() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "10000.00");
        String cash = createAccount("Cash", "CASH", "1000.00");

        // AC1: 1500 by UPI from the bank -> bank 8500, one EXPENSE OUT row, listed
        String id = body(expense("TRANSPORT", "BUSINESS", "1500.00", today(), bank, "UPI")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/expenses/")))
                .andExpect(jsonPath("$.expenseNumber").value("EXP-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.recurrence").value("ONE_TIME"))
                .andExpect(jsonPath("$.account.id").value(bank))
                .andExpect(jsonPath("$.financialTransactionId").isNotEmpty())).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(8500.00));
        assertThat(jdbc.queryForObject("select transaction_type || ' ' || direction from financial_transactions"
                + " where expense_id = ?::uuid", String.class, id)).isEqualTo("EXPENSE OUT");
        asOwner(get("/api/v1/expenses")).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/expenses/" + id)).andExpect(jsonPath("$.amount").value(1500.00));

        // AC3: amount 0 -> 400; AC4: CASH on a bank account -> 422; more than the account holds -> 409
        expense("TRANSPORT", null, "0.00", today(), bank, "UPI").andExpect(status().isBadRequest());
        expense("TRANSPORT", null, "10.00", today(), bank, "CASH").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        expense("RENT", null, "2000.00", today(), cash, "CASH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.queryForObject("select count(*) from expenses", Integer.class)).isEqualTo(1);
        // AC5 / FR-FIN-12 AC2: Staff may record neither an expense nor a personal drawing
        for (String kind : new String[] {"BUSINESS", "PERSONAL"}) {
            mockMvc.perform(postWithKey("/api/v1/expenses", body("OTHER", kind, "1.00", today(), cash, "CASH"))
                            .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("FR-FIN-12 personal drawing excluded from business totals")
    void personalDrawing_excludedFromBusinessTotals() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        expense("TRANSPORT", "BUSINESS", "3000.00", today(), cash, "CASH").andExpect(status().isCreated());
        expense("ELECTRICITY", "BUSINESS", "2000.00", today(), cash, "CASH").andExpect(status().isCreated());
        String drawing = body(expense("OTHER", "PERSONAL", "20000.00", today(), cash, "CASH")
                .andExpect(status().isCreated())).get("id").asString();

        // AC1: business 5000, personal 20000; the drawing reduced cash like any expense, as PERSONAL_DRAWING
        asOwner(get("/api/v1/expenses/summary?from=" + today().withDayOfMonth(1) + "&to=" + today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessTotal").value(5000.00))
                .andExpect(jsonPath("$.personalDrawingsTotal").value(20000.00))
                .andExpect(jsonPath("$.byCategory.length()").value(2))
                .andExpect(jsonPath("$.byCategory[?(@.category == 'OTHER')]").isEmpty());
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(25000.00));
        assertThat(jdbc.queryForObject("select transaction_type from financial_transactions where expense_id = ?::uuid",
                String.class, drawing)).isEqualTo("PERSONAL_DRAWING");
    }

    @Test
    @DisplayName("FR-FIN-13 list and summary by category and kind")
    void list_andSummaryByCategoryAndKind() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        LocalDate lastMonth = today().minusMonths(1).withDayOfMonth(1);
        expense("TRANSPORT", "BUSINESS", "1000.00", lastMonth, cash, "CASH").andExpect(status().isCreated());
        expense("TRANSPORT", "BUSINESS", "2500.00", today(), cash, "CASH").andExpect(status().isCreated());
        expense("RENT", "BUSINESS", "8000.00", today(), cash, "CASH").andExpect(status().isCreated());
        expense("OTHER", "PERSONAL", "4000.00", lastMonth, cash, "CASH").andExpect(status().isCreated());
        String reversed = body(expense("PACKING", "BUSINESS", "300.00", today(), cash, "CASH")).get("id").asString();
        asOwner(postWithKey("/api/v1/expenses/" + reversed + "/reverse", "{\"reason\":\"wrong\"}"))
                .andExpect(status().isOk());

        // AC1: per category (business) and per month over the period; the reversed one is left out
        asOwner(get("/api/v1/expenses/summary?from=" + lastMonth + "&to=" + today()))
                .andExpect(jsonPath("$.businessTotal").value(11500.00))
                .andExpect(jsonPath("$.personalDrawingsTotal").value(4000.00))
                .andExpect(jsonPath("$.byCategory[?(@.category == 'TRANSPORT')].total").value(3500.0))
                .andExpect(jsonPath("$.byCategory[?(@.category == 'RENT')].total").value(8000.0))
                .andExpect(jsonPath("$.byMonth.length()").value(2))
                .andExpect(jsonPath("$.byMonth[0].month").value(YearMonth.from(lastMonth).toString()))
                .andExpect(jsonPath("$.byMonth[0].businessTotal").value(1000.00))
                .andExpect(jsonPath("$.byMonth[0].personalTotal").value(4000.00))
                .andExpect(jsonPath("$.byMonth[1].businessTotal").value(10500.00));
        // list filters
        asOwner(get("/api/v1/expenses?category=TRANSPORT")).andExpect(jsonPath("$.totalElements").value(2));
        asOwner(get("/api/v1/expenses?expenseKind=PERSONAL")).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/expenses?status=REVERSED")).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/expenses?accountId=" + cash + "&from=" + today().withDayOfMonth(1)))
                .andExpect(jsonPath("$.totalElements").value(3));
        asOwner(get("/api/v1/expenses?sort=amount,desc")).andExpect(jsonPath("$.content[0].amount").value(8000.00));
        asOwner(get("/api/v1/expenses?sort=description")).andExpect(status().isBadRequest());
        // AC2: from after to 400; the summary needs both dates
        asOwner(get("/api/v1/expenses/summary?from=" + today() + "&to=" + lastMonth)).andExpect(status().isBadRequest());
        asOwner(get("/api/v1/expenses/summary?from=" + today())).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-FIN-14 FR-FIN-17 reverse adds compensating in transaction")
    void reverse_addsCompensatingInTransaction() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "10000.00");
        String id = body(expense("TRANSPORT", null, "1500.00", today(), bank, "UPI")).get("id").asString();

        // AC1: balance back by 1500, expense REVERSED; FR-FIN-17: original row kept, REVERSAL IN points at it
        asOwner(postWithKey("/api/v1/expenses/" + id + "/reverse", "{\"reason\":\"Wrong amount\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("Wrong amount"))
                .andExpect(jsonPath("$.reversedAt").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(1500.00));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(10000.00));
        assertThat(jdbc.queryForList("select transaction_type || ' ' || direction || ' ' || (reverses_transaction_id"
                + " is not null) from financial_transactions where expense_id = ?::uuid order by transaction_type",
                String.class, id)).containsExactly("EXPENSE OUT false", "REVERSAL IN true");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'EXPENSE_REVERSED'"
                + " and entity_id = ?::uuid", Integer.class, id)).isEqualTo(1);
        // AC2: twice -> 409; blank reason 400; AC3: no PUT, PATCH or DELETE
        asOwner(postWithKey("/api/v1/expenses/" + id + "/reverse", "{\"reason\":\"again\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postWithKey("/api/v1/expenses/" + id + "/reverse", "{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        asOwner(put("/api/v1/expenses/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(patch("/api/v1/expenses/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/expenses/" + id)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("FR-FIN-18 record replay with same key creates one expense")
    void record_replayWithSameKey_createsOneExpense() throws Exception {
        String cash = createAccount("Cash", "CASH", "5000.00");

        // AC1: posted twice with one key -> one expense, cash down once
        replayTwice("/api/v1/expenses", body("TRANSPORT", null, "700.00", today(), cash, "CASH"), 201);
        assertThat(jdbc.queryForObject("select count(*) from expenses", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(4300.00));
        // AC2: the same key with another amount -> 409 DUPLICATE_REQUEST
        String key = java.util.UUID.randomUUID().toString();
        asOwner(postWithKey("/api/v1/expenses", body("TRANSPORT", null, "10.00", today(), cash, "CASH"), key))
                .andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/expenses", body("TRANSPORT", null, "20.00", today(), cash, "CASH"), key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
        // no key -> 400
        asOwner(postJson("/api/v1/expenses", body("TRANSPORT", null, "1.00", today(), cash, "CASH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-FIN-18 reverse replay with same key returns stored response")
    void reverse_replayWithSameKey_returnsStoredResponse() throws Exception {
        String cash = createAccount("Cash", "CASH", "5000.00");
        String id = body(expense("TRANSPORT", null, "700.00", today(), cash, "CASH")).get("id").asString();

        // the retry gets the first answer (200 REVERSED), not a 409, and money comes back once
        replayTwice("/api/v1/expenses/" + id + "/reverse", "{\"reason\":\"duplicate entry\"}", 200);
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(5000.00));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'REVERSAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-FIN-19 unknown category returns400 malformed request")
    void unknownCategory_returns400MalformedRequest() throws Exception {
        String cash = createAccount("Cash", "CASH", "5000.00");

        // Unknown category is rejected with MALFORMED_REQUEST
        expense("GAMBLING", null, "100.00", today(), cash, "CASH").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        // After adding category, it can be used
        asOwner(postJson("/api/v1/expense-categories", "{\"name\":\"FUEL\"}"))
                .andExpect(status().isCreated());
        expense("FUEL", null, "100.00", today(), cash, "CASH").andExpect(status().isCreated());
    }

    private ResultActions expense(String category, String kind, String amount, LocalDate date, String account,
                                  String method) throws Exception {
        return asOwner(postWithKey("/api/v1/expenses", body(category, kind, amount, date, account, method)));
    }

    private static String body(String category, String kind, String amount, LocalDate date, String account,
                               String method) {
        return """
                {"category":"%s",%s"amount":%s,"expenseDate":"%s","financialAccountId":"%s","paymentMethod":"%s"}"""
                .formatted(category, kind == null ? "" : "\"expenseKind\":\"" + kind + "\",", amount, date, account,
                        method);
    }
}

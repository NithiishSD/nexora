package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class LoansAndChitsReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-14 report loans and chits")
    void report_loansAndChits() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String cash = createAccount("Cash", "CASH", "100000.00");
        // a loan of 100000: 20000 principal repaid, 3000 interest paid
        String loan = loan(bank);
        money("loans/" + loan + "/principal-repayments", "20000.00", bank, "BANK_TRANSFER");
        money("loans/" + loan + "/interest-payments", "3000.00", bank, "BANK_TRANSFER");
        // a chit of 20 months at 25000 with 6 paid before go-live and one more now
        String chit = chit();
        money("chits/" + chit + "/contributions", "25000.00", cash, "CASH");

        // AC1: both with the figures of their detail endpoints
        asOwner(get("/api/v1/reports/loans-and-chits")).andExpect(status().isOk())
                .andExpect(jsonPath("$.loans.length()").value(1))
                .andExpect(jsonPath("$.loans[0].principal").value(100000.00))
                .andExpect(jsonPath("$.loans[0].outstandingPrincipal").value(80000.00))
                .andExpect(jsonPath("$.loans[0].interestPaid").value(3000.00))
                .andExpect(jsonPath("$.loans[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.chits.length()").value(1))
                .andExpect(jsonPath("$.chits[0].totalContributed").value(175000.00))
                .andExpect(jsonPath("$.chits[0].remainingExpectedContributions").value(13))
                .andExpect(jsonPath("$.chits[0].payoutReceived").doesNotExist());
        asOwner(get("/api/v1/loans/" + loan)).andExpect(jsonPath("$.outstandingPrincipal").value(80000.00))
                .andExpect(jsonPath("$.interestPaid").value(3000.00));
        asOwner(get("/api/v1/chits/" + chit)).andExpect(jsonPath("$.totalContributed").value(175000.00))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(13));

        // AC2: without REPORT_VIEW -> 403
        mockMvc.perform(get("/api/v1/reports/loans-and-chits").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.staff()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-CHIT-07 chit movements are investment not expense")
    void chitMovements_areInvestmentNotExpense() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String chit = chit();
        // AC2: a 25000 contribution; AC1: a 460000 payout in the same month
        money("chits/" + chit + "/contributions", "25000.00", cash, "CASH");
        money("chits/" + chit + "/payout", "460000.00", cash, "CASH");

        String period = "?from=" + today() + "&to=" + today();
        // neither an expense nor revenue
        asOwner(get("/api/v1/reports/expenses" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessTotal").value(0.0))
                .andExpect(jsonPath("$.personalDrawingsTotal").value(0.0))
                .andExpect(jsonPath("$.interestCost").value(0.0));
        asOwner(get("/api/v1/reports/sales" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenueDelivered").value(0.0));
        // shown as investment movements: the chit report and the cash movements by type
        asOwner(get("/api/v1/reports/loans-and-chits")).andExpect(jsonPath("$.chits[0].payoutReceived").value(460000.00))
                .andExpect(jsonPath("$.chits[0].status").value("MATURED"));
        asOwner(get("/api/v1/reports/cash-bank" + period)).andExpect(jsonPath("$[0].outByType[0].transactionType")
                .value("CHIT_CONTRIBUTION")).andExpect(jsonPath("$[0].inByType[?(@.transactionType == 'CHIT_PAYOUT')]"
                + ".amount").value(460000.00));
    }

    private String loan(String bank) throws Exception {
        return body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":100000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(data.lender().id(), today(), bank))).andExpect(status().isCreated())).get("id").asString();
    }

    private String chit() throws Exception {
        return body(asOwner(postJson("/api/v1/chits", """
                {"chitName":"Chit A","totalAmount":500000.00,"monthlyAmount":25000.00,"durationMonths":20,
                 "startDate":"%s","priorContributionsCount":6,"priorContributionsAmount":150000.00}"""
                .formatted(today().minusMonths(6)))).andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions money(String path, String amount, String account, String method) throws Exception {
        String date = path.startsWith("chits") ? "entryDate" : "paymentDate";
        return asOwner(postWithKey("/api/v1/" + path, """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"%s","%s":"%s"}"""
                .formatted(amount, account, method, date, today()))).andExpect(status().isCreated());
    }
}

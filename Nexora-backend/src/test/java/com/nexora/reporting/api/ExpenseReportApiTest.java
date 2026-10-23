package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

class ExpenseReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-08 report by category kind and month with interest")
    void report_byCategoryKindAndMonthWithInterest() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        // business 2000 transport + 3000 electricity; a 1000 packing expense reversed; a 20000 personal drawing
        expense("TRANSPORT", "BUSINESS", "2000.00", cash);
        expense("ELECTRICITY", "BUSINESS", "3000.00", cash);
        String wrong = body(expense("PACKING", "BUSINESS", "1000.00", cash)).get("id").asString();
        asOwner(postWithKey("/api/v1/expenses/" + wrong + "/reverse", "{\"reason\":\"wrong\"}"))
                .andExpect(status().isOk());
        expense("OTHER", "PERSONAL", "20000.00", cash);
        // a loan: 6000 interest counts, a 500 interest payment reversed does not, a 10000 principal repayment is no
        // expense; a chit contribution is an investment, not an expense
        String loan = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":100000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}"""
                .formatted(data.lender().id(), today(), bank))).andExpect(status().isCreated())).get("id").asString();
        loanPayment(loan, "interest-payments", "6000.00", bank).andExpect(status().isCreated());
        String tx = body(loanPayment(loan, "interest-payments", "500.00", bank).andExpect(status().isCreated()))
                .get("payments").get(1).get("transactionId").asString();
        asOwner(postWithKey("/api/v1/loans/" + loan + "/payments/" + tx + "/reverse", "{\"reason\":\"twice\"}"))
                .andExpect(status().isOk());
        loanPayment(loan, "principal-repayments", "10000.00", bank).andExpect(status().isCreated());
        String chit = body(asOwner(postJson("/api/v1/chits", """
                {"chitName":"Chit A","totalAmount":100000.00,"monthlyAmount":5000.00,"durationMonths":20,
                 "startDate":"%s","priorContributionsCount":0,"priorContributionsAmount":0.00}""".formatted(today())))
                .andExpect(status().isCreated())).get("id").asString();
        asOwner(postWithKey("/api/v1/chits/" + chit + "/contributions", """
                {"amount":5000.00,"financialAccountId":"%s","paymentMethod":"CASH","entryDate":"%s"}"""
                .formatted(cash, today()))).andExpect(status().isCreated());

        // AC1: business, personal drawings and interest are three separate figures
        String period = "?from=" + today().withDayOfMonth(1) + "&to=" + today();
        asOwner(get("/api/v1/reports/expenses" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessTotal").value(5000.00))
                .andExpect(jsonPath("$.personalDrawingsTotal").value(20000.00))
                .andExpect(jsonPath("$.interestCost").value(6000.00))
                .andExpect(jsonPath("$.businessByCategory.length()").value(2))
                .andExpect(jsonPath("$.businessByCategory[?(@.category == 'ELECTRICITY')].total").value(3000.00))
                .andExpect(jsonPath("$.byMonth.length()").value(1))
                .andExpect(jsonPath("$.byMonth[0].month").value(java.time.YearMonth.from(today()).toString()))
                .andExpect(jsonPath("$.byMonth[0].businessTotal").value(5000.00))
                .andExpect(jsonPath("$.byMonth[0].personalTotal").value(20000.00));
        // a period before all of it is empty
        asOwner(get("/api/v1/reports/expenses?from=" + today().minusDays(60) + "&to=" + today().minusDays(40)))
                .andExpect(jsonPath("$.businessTotal").value(0.0)).andExpect(jsonPath("$.interestCost").value(0.0));

        // AC2: from after to is 400
        asOwner(get("/api/v1/reports/expenses?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions expense(String category, String kind, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/expenses", """
                {"category":"%s","expenseKind":"%s","amount":%s,"expenseDate":"%s","financialAccountId":"%s",
                 "paymentMethod":"CASH"}""".formatted(category, kind, amount, today(), account)))
                .andExpect(status().isCreated());
    }

    private ResultActions loanPayment(String loan, String kind, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/loans/" + loan + "/" + kind, """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"BANK_TRANSFER","paymentDate":"%s"}"""
                .formatted(amount, account, today())));
    }
}

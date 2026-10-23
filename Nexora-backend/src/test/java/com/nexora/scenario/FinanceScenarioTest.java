package com.nexora.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;

// The money side of a month end to end through the API (NFR-TEST-06): cash and bank at go-live, a transfer, business
// expenses and a personal drawing, a bank loan with a repayment and interest, a chit (running at go-live, one more
// contribution and an organiser payment), one mistaken expense reversed, and the cash position read back. Every
// figure is checked, and the balances must equal the ledger.
class FinanceScenarioTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-TEST-06 transfer expense loan chit reversal cash position")
    void transferExpenseLoanChitReversalCashPosition() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        String bank = createAccount("SBI Current", "BANK", "200000.00");

        // 1. 30000 bank -> cash (neither revenue nor expense): cash 80000, bank 170000
        asOwner(postWithKey("/api/v1/finance/transfers", """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":30000.00,"transferDate":"%s"}"""
                .formatted(bank, cash, today()))).andExpect(status().isCreated());

        // 2. expenses: transport 1500 cash, electricity 4200 by UPI, a personal drawing of 10000 cash, and a rent
        //    entry of 9000 recorded by mistake and reversed: cash 68500, bank 165800
        expense("TRANSPORT", "BUSINESS", "1500.00", cash, "CASH").andExpect(status().isCreated());
        expense("ELECTRICITY", "BUSINESS", "4200.00", bank, "UPI").andExpect(status().isCreated());
        expense("OTHER", "PERSONAL", "10000.00", cash, "CASH").andExpect(status().isCreated());
        String mistake = body(expense("RENT", "BUSINESS", "9000.00", cash, "CASH")).get("id").asString();
        asOwner(postWithKey("/api/v1/expenses/" + mistake + "/reverse", "{\"reason\":\"paid by the landlord's son\"}"))
                .andExpect(status().isOk());

        // 3. a bank loan of 300000 into the bank, 50000 principal repaid, 3000 interest: bank 412800, owed 250000
        String lender = data.lender().id().toString();
        String loan = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":300000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"BANK_TRANSFER"}""".formatted(lender, today(), bank)))
                .andExpect(status().isCreated())).get("id").asString();
        loanPayment(loan, "principal-repayments", "50000.00", bank).andExpect(status().isCreated());
        loanPayment(loan, "interest-payments", "3000.00", bank).andExpect(jsonPath("$.outstandingPrincipal")
                .value(250000.00)).andExpect(jsonPath("$.interestPaid").value(3000.00));

        // 4. a chit running at go-live (6 of 20 paid, 150000), one more contribution and an organiser payment in cash:
        //    cash 68500 - 25000 - 1000 = 42500
        String chit = body(asOwner(postJson("/api/v1/chits", """
                {"chitName":"Chit A","totalAmount":500000.00,"monthlyAmount":25000.00,"durationMonths":20,
                 "startDate":"%s","priorContributionsCount":6,"priorContributionsAmount":150000.00}"""
                .formatted(today().minusMonths(6)))).andExpect(status().isCreated())).get("id").asString();
        chitEntry(chit, "contributions", "25000.00", cash).andExpect(status().isCreated());
        chitEntry(chit, "organiser-payments", "1000.00", cash).andExpect(jsonPath("$.contributionsCount").value(7))
                .andExpect(jsonPath("$.totalContributed").value(175000.00))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(13))
                .andExpect(jsonPath("$.organiserPayments").value(1000.00));

        // 5. cash position: cash 42500, bank 412800, total 455300
        asOwner(get("/api/v1/finance/cash-position")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCash").value(42500.00))
                .andExpect(jsonPath("$.totalBank").value(412800.00))
                .andExpect(jsonPath("$.totalAvailable").value(455300.00));

        // 6. reports' raw material: business expenses 5700 (rent reversed, drawing apart), drawings 10000; loans and chits
        //    are not expenses
        asOwner(get("/api/v1/expenses/summary?from=" + today() + "&to=" + today()))
                .andExpect(jsonPath("$.businessTotal").value(5700.00))
                .andExpect(jsonPath("$.personalDrawingsTotal").value(10000.00));
        assertThat(jdbc.queryForObject("select count(*) from expenses", Integer.class)).isEqualTo(4);
        // every balance is the sum of its ledger rows, and every reversal mirrors its original
        assertThat(jdbc.queryForObject("""
                select count(*) from financial_transactions r join financial_transactions o
                  on o.id = r.reverses_transaction_id
                where r.amount <> o.amount or r.direction = o.direction or r.financial_account_id <> o.financial_account_id
                """, Integer.class)).isZero();
        asOwner(get("/api/v1/finance/accounts/" + cash + "/transactions?size=100"))
                .andExpect(jsonPath("$.content[-1:].runningBalance").value(42500.0));
    }

    private ResultActions expense(String category, String kind, String amount, String account, String method)
            throws Exception {
        return asOwner(postWithKey("/api/v1/expenses", """
                {"category":"%s","expenseKind":"%s","amount":%s,"expenseDate":"%s","financialAccountId":"%s",
                 "paymentMethod":"%s"}""".formatted(category, kind, amount, today(), account, method)));
    }

    private ResultActions loanPayment(String loan, String kind, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/loans/" + loan + "/" + kind, """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"BANK_TRANSFER","paymentDate":"%s"}"""
                .formatted(amount, account, today())));
    }

    private ResultActions chitEntry(String chit, String kind, String amount, String account) throws Exception {
        return asOwner(postWithKey("/api/v1/chits/" + chit + "/" + kind, """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"CASH","entryDate":"%s"}"""
                .formatted(amount, account, today())));
    }
}

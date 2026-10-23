package com.nexora.loans.api;

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

class LoanApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-LOAN-01 create records loan received in transaction")
    void create_recordsLoanReceivedInTransaction() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        UUID lender = data.lender().id();

        // AC1: 500000 into the bank as LOAN_RECEIVED IN; outstanding 500000; a liability, not revenue
        String id = body(create(lender, "500000.00", bank, "BANK_TRANSFER").andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/loans/")))
                .andExpect(jsonPath("$.loanNumber").value("LON-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.lender.id").value(lender.toString()))
                .andExpect(jsonPath("$.outstandingPrincipal").value(500000.00))
                .andExpect(jsonPath("$.repaidBeforeGoLive").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.existingAtGoLive").value(false))
                .andExpect(jsonPath("$.receivedTransactionId").isNotEmpty())
                .andExpect(jsonPath("$.payments.length()").value(0))).get("id").asString();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(500000.00));
        assertThat(jdbc.queryForObject("select transaction_type || ' ' || direction from financial_transactions"
                + " where loan_id = ?::uuid", String.class, id)).isEqualTo("LOAN_RECEIVED IN");
        // 400 principal 0 and due before start; 422 method not fitting the account; 404 unknown account
        create(lender, "0.00", bank, "BANK_TRANSFER").andExpect(status().isBadRequest());
        asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":1.00,"startDate":"%s","dueDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"UPI"}""".formatted(lender, today(), today().minusDays(1),
                bank))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dueDate"));
        create(lender, "1.00", bank, "CASH").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        create(lender, "1.00", UUID.randomUUID().toString(), "CASH").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from loans", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-LOAN-01 create stores interest notes")
    void create_storesInterestNotes() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");

        asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"PRIVATE","principalAmount":200000.00,"startDate":"%s",
                 "dueDate":"%s","interestRatePercent":18.000,"interestNotes":"1.5%% a month, paid by the 5th",
                 "financialAccountId":"%s","paymentMethod":"UPI"}""".formatted(data.lender().id(), today(),
                today().plusYears(1), bank))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.interestNotes").value("1.5% a month, paid by the 5th"))
                .andExpect(jsonPath("$.interestRatePercent").value(18.000))
                .andExpect(jsonPath("$.loanType").value("PRIVATE"))
                .andExpect(jsonPath("$.dueDate").value(today().plusYears(1).toString()));
    }

    @Test
    @DisplayName("FR-LOAN-01 FR-PARTY-16 create for non lender returns422 invalid party role")
    void create_forNonLender_returns422InvalidPartyRole() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");

        create(data.supplier().id(), "1000.00", bank, "UPI").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        asOwner(postJson("/api/v1/loans/existing", existing(data.customer().id(), "1000.00", "500.00")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PARTY_ROLE"));
        create(UUID.randomUUID(), "1000.00", bank, "UPI").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from loans", Integer.class)).isZero();
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("FR-LOAN-02 register existing stores repaid before go live without transaction")
    void registerExisting_storesRepaidBeforeGoLiveWithoutTransaction() throws Exception {
        // AC1: 500000 borrowed, 320000 still owed at go-live -> 180000 repaid before, no ledger row at all
        String id = body(asOwner(postJson("/api/v1/loans/existing", existing(data.lender().id(), "500000.00",
                "320000.00"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.repaidBeforeGoLive").value(180000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(320000.00))
                .andExpect(jsonPath("$.existingAtGoLive").value(true))
                .andExpect(jsonPath("$.receivedTransactionId").value(Matchers.nullValue()))).get("id").asString();
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions", Integer.class)).isZero();
        // nothing outstanding at go-live is allowed too, and is still an existing loan
        asOwner(postJson("/api/v1/loans/existing", existing(data.lender().id(), "1000.00", "1000.00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.existingAtGoLive").value(true))
                .andExpect(jsonPath("$.repaidBeforeGoLive").value(0));
        asOwner(get("/api/v1/loans/" + id)).andExpect(jsonPath("$.loanNumber").value(Matchers.startsWith("LON-")));
    }

    @Test
    @DisplayName("FR-LOAN-02 register existing outstanding above principal returns400")
    void registerExisting_outstandingAbovePrincipal_returns400() throws Exception {
        asOwner(postJson("/api/v1/loans/existing", existing(data.lender().id(), "500000.00", "500000.01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("outstandingPrincipal"));
        asOwner(postJson("/api/v1/loans/existing", existing(data.lender().id(), "500000.00", "-1.00")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from loans", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-LOAN-03 principal repayment above outstanding is rejected")
    void principalRepayment_aboveOutstanding_isRejected() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "1000000.00");
        String id = existingLoan("500000.00", "100000.00");

        // AC2: 100000 owed -> 100000.01 refused, nothing paid
        payment(id, "principal-repayments", "100000.01", bank, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALLOCATION_EXCEEDED"));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(1000000.00));
        // AC1: a partial repayment, then the rest
        payment(id, "principal-repayments", "60000.00", bank, null).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/loans/" + id))
                .andExpect(jsonPath("$.principalRepaid").value(60000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(40000.00));
        payment(id, "principal-repayments", "40000.00", bank, null)
                .andExpect(jsonPath("$.outstandingPrincipal").value(0));
        // more than the account holds -> 409 INSUFFICIENT_FUNDS
        String cash = createAccount("Cash", "CASH", "10.00");
        String other = existingLoan("1000.00", "1000.00");
        asOwner(postWithKey("/api/v1/loans/" + other + "/principal-repayments", """
                {"amount":500.00,"financialAccountId":"%s","paymentMethod":"CASH","paymentDate":"%s"}"""
                .formatted(cash, today()))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("FR-LOAN-04 FR-LOAN-08 interest payment is expense not principal")
    void interestPayment_isExpenseNotPrincipal() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        String id = body(create(data.lender().id(), "500000.00", bank, "BANK_TRANSFER")).get("id").asString();

        // AC1: 6000 interest -> principal unchanged, interestPaid 6000, LOAN_INTEREST_PAYMENT OUT with the period
        payment(id, "interest-payments", "6000.00", bank, "Oct 2026").andExpect(status().isCreated())
                .andExpect(jsonPath("$.interestPaid").value(6000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(500000.00))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].transactionType").value("LOAN_INTEREST_PAYMENT"))
                .andExpect(jsonPath("$.payments[0].periodNote").value("Oct 2026"))
                .andExpect(jsonPath("$.payments[0].account.id").value(bank));
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(494000.00));
        assertThat(jdbc.queryForList("select transaction_type || ' ' || direction from financial_transactions"
                + " where loan_id = ?::uuid order by created_at", String.class, id))
                .containsExactly("LOAN_RECEIVED IN", "LOAN_INTEREST_PAYMENT OUT");
        // interest may still be paid after the principal reached 0, and is never capped by it
        payment(id, "principal-repayments", "494000.00", bank, null).andExpect(status().isCreated());
        payment(id, "interest-payments", "0.00", bank, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-LOAN-05 outstanding subtracts repaid before go live and repayments")
    void outstanding_subtractsRepaidBeforeGoLiveAndRepayments() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "500000.00");
        String id = existingLoan("500000.00", "350000.00");            // 150000 repaid before go-live

        payment(id, "principal-repayments", "100000.00", bank, null).andExpect(status().isCreated());
        payment(id, "interest-payments", "5000.00", bank, "Nov 2026").andExpect(status().isCreated());

        // 500000 - 150000 - 100000 = 250000; interest does not count
        asOwner(get("/api/v1/loans/" + id)).andExpect(jsonPath("$.principalAmount").value(500000.00))
                .andExpect(jsonPath("$.repaidBeforeGoLive").value(150000.00))
                .andExpect(jsonPath("$.principalRepaid").value(100000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(250000.00))
                .andExpect(jsonPath("$.interestPaid").value(5000.00))
                .andExpect(jsonPath("$.payments.length()").value(2));
        // list: filters, sort by outstanding, no payments in rows
        existingLoan("1000.00", "1000.00");
        asOwner(get("/api/v1/loans?sort=outstandingPrincipal,desc")).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].payments").value(Matchers.nullValue()));
        asOwner(get("/api/v1/loans?status=CLOSED")).andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/loans?loanType=BANK&size=1")).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        asOwner(get("/api/v1/loans?sort=notes")).andExpect(status().isBadRequest());
        asOwner(get("/api/v1/loans/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-LOAN-06 close with outstanding is rejected")
    void close_withOutstanding_isRejected() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String id = existingLoan("100000.00", "20000.00");

        // AC2: 20000 owed -> 409, still ACTIVE
        asOwner(postJson("/api/v1/loans/" + id + "/close", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // AC1: at 0 -> CLOSED; then no more payments; closing twice 409
        payment(id, "principal-repayments", "20000.00", bank, null).andExpect(status().isCreated());
        payment(id, "interest-payments", "300.00", bank, "final").andExpect(status().isCreated());
        asOwner(postJson("/api/v1/loans/" + id + "/close", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.outstandingPrincipal").value(0));
        payment(id, "interest-payments", "100.00", bank, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postJson("/api/v1/loans/" + id + "/close", "")).andExpect(status().isConflict());
        asOwner(get("/api/v1/loans?status=CLOSED")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("FR-LOAN-07 NFR-OBS-06 reverse payment adds compensating transaction and audit entry")
    void reversePayment_addsCompensatingTransactionAndAuditEntry() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "200000.00");
        String id = existingLoan("500000.00", "500000.00");
        String tx = body(payment(id, "principal-repayments", "100000.00", bank, null)).get("payments").get(0)
                .get("transactionId").asString();

        // AC: REVERSAL IN for the repayment, outstanding back to 500000, audited
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"Paid from wrong account\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPrincipal").value(500000.00))
                .andExpect(jsonPath("$.principalRepaid").value(0))
                .andExpect(jsonPath("$.payments[0].reversed").value(true))
                .andExpect(jsonPath("$.payments[0].reversalTransactionId").isNotEmpty());
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(200000.00));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'REVERSAL' and direction = 'IN' and reverses_transaction_id = ?::uuid", Integer.class, tx))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOAN_PAYMENT_REVERSED'"
                + " and entity_id = ?::uuid and reason = 'Paid from wrong account'", Integer.class, id)).isEqualTo(1);
        // twice 409; the LOAN_RECEIVED row or another loan's row is not a payment of this loan (404); blank reason 400
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"again\"}")).andExpect(status().isConflict());
        String other = body(create(data.lender().id(), "1000.00", bank, "BANK_TRANSFER")).get("id").asString();
        String received = body(asOwner(get("/api/v1/loans/" + other))).get("receivedTransactionId").asString();
        asOwner(postWithKey(reverse(other, received), "{\"reason\":\"x\"}")).andExpect(status().isNotFound());
        asOwner(postWithKey(reverse(other, tx), "{\"reason\":\"x\"}")).andExpect(status().isNotFound());
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-FIN-18 FR-PAY-07 money writes replay with same key return stored response")
    void moneyWrites_replayWithSameKey_returnStoredResponse() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        UUID lender = data.lender().id();

        String created = replayTwice("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":50000.00,"startDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"UPI"}""".formatted(lender, today(), bank), 201);
        String id = jsonMapper.readTree(created).get("id").asString();
        replayTwice("/api/v1/loans/" + id + "/principal-repayments", paymentBody("1000.00", bank, null), 201);
        replayTwice("/api/v1/loans/" + id + "/interest-payments", paymentBody("500.00", bank, "Oct"), 201);
        String tx = body(asOwner(get("/api/v1/loans/" + id))).get("payments").get(0).get("transactionId").asString();
        replayTwice(reverse(id, tx), "{\"reason\":\"typo\"}", 200);

        // one loan, one repayment, one interest payment, one reversal: 50000 - 1000 - 500 + 1000
        assertThat(jdbc.queryForObject("select count(*) from loans", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where loan_id = ?::uuid",
                Integer.class, id)).isEqualTo(4);
        asOwner(get("/api/v1/finance/accounts/" + bank)).andExpect(jsonPath("$.balance").value(49500.00));
        // the four money routes need a key
        asOwner(postJson("/api/v1/loans/" + id + "/interest-payments", paymentBody("1.00", bank, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-LOAN-09 put or delete is not supported")
    void putOrDelete_isNotSupported() throws Exception {
        String id = existingLoan("1000.00", "1000.00");

        asOwner(put("/api/v1/loans/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        asOwner(patch("/api/v1/loans/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalAmount\":1.00}")).andExpect(status().isMethodNotAllowed());
        asOwner(delete("/api/v1/loans/" + id)).andExpect(status().isMethodNotAllowed());
        asOwner(get("/api/v1/loans/" + id)).andExpect(jsonPath("$.principalAmount").value(1000.00));
    }

    @Test
    @DisplayName("FR-LOAN-10 interest is entered manually")
    void interestIsEnteredManually() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String id = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":100000.00,"startDate":"%s",
                 "interestRatePercent":12.000,"financialAccountId":"%s","paymentMethod":"UPI"}"""
                .formatted(data.lender().id(), today().minusMonths(3), bank)))).get("id").asString();

        // months pass, yet nothing is calculated or charged: interest is only what the owner enters
        asOwner(get("/api/v1/loans/" + id)).andExpect(jsonPath("$.interestPaid").value(0))
                .andExpect(jsonPath("$.payments.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'LOAN_INTEREST_PAYMENT'", Integer.class)).isZero();
        // the advice endpoint suggests monthly interest (12% per year on 100k = 1000.00) but creates NO payment
        asOwner(get("/api/v1/loans/" + id + "/advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestDueSuggestion").value(1000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(100000.00))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.options[0].action").value("PAY_INTEREST"))
                .andExpect(jsonPath("$.options[0].amount").value(1000.00));
        // Still zero payments created by advice call (read-only)
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'LOAN_INTEREST_PAYMENT'", Integer.class)).isZero();

        // an odd amount the owner agreed with the lender is taken as entered
        payment(id, "interest-payments", "1003.37", bank, "Agreed with the bank").andExpect(status().isCreated())
                .andExpect(jsonPath("$.interestPaid").value(1003.37));
    }

    @Test
    @DisplayName("FR-LOAN-10 AC1 AC2 getAdvice suggests interest and closure options without creating payments")
    void getAdvice_suggestsInterestAndClosureOptions() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        // Private loan at 1.5% per month on 200,000
        String id = body(asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"PRIVATE","principalAmount":200000.00,"startDate":"%s",
                 "interestRatePercent":1.500,"financialAccountId":"%s","paymentMethod":"CASH"}"""
                .formatted(data.lender().id(), today().minusMonths(2), cash)))).get("id").asString();

        // Total cash is 50000 + 200000 = 250000.00
        asOwner(get("/api/v1/loans/" + id + "/advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPrincipal").value(200000.00))
                .andExpect(jsonPath("$.interestDueSuggestion").value(3000.00)) // 200,000 * 1.5% = 3000.00
                .andExpect(jsonPath("$.totalAvailableFunds").value(250000.00))
                .andExpect(jsonPath("$.options[0].action").value("PAY_INTEREST"))
                .andExpect(jsonPath("$.options[0].amount").value(3000.00))
                .andExpect(jsonPath("$.options[0].availableAfter").value(247000.00))
                .andExpect(jsonPath("$.options[1].action").value("PART_PRINCIPAL"))
                .andExpect(jsonPath("$.options[1].amount").value(40000.00)) // 20% of 200k = 40k
                .andExpect(jsonPath("$.options[1].availableAfter").value(210000.00))
                .andExpect(jsonPath("$.options[2].action").value("FULL_CLOSURE"))
                .andExpect(jsonPath("$.options[2].amount").value(203000.00)) // 200k + 3k
                .andExpect(jsonPath("$.options[2].availableAfter").value(47000.00));
    }

    @Test
    @DisplayName("FR-LOAN-10 getAdvice on closed loan returns zero interest")
    void getAdvice_closedLoan_returnsZeroInterest() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String id = existingLoan("10000.00", "10000.00");
        payment(id, "principal-repayments", "10000.00", bank, null).andExpect(status().isCreated());
        asOwner(postJson("/api/v1/loans/" + id + "/close", "{}")).andExpect(status().isOk());

        asOwner(get("/api/v1/loans/" + id + "/advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPrincipal").value(0.00))
                .andExpect(jsonPath("$.interestDueSuggestion").value(0.00))
                .andExpect(jsonPath("$.options[0].amount").value(0.00))
                .andExpect(jsonPath("$.options[2].amount").value(0.00));
    }

    private ResultActions create(UUID lender, String principal, String account, String method) throws Exception {
        return asOwner(postWithKey("/api/v1/loans", """
                {"lenderId":"%s","loanType":"BANK","principalAmount":%s,"startDate":"%s","financialAccountId":"%s",
                 "paymentMethod":"%s"}""".formatted(lender, principal, today(), account, method)));
    }

    private String existing(UUID lender, String principal, String outstanding) {
        return """
                {"lenderId":"%s","loanType":"BANK","principalAmount":%s,"startDate":"%s","outstandingPrincipal":%s}"""
                .formatted(lender, principal, today().minusYears(1), outstanding);
    }

    private String existingLoan(String principal, String outstanding) throws Exception {
        return body(asOwner(postJson("/api/v1/loans/existing", existing(data.lender().id(), principal, outstanding)))
                .andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions payment(String loan, String kind, String amount, String account, String period)
            throws Exception {
        return asOwner(postWithKey("/api/v1/loans/" + loan + "/" + kind, paymentBody(amount, account, period)));
    }

    private String paymentBody(String amount, String account, String period) {
        return """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"BANK_TRANSFER","paymentDate":"%s"%s}"""
                .formatted(amount, account, today(), period == null ? "" : ",\"periodNote\":\"" + period + "\"");
    }

    private static String reverse(String loan, String transaction) {
        return "/api/v1/loans/" + loan + "/payments/" + transaction + "/reverse";
    }
}

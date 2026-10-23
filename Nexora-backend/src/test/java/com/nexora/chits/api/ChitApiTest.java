package com.nexora.chits.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class ChitApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-CHIT-01 create returns active chit")
    void create_returnsActiveChit() throws Exception {
        // AC1
        create("Chit A", 20, null, null).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/chits/")))
                .andExpect(jsonPath("$.chitNumber").value("CHT-" + Year.now(clock) + "-0001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contributionsCount").value(0))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(20))
                .andExpect(jsonPath("$.payoutReceived").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.entries.length()").value(0));
        // AC2: duration 0 -> 400; blank name 400; AC3: Staff 403
        create("Chit B", 0, null, null).andExpect(status().isBadRequest());
        create(" ", 10, null, null).andExpect(status().isBadRequest());
        mockMvc.perform(postJson("/api/v1/chits", chitBody("Chit C", 10, null, null))
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from chits", Integer.class)).isEqualTo(1);
        asOwner(get("/api/v1/chits")).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entries").value(Matchers.nullValue()));
        asOwner(get("/api/v1/chits?status=CLOSED")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("FR-CHIT-02 contribution non cash returns422 invalid payment method")
    void contribution_nonCash_returns422InvalidPaymentMethod() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String bank = createAccount("SBI Current", "BANK", "100000.00");
        String id = chit();

        // AC1: 25000 from cash -> cash 75000, one contribution
        entry(id, "contributions", "25000.00", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.contributionsCount").value(1))
                .andExpect(jsonPath("$.totalContributed").value(25000.00));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(75000.00));
        // AC2: a bank account, or UPI on the cash account -> 422, nothing stored
        entry(id, "contributions", "25000.00", bank, "CASH").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        entry(id, "contributions", "25000.00", bank, "UPI").andExpect(status().isUnprocessableContent());
        entry(id, "contributions", "25000.00", cash, "UPI").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        // AC3: more than the cash -> 409
        entry(id, "contributions", "80000.00", cash, "CASH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where chit_id is not null",
                Integer.class)).isEqualTo(1);
        // AC4: a CLOSED chit takes no contribution; an amount other than the monthly one is fine
        entry(id, "contributions", "23500.00", cash, "CASH").andExpect(status().isCreated());
        asOwner(postJson("/api/v1/chits/" + id + "/close", "")).andExpect(status().isOk());
        entry(id, "contributions", "1.00", cash, "CASH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-CHIT-03 payout cash only records in transaction")
    void payout_cashOnly_recordsInTransaction() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String bank = createAccount("SBI Current", "BANK", "0.00");
        String id = chit();

        // AC3: a bank account -> 422
        entry(id, "payout", "460000.00", bank, "BANK_TRANSFER").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
        // AC1: cash +460000, MATURED, a CHIT_PAYOUT IN row; contributions still allowed afterwards
        entry(id, "payout", "460000.00", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATURED"))
                .andExpect(jsonPath("$.payoutReceived").value(460000.00));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(560000.00));
        assertThat(jdbc.queryForObject("select transaction_type || ' ' || direction from financial_transactions"
                + " where chit_id = ?::uuid", String.class, id)).isEqualTo("CHIT_PAYOUT IN");
        entry(id, "contributions", "25000.00", cash, "CASH").andExpect(status().isCreated());
        // AC2: a second payout -> 409
        entry(id, "payout", "1000.00", cash, "CASH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-CHIT-04 organiser payment recorded separately")
    void organiserPayment_recordedSeparately() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        String bank = createAccount("SBI Current", "BANK", "50000.00");
        String id = chit();
        entry(id, "contributions", "25000.00", cash, "CASH").andExpect(status().isCreated());

        // AC1: 2000 under organiser payments, not contributions
        entry(id, "organiser-payments", "2000.00", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.organiserPayments").value(2000.00))
                .andExpect(jsonPath("$.contributionsCount").value(1))
                .andExpect(jsonPath("$.totalContributed").value(25000.00));
        // AC2: a bank account -> 422
        entry(id, "organiser-payments", "2000.00", bank, "CASH").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
    }

    @Test
    @DisplayName("FR-CHIT-04 organiser payment creates chit organiser payment out transaction")
    void organiserPayment_createsChitOrganiserPaymentOutTransaction() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        String id = chit();

        entry(id, "organiser-payments", "2000.00", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries[0].transactionType").value("CHIT_ORGANISER_PAYMENT"));
        assertThat(jdbc.queryForObject("select transaction_type || ' ' || direction || ' ' || amount from"
                + " financial_transactions where chit_id = ?::uuid", String.class, id))
                .isEqualTo("CHIT_ORGANISER_PAYMENT OUT 2000.00");
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(48000.00));
        // FR-CHIT-07: not an expense
        assertThat(jdbc.queryForObject("select count(*) from expenses", Integer.class)).isZero();
    }

    @Test
    @DisplayName("FR-CHIT-05 summary derived from entries")
    void summary_derivedFromEntries() throws Exception {
        String cash = createAccount("Cash", "CASH", "500000.00");
        String id = chit();
        for (int i = 0; i < 8; i++) {
            entry(id, "contributions", "25000.00", cash, "CASH").andExpect(status().isCreated());
        }

        // AC1: 8 x 25000 = 200000, 12 remaining of 20
        asOwner(get("/api/v1/chits/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contributionsCount").value(8))
                .andExpect(jsonPath("$.totalContributed").value(200000.00))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(12))
                .andExpect(jsonPath("$.entries.length()").value(8));
        asOwner(get("/api/v1/chits")).andExpect(jsonPath("$.content[0].totalContributed").value(200000.00));
        // AC2: unknown 404
        asOwner(get("/api/v1/chits/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-CHIT-06 close from active or matured")
    void close_fromActiveOrMatured() throws Exception {
        String cash = createAccount("Cash", "CASH", "10000.00");
        String active = chit();
        String matured = chit();
        entry(matured, "payout", "5000.00", cash, "CASH").andExpect(status().isCreated());

        // AC1: MATURED -> CLOSED, ACTIVE -> CLOSED; history stays
        asOwner(postJson("/api/v1/chits/" + matured + "/close", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.entries.length()").value(1));
        asOwner(postJson("/api/v1/chits/" + active + "/close", "")).andExpect(status().isOk());
        // AC2: twice -> 409; unknown 404
        asOwner(postJson("/api/v1/chits/" + active + "/close", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postJson("/api/v1/chits/" + UUID.randomUUID() + "/close", "")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-CHIT-08 create with prior contributions counts them without cash transaction")
    void create_withPriorContributions_countsThemWithoutCashTransaction() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");

        // AC1: 6 paid before (150000) -> 14 remaining, no ledger row
        String id = body(create("Chit A", 20, 6, "150000.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.priorContributionsCount").value(6))
                .andExpect(jsonPath("$.contributionsCount").value(6))
                .andExpect(jsonPath("$.totalContributed").value(150000.00))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(14))).get("id").asString();
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where chit_id is not null",
                Integer.class)).isZero();
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(100000.00));
        // one more in the system counts on top
        entry(id, "contributions", "25000.00", cash, "CASH").andExpect(jsonPath("$.contributionsCount").value(7))
                .andExpect(jsonPath("$.totalContributed").value(175000.00))
                .andExpect(jsonPath("$.remainingExpectedContributions").value(13));
        // a count needs its amount, and an amount its count
        create("Chit B", 20, 2, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("priorContributionsAmount"));
        create("Chit C", 20, 0, "100.00").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-CHIT-08 prior contributions count above duration returns400")
    void priorContributionsCountAboveDuration_returns400() throws Exception {
        // AC2
        create("Chit A", 20, 21, "525000.00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("priorContributionsCount"));
        create("Chit A", 20, -1, "0.00").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from chits", Integer.class)).isZero();
        // all 20 paid before is allowed: nothing remains
        create("Chit A", 20, 20, "500000.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingExpectedContributions").value(0));
    }

    @Test
    @DisplayName("FR-CHIT-09 NFR-OBS-06 reverse entry adds compensating transaction and audit entry")
    void reverseEntry_addsCompensatingTransactionAndAuditEntry() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String id = chit();
        String tx = body(entry(id, "contributions", "25000.00", cash, "CASH")).get("entries").get(0)
                .get("transactionId").asString();

        // AC1: cash back by 25000, no longer counted; REVERSAL IN; audited
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"Entered twice\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contributionsCount").value(0))
                .andExpect(jsonPath("$.totalContributed").value(0))
                .andExpect(jsonPath("$.entries[0].reversed").value(true));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(100000.00));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where transaction_type ="
                + " 'REVERSAL' and direction = 'IN' and reverses_transaction_id = ?::uuid", Integer.class, tx))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CHIT_ENTRY_REVERSED'"
                + " and entity_id = ?::uuid and reason = 'Entered twice'", Integer.class, id)).isEqualTo(1);
        // AC2: twice 409; another chit's entry 404; blank reason 400
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"again\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        asOwner(postWithKey(reverse(chit(), tx), "{\"reason\":\"x\"}")).andExpect(status().isNotFound());
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-CHIT-09 reverse payout returns chit to active")
    void reversePayout_returnsChitToActive() throws Exception {
        String cash = createAccount("Cash", "CASH", "0.00");
        String id = chit();
        String tx = body(entry(id, "payout", "460000.00", cash, "CASH")).get("entries").get(0).get("transactionId")
                .asString();

        // the cash went out meanwhile: reversing the payout needs it back -> 409 INSUFFICIENT_FUNDS, still MATURED
        asOwner(postWithKey("/api/v1/finance/accounts/" + cash + "/adjustments", """
                {"direction":"OUT","amount":10000.00,"transactionDate":"%s","reason":"spent"}""".formatted(today())))
                .andExpect(status().isCreated());
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"wrong chit\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        asOwner(get("/api/v1/chits/" + id)).andExpect(jsonPath("$.status").value("MATURED"));
        asOwner(postWithKey("/api/v1/finance/accounts/" + cash + "/adjustments", """
                {"direction":"IN","amount":10000.00,"transactionDate":"%s","reason":"found"}""".formatted(today())))
                .andExpect(status().isCreated());
        // MATURED -> ACTIVE, the payout no longer counts, and it can be recorded again
        asOwner(postWithKey(reverse(id, tx), "{\"reason\":\"wrong chit\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payoutReceived").value(Matchers.nullValue()));
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(0));
        entry(id, "payout", "450000.00", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATURED"));
    }

    @Test
    @DisplayName("FR-PAY-07 money writes replay with same key return stored response")
    void moneyWrites_replayWithSameKey_returnStoredResponse() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String id = chit();

        replayTwice("/api/v1/chits/" + id + "/contributions", entryBody("25000.00", cash, "CASH"), 201);
        replayTwice("/api/v1/chits/" + id + "/organiser-payments", entryBody("2000.00", cash, "CASH"), 201);
        replayTwice("/api/v1/chits/" + id + "/payout", entryBody("40000.00", cash, "CASH"), 201);
        String tx = body(asOwner(get("/api/v1/chits/" + id))).get("entries").get(0).get("transactionId").asString();
        replayTwice(reverse(id, tx), "{\"reason\":\"typo\"}", 200);

        // 100000 - 25000 - 2000 + 40000 + 25000 (reversed contribution) = 138000; one row per write
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(138000.00));
        assertThat(jdbc.queryForObject("select count(*) from financial_transactions where chit_id = ?::uuid",
                Integer.class, id)).isEqualTo(4);
        asOwner(postJson("/api/v1/chits/" + id + "/contributions", entryBody("1.00", cash, "CASH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-CHIT-10 amounts are entered without bidding calculation")
    void amountsAreEnteredWithoutBiddingCalculation() throws Exception {
        String cash = createAccount("Cash", "CASH", "100000.00");
        String id = chit();                                            // 25000 a month standard

        // whatever the month's bid made it, the owner enters what was paid: 21750.50 is taken as entered
        entry(id, "contributions", "21750.50", cash, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalContributed").value(21750.50));

        // E-CHIT-09: monthly bid of 40,000 on 20 months gives 2,000 dividend and 23,000 contribution due
        asOwner(postJson("/api/v1/chits/" + id + "/bids", "{\"month\":\"2026-10\",\"bidAmount\":40000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chitId").value(id))
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.bidAmount").value(40000.00))
                .andExpect(jsonPath("$.dividendPerMember").value(2000.00))
                .andExpect(jsonPath("$.standardMonthlyAmount").value(25000.00))
                .andExpect(jsonPath("$.contributionDue").value(23000.00));
    }

    @Test
    @DisplayName("FR-CHIT-10 AC2 bid amount exceeding total returns 400 validation failed")
    void calculateBid_bidExceedingTotal_returns400() throws Exception {
        String id = chit();
        asOwner(postJson("/api/v1/chits/" + id + "/bids", "{\"month\":\"2026-10\",\"bidAmount\":600000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-CHIT-10 calculateBid on closed chit returns 409 invalid state transition")
    void calculateBid_closedChit_returns409() throws Exception {
        String id = chit();
        asOwner(postJson("/api/v1/chits/" + id + "/close", "{}")).andExpect(status().isOk());

        asOwner(postJson("/api/v1/chits/" + id + "/bids", "{\"month\":\"2026-10\",\"bidAmount\":20000.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    private ResultActions create(String name, int months, Integer priorCount, String priorAmount) throws Exception {
        return asOwner(postJson("/api/v1/chits", chitBody(name, months, priorCount, priorAmount)));
    }

    private String chitBody(String name, int months, Integer priorCount, String priorAmount) {
        return """
                {"chitName":"%s","totalAmount":500000.00,"monthlyAmount":25000.00,"durationMonths":%d,"startDate":"%s"%s%s}"""
                .formatted(name, months, today(),
                        priorCount == null ? "" : ",\"priorContributionsCount\":" + priorCount,
                        priorAmount == null ? "" : ",\"priorContributionsAmount\":" + priorAmount);
    }

    private String chit() throws Exception {
        return body(create("Chit A", 20, null, null).andExpect(status().isCreated())).get("id").asString();
    }

    private ResultActions entry(String chit, String kind, String amount, String account, String method)
            throws Exception {
        return asOwner(postWithKey("/api/v1/chits/" + chit + "/" + kind, entryBody(amount, account, method)));
    }

    private String entryBody(String amount, String account, String method) {
        return """
                {"amount":%s,"financialAccountId":"%s","paymentMethod":"%s","entryDate":"%s"}"""
                .formatted(amount, account, method, today());
    }

    private static String reverse(String chit, String transaction) {
        return "/api/v1/chits/" + chit + "/entries/" + transaction + "/reverse";
    }
}

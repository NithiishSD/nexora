package com.nexora.reporting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

class CashBankReportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-12 report opening in out closing per account")
    void report_openingInOutClosingPerAccount() throws Exception {
        // both accounts opened 10 days ago: cash 10000, bank 50000
        String cash = account("Cash", "CASH", "10000.00", today().minusDays(10));
        String bank = account("SBI Current", "BANK", "50000.00", today().minusDays(10));
        // in the period: a customer pays 5000 cash, a 3000 transport expense is paid in cash
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":5000.00,"paymentMethod":"CASH","financialAccountId":"%s"}"""
                .formatted(data.customer().id(), cash))).andExpect(status().isCreated());
        asOwner(postWithKey("/api/v1/expenses", """
                {"category":"TRANSPORT","expenseKind":"BUSINESS","amount":3000.00,"expenseDate":"%s",
                 "financialAccountId":"%s","paymentMethod":"CASH"}""".formatted(today(), cash)))
                .andExpect(status().isCreated());

        // AC1: opening 10000 + 5000 in - 3000 out = closing 12000; the untouched bank keeps its opening
        String period = "?from=" + today().minusDays(2) + "&to=" + today();
        asOwner(get("/api/v1/reports/cash-bank" + period)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].account.id").value(cash))
                .andExpect(jsonPath("$[0].openingBalance").value(10000.00))
                .andExpect(jsonPath("$[0].inByType[0].transactionType").value("CUSTOMER_PAYMENT"))
                .andExpect(jsonPath("$[0].inByType[0].amount").value(5000.00))
                .andExpect(jsonPath("$[0].outByType[0].transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$[0].totalIn").value(5000.00))
                .andExpect(jsonPath("$[0].totalOut").value(3000.00))
                .andExpect(jsonPath("$[0].closingBalance").value(12000.00))
                .andExpect(jsonPath("$[1].account.id").value(bank))
                .andExpect(jsonPath("$[1].openingBalance").value(50000.00))
                .andExpect(jsonPath("$[1].inByType.length()").value(0))
                .andExpect(jsonPath("$[1].closingBalance").value(50000.00));
        // the closing balance up to today equals the account balance
        asOwner(get("/api/v1/finance/accounts/" + cash)).andExpect(jsonPath("$.balance").value(12000.00));
        // a period covering the opening shows it as an inflow
        asOwner(get("/api/v1/reports/cash-bank?from=" + today().minusDays(10) + "&to=" + today() + "&accountId=" + bank))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].openingBalance").value(0.0))
                .andExpect(jsonPath("$[0].inByType[0].transactionType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$[0].closingBalance").value(50000.00));

        // AC2: an unknown account is 404; from after to is 400
        asOwner(get("/api/v1/reports/cash-bank" + period + "&accountId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        asOwner(get("/api/v1/reports/cash-bank?from=" + today() + "&to=" + today().minusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String account(String name, String type, String opening, LocalDate date) throws Exception {
        String bankName = type.equals("BANK") ? ",\"bankName\":\"State Bank\"" : "";
        return body(asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"%s","accountType":"%s"%s,"openingBalance":%s,"openingDate":"%s"}"""
                .formatted(name, type, bankName, opening, date))).andExpect(status().isCreated())).get("id").asString();
    }
}

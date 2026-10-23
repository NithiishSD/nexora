package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.finance.application.FinanceService;
import com.nexora.finance.application.TransactionCommand;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.support.AbstractApiTest;

class FinancialTransactionApiTest extends AbstractApiTest {

    @Autowired
    FinanceService finance;

    @Test
    @DisplayName("FR-FIN-07 list filtered by account type and date")
    void list_filteredByAccountTypeAndDate() throws Exception {
        LocalDate september = today().withDayOfMonth(1).minusMonths(1);
        LocalDate thisMonth = today().withDayOfMonth(1);
        String cash = account("Cash", "CASH", "10000.00", september);
        String bank = account("SBI Current", "BANK", "50000.00", thisMonth);
        adjust(cash, Direction.OUT, "500.00", thisMonth);
        adjust(bank, Direction.IN, "100.00", thisMonth);

        // AC1: one account, one month -> only the cash adjustment of this month
        asOwner(get("/api/v1/finance/transactions?accountId=%s&from=%s&to=%s".formatted(cash, thisMonth,
                thisMonth.plusMonths(1).minusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(500.00))
                .andExpect(jsonPath("$.content[0].description").value("recount"))
                .andExpect(jsonPath("$.content[0].account.accountType").value("CASH"));
        // type, direction and reference filters; default sort newest date first
        asOwner(get("/api/v1/finance/transactions?type=OPENING_BALANCE"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].account.accountName").value("SBI Current"));
        asOwner(get("/api/v1/finance/transactions?direction=OUT")).andExpect(jsonPath("$.totalElements").value(1));
        asOwner(get("/api/v1/finance/transactions?referenceType=NONE")).andExpect(jsonPath("$.totalElements").value(4));
        asOwner(get("/api/v1/finance/transactions?referenceType=PAYMENT")).andExpect(jsonPath("$.totalElements").value(0));
        asOwner(get("/api/v1/finance/transactions?sort=amount,asc"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));

        // AC2: from after to; an unknown sort property; AC3: an unknown account
        asOwner(get("/api/v1/finance/transactions?from=%s&to=%s".formatted(thisMonth, september)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        asOwner(get("/api/v1/finance/transactions?sort=description")).andExpect(status().isBadRequest());
        asOwner(get("/api/v1/finance/transactions?accountId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("FR-FIN-20 manual entry only no statement import route")
    void manualEntryOnly_noStatementImportRoute() throws Exception {
        String bank = account("SBI Current", "BANK", "1000.00", today());

        // no import, reconciliation or bank-feed route exists (post-MVP); money is entered by hand only
        for (String path : List.of("/api/v1/finance/accounts/" + bank + "/statements",
                "/api/v1/finance/statements/import", "/api/v1/finance/reconciliations")) {
            int code = asOwner(post(path).contentType(MediaType.TEXT_PLAIN).content("date,amount"))
                    .andReturn().getResponse().getStatus();
            assertThat(code).as(path).isIn(404, 405);
        }
        // no POST on the ledger itself either
        asOwner(postJson("/api/v1/finance/transactions", "{}")).andExpect(status().isMethodNotAllowed());
    }

    private String account(String name, String type, String opening, LocalDate date) throws Exception {
        String bank = type.equals("BANK") ? ",\"bankName\":\"SBI\"" : "";
        return body(asOwner(postJson("/api/v1/finance/accounts", """
                {"accountName":"%s","accountType":"%s"%s,"openingBalance":%s,"openingDate":"%s"}"""
                .formatted(name, type, bank, opening, date)))).get("id").asString();
    }

    private void adjust(String account, Direction direction, String amount, LocalDate date) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> finance.recordTransaction(
                new TransactionCommand(UUID.fromString(account), direction, new BigDecimal(amount),
                        TransactionType.ADJUSTMENT, date, "recount", null, TransactionReferenceType.NONE, null)));
    }
}

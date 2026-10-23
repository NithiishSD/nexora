package com.nexora.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CsvExportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-16 NFR-DATA-07 export returns csv with headers")
    void export_returnsCsvWithHeaders() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "0.00");
        UUID customer = data.customer().id();
        // 25 payments of one customer (more than a JSON page of 20) and one of another customer
        for (int i = 1; i <= 25; i++) {
            pay(customer, i + "000.00", bank);
        }
        pay(data.customer().id(), "999.00", bank);

        // AC1: the same filter, every matching row (no paging), a header row, money with 2 decimals
        String csv = asOwner(get("/api/v1/payments?format=csv&partyId=" + customer + "&from=" + today() + "&to="
                + today())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payments-" + today() + "-" + today() + ".csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(26);
        assertThat(lines[0]).startsWith("id,paymentNumber,").contains("amount");
        assertThat(csv).contains(",25000.00,").contains(",1000.00,").doesNotContain("999.00");
        // without format=csv the same list is still a JSON page of 20
        asOwner(get("/api/v1/payments?partyId=" + customer)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(20));

        // a report with several tables: a section column, the summary first (expense report: interest apart)
        String report = asOwner(get("/api/v1/reports/expenses?format=csv&from=" + today() + "&to=" + today()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(report.split("\r\n")[0]).startsWith("section,from,to,businessTotal,personalDrawingsTotal,interestCost");
        assertThat(report.split("\r\n")[1]).startsWith("summary," + today() + "," + today() + ",0.00,0.00,0.00");
        // a report that is an array: one row per location, nested movement types expanded, weights with 3 decimals
        String inventory = asOwner(get("/api/v1/reports/inventory-movements?format=csv&productType=RAW_SILK&from="
                + today() + "&to=" + today())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(inventory).startsWith("location,openingKg,closingKg\r\nRAW_STOCK,0.000,0.000\r\n");

        // AC3: without the list's permission -> 403
        mockMvc.perform(get("/api/v1/payments?format=csv").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.staff()))).andExpect(status().isForbidden());

        // AC2: more than 50000 matching rows -> 400 asking to narrow the period (still a JSON problem)
        jdbc.update("""
                insert into financial_transactions (financial_account_id, transaction_date, transaction_type, direction,
                                                    amount, description)
                select ?::uuid, ?::date, 'ADJUSTMENT', 'IN', 1.00, 'bulk ' || n from generate_series(1, 50001) n""",
                bank, today().toString());
        asOwner(get("/api/v1/finance/transactions?format=csv&accountId=" + bank)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("narrow the period")));
    }

    private void pay(UUID customer, String amount, String bank) throws Exception {
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"UPI","financialAccountId":"%s"}"""
                .formatted(customer, amount, bank))).andExpect(status().isCreated());
    }
}

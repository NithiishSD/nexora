package com.nexora.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class StatementImportApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-FIN-20 AC1 statement import parses rows and suggests matching transactions")
    void statementImport_matchesTransactions() throws Exception {
        UUID customer = data.customer().id();
        String bank = createAccount("SBI Current", "BANK", "50000.00");

        // Record a customer payment into the bank
        String paymentId = body(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":25000.00,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s","paymentDate":"%s","referenceNumber":"TXN-123456"}"""
                .formatted(customer, bank, today())))
                .andExpect(status().isCreated())).get("id").asString();

        UUID txId = jdbc.queryForObject("select id from financial_transactions where payment_id = ?",
                UUID.class, UUID.fromString(paymentId));

        // Create a bank statement CSV with a matching row and an unmatched row
        String csvContent = """
                Date,Description,Amount,Direction
                %s,NEFT/TXN-123456/KUMAR,25000.00,CR
                %s,BANK CHARGES,50.00,DR
                """.formatted(today(), today());

        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        owner(multipart("/api/v1/finance/accounts/" + bank + "/statement-imports").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.suggestedMatches").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(25000.00))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].matchedTransactionId").value(txId.toString()))
                .andExpect(jsonPath("$.items[1].amount").value(50.00))
                .andExpect(jsonPath("$.items[1].direction").value("OUT"))
                .andExpect(jsonPath("$.items[1].matchedTransactionId").doesNotExist());
    }

    @Test
    @DisplayName("FR-FIN-20 AC2 unparseable statement returns 400 malformed request")
    void unparseableStatement_returns400() throws Exception {
        String bank = createAccount("HDFC Bank", "BANK", "10000.00");

        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.csv", "text/csv", "this is completely not a statement".getBytes(StandardCharsets.UTF_8));

        owner(multipart("/api/v1/finance/accounts/" + bank + "/statement-imports").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FR-FIN-20 empty statement returns 400 malformed request")
    void emptyStatement_returns400() throws Exception {
        String bank = createAccount("Axis Bank", "BANK", "10000.00");

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        owner(multipart("/api/v1/finance/accounts/" + bank + "/statement-imports").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FR-FIN-20 statement import on cash account returns 400 validation failed")
    void cashAccount_returns400ValidationFailed() throws Exception {
        String cash = createAccount("Cash Box", "CASH", "5000.00");

        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.csv", "text/csv", ("Date,Amount\n" + today() + ",100.00").getBytes(StandardCharsets.UTF_8));

        owner(multipart("/api/v1/finance/accounts/" + cash + "/statement-imports").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private org.springframework.test.web.servlet.ResultActions owner(
            org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(org.springframework.http.HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())));
    }
}

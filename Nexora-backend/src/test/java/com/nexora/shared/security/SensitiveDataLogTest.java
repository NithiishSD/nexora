package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import tools.jackson.databind.JsonNode;

import com.nexora.support.AbstractApiTest;

// NFR-SEC-06, FR-AUTH-08: passwords, password hashes and tokens never reach the log, even at DEBUG, and the hash never
// reaches a response. The log levels are raised for the test so that every log line the flows can write is searched.
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLogTest extends AbstractApiTest {

    @Value("${nexora.bootstrap.owner-username}")
    String username;

    @Value("${nexora.bootstrap.owner-password}")
    String password;

    private final LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());

    @BeforeEach
    void verbose() {
        logging.setLogLevel("com.nexora", LogLevel.DEBUG);
        logging.setLogLevel("org.springframework.security", LogLevel.DEBUG);
        logging.setLogLevel("org.springframework.web", LogLevel.DEBUG);
    }

    @AfterEach
    void quiet() {
        logging.setLogLevel("com.nexora", null);
        logging.setLogLevel("org.springframework.security", null);
        logging.setLogLevel("org.springframework.web", null);
    }

    @Test
    @DisplayName("NFR-SEC-06 FR-AUTH-08 login and payment logs contain no password token or account number")
    void loginAndPayment_logsContainNoPasswordTokenOrAccountNumber(CapturedOutput output) throws Exception {
        // AC1: login, refresh and logout; a wrong password; a malformed body carrying a password (AC2)
        JsonNode login = body(mockMvc.perform(json("/api/v1/auth/login", """
                {"username":"%s","password":"%s"}""".formatted(username, password))).andExpect(status().isOk()));
        String access = login.get("accessToken").asString();
        String refresh = login.get("refreshToken").asString();
        JsonNode refreshed = body(mockMvc.perform(json("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}""".formatted(refresh))).andExpect(status().isOk()));
        String newRefresh = refreshed.get("refreshToken").asString();
        mockMvc.perform(json("/api/v1/auth/logout", "{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());
        String wrong = "Wrong#Secret-" + System.nanoTime();
        mockMvc.perform(json("/api/v1/auth/login", """
                {"username":"%s","password":"%s"}""".formatted(username, wrong))).andExpect(status().isUnauthorized());
        String broken = "Broken#Secret-" + System.nanoTime();
        mockMvc.perform(json("/api/v1/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + broken
                + "\",")).andExpect(status().isBadRequest());

        // a bank account (last 4 digits only) and a payment into it
        String bank = createAccount("SBI Current", "BANK", "1000.00");
        asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"CUSTOMER","partyId":"%s","amount":500.00,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s","referenceNumber":"UTR 4471"}""".formatted(data.customer().id(), bank)))
                .andExpect(status().isCreated());

        // the hash is never in a response either (FR-AUTH-08 AC2)
        String hash = jdbc.queryForObject("select password_hash from users where username = ?", String.class, username);
        assertThat(hash).startsWith("{bcrypt}");
        String me = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain(hash).doesNotContain(password).doesNotContain("password");

        String log = output.getAll();
        assertThat(log).as("the flows did log at DEBUG").contains("event=payment_recorded");
        for (String secret : new String[] {password, wrong, broken, hash, access, refresh, newRefresh}) {
            assertThat(log).as("log must not contain a secret").doesNotContain(secret);
        }
        // a full account number is never even accepted: only last 4 digits are stored, shown masked
        assertThat(jdbc.queryForObject("select count(*) from financial_accounts where masked_account_number !~"
                + " '^X{6}[0-9]{4}$' and masked_account_number is not null", Integer.class)).isZero();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(String path,
                                                                                                   String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

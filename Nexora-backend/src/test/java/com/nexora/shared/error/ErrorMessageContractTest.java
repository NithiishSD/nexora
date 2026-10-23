package com.nexora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

// NFR-USE-04: every problem body's detail is plain text of at most 200 characters, without stack traces, class or
// package names, SQL or constraint names; the stable code is what the client turns into a message.
class ErrorMessageContractTest extends AbstractApiTest {

    private static final Pattern INTERNAL = Pattern.compile(
            "(?i)exception|com\\.nexora|java\\.|org\\.|tools\\.jackson|\\bat [a-z]+\\.|select |insert |update .* set "
                    + "|delete from|\\buq_|\\bfk_|\\bck_|sqlstate|hibernate|jdbc|`");

    @Test
    @DisplayName("NFR-USE-04 every problem detail is short plain text without internal names")
    void everyProblemDetail_isShortPlainTextWithoutInternalNames() throws Exception {
        String bank = createAccount("SBI Current", "BANK", "100.00");
        String supplier = data.supplier().id().toString();
        String purchase = createPurchase(UUID.fromString(supplier), "RAW_SILK", "10.000", "800.00");
        List<ResultActions> errors = new ArrayList<>();
        // 400 validation, malformed JSON, wrong type, unknown enum, bad id, bad sort, bad range
        errors.add(asOwner(postJson("/api/v1/parties", "{\"name\":\"\",\"roles\":[]}")));
        errors.add(asOwner(postJson("/api/v1/parties", "{\"name\":")));
        errors.add(asOwner(postJson("/api/v1/purchases", """
                {"supplierId":"%s","purchaseDate":"2026-10-01","orderedWeightKg":"lots","ratePerKg":1,
                 "paymentType":"CREDIT"}""".formatted(supplier))));
        errors.add(asOwner(get("/api/v1/purchases?productType=COTTON")));
        errors.add(asOwner(get("/api/v1/purchases/not-a-uuid")));
        errors.add(asOwner(get("/api/v1/purchases?sort=secretColumn")));
        errors.add(asOwner(get("/api/v1/finance/transactions?from=2026-10-09&to=2026-10-01")));
        // 401, 403, 404, 405, 415
        errors.add(mockMvc.perform(get("/api/v1/purchases")));
        errors.add(mockMvc.perform(get("/api/v1/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z")));
        errors.add(mockMvc.perform(get("/api/v1/finance/accounts").header(HttpHeaders.AUTHORIZATION,
                AuthTokens.bearer(tokens.staff()))));
        errors.add(asOwner(get("/api/v1/purchases/" + UUID.randomUUID())));
        errors.add(asOwner(get("/api/v1/no-such-thing")));
        errors.add(asOwner(delete("/api/v1/payments/" + UUID.randomUUID())));
        errors.add(asOwner(postJson("/api/v1/parties", "{}").contentType("text/plain")));
        errors.add(mockMvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {77, 90, 0}))
                .param("documentType", "OTHER").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner()))));
        // 409 and 422 business rules
        errors.add(receive(purchase, "20.000", "20.000", "0.000"));
        errors.add(asOwner(postWithKey("/api/v1/payments", """
                {"purpose":"SUPPLIER","partyId":"%s","amount":99999.00,"paymentMethod":"BANK_TRANSFER",
                 "financialAccountId":"%s"}""".formatted(supplier, bank))));
        errors.add(asOwner(postJson("/api/v1/inventory/wastage", """
                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":5.000,"movementDate":"%s",
                 "reason":"x"}""".formatted(today()))));
        errors.add(asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":[{"productType":"RAW_SILK","requiredWeightKg":1,
                 "ratePerKg":1}]}""".formatted(supplier, today()))));
        errors.add(asOwner(postWithKey("/api/v1/finance/transfers", """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":1000.00,"transferDate":"%s"}"""
                .formatted(bank, createAccount("Cash", "CASH", "0.00"), today()))));

        List<String> problems = new ArrayList<>();
        for (ResultActions error : errors) {
            var response = error.andReturn().getResponse();
            JsonNode body = jsonMapper.readTree(response.getContentAsString());
            String where = error.andReturn().getRequest().getMethod() + " " + error.andReturn().getRequest()
                    .getRequestURI() + " -> " + response.getStatus() + ": ";
            assertThat(response.getStatus()).as(where).isBetween(400, 499);
            assertThat(body.path("code").asString()).as(where + "code").isNotBlank();
            String detail = body.path("detail").asString();
            if (detail.isBlank() || detail.length() > 200 || INTERNAL.matcher(detail).find()) {
                problems.add(where + detail);
            }
        }
        // 500: whatever went wrong inside, the client only gets the generic sentence (AC2)
        GlobalExceptionHandler handler = new GlobalExceptionHandler(clock);
        String generic = handler.handleGeneric(new IllegalStateException("ERROR: relation \"payments\" in"
                + " com.nexora.x.Y"), new MockHttpServletRequest("GET", "/x")).getDetail();
        assertThat(INTERNAL.matcher(generic).find()).isFalse();
        assertThat(problems).as("problem details that are too long or show internal names").isEmpty();
    }
}

package com.nexora.parties.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class CustomerTermsApiTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-PARTY-17 update customer terms sets credit period days")
    void updateCustomerTerms_setsCreditPeriodDays() throws Exception {
        UUID customerId = data.customer().id();

        asOwner(patch("/api/v1/parties/" + customerId + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": 30}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.customerCreditDays").value(30));

        // Read back via GET /parties/{id}
        asOwner(get("/api/v1/parties/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCreditDays").value(30));
    }

    @Test
    @DisplayName("FR-PARTY-17 update customer terms rejects non-customer party")
    void updateCustomerTerms_rejectsNonCustomer() throws Exception {
        UUID supplierId = data.supplier().id();

        asOwner(patch("/api/v1/parties/" + supplierId + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": 30}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("customerCreditDays"));
    }

    @Test
    @DisplayName("FR-PARTY-17 update customer terms validates credit period range")
    void updateCustomerTerms_validatesRange() throws Exception {
        UUID customerId = data.customer().id();

        // Negative credit days -> 400
        asOwner(patch("/api/v1/parties/" + customerId + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": -5}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Over 365 credit days -> 400
        asOwner(patch("/api/v1/parties/" + customerId + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": 400}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-PARTY-17 update customer terms handles not found and security")
    void updateCustomerTerms_notFoundAndSecurity() throws Exception {
        UUID customerId = data.customer().id();
        UUID unknownId = UUID.randomUUID();

        // Unknown party -> 404
        asOwner(patch("/api/v1/parties/" + unknownId + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": 15}
                        """))
                .andExpect(status().isNotFound());

        // Unauthenticated -> 401
        mockMvc.perform(patch("/api/v1/parties/" + customerId + "/customer-terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"creditPeriodDays": 15}
                                """))
                .andExpect(status().isUnauthorized());

        // Staff token without PARTY_MANAGE -> 403
        mockMvc.perform(patch("/api/v1/parties/" + customerId + "/customer-terms")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"creditPeriodDays": 15}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PARTY-17 outstanding calculates dueDate and overdue with customer credit days")
    void outstanding_calculatesDueDateAndOverdueWithCreditDays() throws Exception {
        UUID customer = data.customer().id();

        // Configure 30 days credit terms
        asOwner(patch("/api/v1/parties/" + customer + "/customer-terms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"creditPeriodDays": 30}
                        """))
                .andExpect(status().isOk());

        // Order placed 40 days ago -> overdue (40 > 30)
        confirmed(customer, today().minusDays(40), "10.000");
        // Order placed 10 days ago -> not overdue (10 <= 30)
        confirmed(customer, today().minusDays(10), "5.000");

        asOwner(get("/api/v1/customers/" + customer + "/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditDays").value(30))
                .andExpect(jsonPath("$.orders[0].ageDays").value(40))
                .andExpect(jsonPath("$.orders[0].dueDate").value(today().minusDays(10).toString()))
                .andExpect(jsonPath("$.orders[0].overdue").value(true))
                .andExpect(jsonPath("$.orders[1].ageDays").value(10))
                .andExpect(jsonPath("$.orders[1].dueDate").value(today().plusDays(20).toString()))
                .andExpect(jsonPath("$.orders[1].overdue").value(false));
    }

    private void confirmed(UUID customer, java.time.LocalDate date, String kg) throws Exception {
        String orderId = orderOn(customer, date, kg);
        asOwner(postJson("/api/v1/orders/" + orderId + "/confirm", "{}")).andExpect(status().isOk());
    }

    private String orderOn(UUID customer, java.time.LocalDate date, String kg) throws Exception {
        var result = asOwner(postJson("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":[{"productType":"KORA_WARP","requiredWeightKg":%s,"ratePerKg":1000.00}]}
                """.formatted(customer, date, kg)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

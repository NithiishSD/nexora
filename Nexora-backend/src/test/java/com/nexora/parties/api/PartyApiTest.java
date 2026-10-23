package com.nexora.parties.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Year;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

class PartyApiTest extends AbstractApiTest {

    @Autowired
    Clock clock;

    @Test
    @DisplayName("FR-PARTY-01 FR-PARTY-03 create with valid request returns201 with party code")
    void create_withValidRequest_returns201WithPartyCode() throws Exception {
        String year = String.valueOf(Year.now(clock).getValue());

        create("""
                {"name": "Kumar Silks", "roles": ["CUSTOMER"], "city": "Kanchipuram",
                 "phones": [ {"phoneNumber": "+91 98400 12345", "label": "mobile"} ]}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/v1/parties/")))
                .andExpect(jsonPath("$.partyCode").value("PTY-" + year + "-0001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phones[0].primary").value(true));
        // the next party gets the next number
        create("{\"name\": \"Second\", \"roles\": [\"SUPPLIER\"]}")
                .andExpect(jsonPath("$.partyCode").value("PTY-" + year + "-0002"));
    }

    @Test
    @DisplayName("FR-PARTY-01 NFR-TEST-04 create with blank name returns400 validation failed")
    void create_withBlankName_returns400ValidationFailed() throws Exception {
        create("{\"name\": \"  \", \"roles\": [\"CUSTOMER\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        assertThat(partyCount()).isZero();               // nothing stored
    }

    @Test
    @DisplayName("FR-PARTY-01 NFR-TEST-04 create with empty roles returns400 validation failed")
    void create_withEmptyRoles_returns400ValidationFailed() throws Exception {
        create("{\"name\": \"X\", \"roles\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roles"));
    }

    @Test
    @DisplayName("FR-PARTY-02 create with unknown role returns400 malformed request")
    void create_withUnknownRole_returns400MalformedRequest() throws Exception {
        create("{\"name\": \"X\", \"roles\": [\"ADMIN\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(partyCount()).isZero();
    }

    @Test
    @DisplayName("FR-PARTY-04 create with two primary phones returns400 validation failed")
    void create_withTwoPrimaryPhones_returns400() throws Exception {
        create("""
                {"name": "X", "roles": ["CUSTOMER"], "phones": [
                  {"phoneNumber": "9840012345", "label": "mobile", "primary": true},
                  {"phoneNumber": "9840054321", "label": "home", "primary": true}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("phones"));
        // FR-PARTY-03 AC2: the failed request did not use up a number
        create("{\"name\": \"Y\", \"roles\": [\"CUSTOMER\"]}")
                .andExpect(jsonPath("$.partyCode").value(org.hamcrest.Matchers.endsWith("-0001")));
    }

    @Test
    @DisplayName("FR-PARTY-13 FR-PARTY-14 defaults without matching role return400 validation failed")
    void create_defaultsWithoutRole_return400() throws Exception {
        create("{\"name\": \"X\", \"roles\": [\"CUSTOMER\"], \"defaultCreditDays\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("defaultCreditDays"));
        create("{\"name\": \"X\", \"roles\": [\"SUPPLIER\"], \"defaultCreditDays\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("defaultCreditDays"));
        create("{\"name\": \"X\", \"roles\": [\"WORKER\"], \"defaultRollingRatePerHour\": -1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-PARTY-05 create without address is accepted")
    void create_withoutAddress_isAccepted() throws Exception {
        create("{\"name\": \"Worker Ravi\", \"roles\": [\"WORKER\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value(nullValue()))
                .andExpect(jsonPath("$.pincode").value(nullValue()))
                .andExpect(jsonPath("$.phones.length()").value(0));
    }

    @Test
    @DisplayName("FR-PARTY-05 NFR-SEC-03 create with too long city returns400 validation failed")
    void create_withTooLongCity_returns400ValidationFailed() throws Exception {
        create("{\"name\": \"X\", \"roles\": [\"CUSTOMER\"], \"city\": \"" + "c".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("city"));
        create("{\"name\": \"" + "n".repeat(201) + "\", \"roles\": [\"CUSTOMER\"]}")    // NFR-SEC-03 AC2
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("FR-PARTY-01 staff cannot create a party")
    void create_asStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/parties").header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\", \"roles\": [\"CUSTOMER\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(partyCount()).isZero();
    }

    @Test
    @DisplayName("FR-PARTY-06 get returns roles phones defaults and audit fields")
    void get_returnsRolesPhonesDefaultsAndAuditFields() throws Exception {
        String body = create("""
                {"name": "Ramesh Silks", "roles": ["SUPPLIER", "CUSTOMER"], "defaultCreditDays": 10,
                 "phones": [ {"phoneNumber": "9443011111", "label": "mobile"},
                             {"phoneNumber": "9443022222", "label": "WhatsApp", "primary": true} ]}
                """).andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(body).get("id").asString();

        mockMvc.perform(get("/api/v1/parties/" + id).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))           // sorted alphabetically
                .andExpect(jsonPath("$.roles[1]").value("SUPPLIER"))
                .andExpect(jsonPath("$.phones[0].phoneNumber").value("9443022222"))   // primary first
                .andExpect(jsonPath("$.phones[0].primary").value(true))
                .andExpect(jsonPath("$.phones[1].primary").value(false))
                .andExpect(jsonPath("$.defaultCreditDays").value(10))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.createdBy").isString())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("FR-PARTY-06 get unknown id returns404 resource not found")
    void get_unknownId_returns404ResourceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/parties/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/parties/not-a-uuid").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FR-PARTY-07 list filtered by role status and search returns page")
    void list_filteredByRoleStatusAndSearch_returnsPage() throws Exception {
        create("{\"name\": \"Ramesh Silks\", \"roles\": [\"SUPPLIER\"], \"city\": \"Salem\","
                + " \"phones\": [{\"phoneNumber\": \"+91 94430 11111\", \"label\": \"mobile\"}]}");
        create("{\"name\": \"Rama Yarns\", \"roles\": [\"SUPPLIER\", \"CUSTOMER\"]}");
        create("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}");
        String inactive = createdId("{\"name\": \"Ramu Old\", \"roles\": [\"SUPPLIER\"]}");
        action(inactive, "deactivate").andExpect(status().isOk());

        // AC1: "ram" matches both Ramesh and Rama (and the inactive Ramu)
        list("search=ram&status=ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0));
        list("search=RAMESH")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ramesh Silks"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("SUPPLIER"))
                .andExpect(jsonPath("$.content[0].primaryPhone").value("+91 94430 11111"))
                .andExpect(jsonPath("$.content[0].city").value("Salem"));
        list("role=CUSTOMER&sort=name,asc")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Kumar Silks"))
                .andExpect(jsonPath("$.content[1].name").value("Rama Yarns"));
        list("search=94430")                                             // matches a phone number
                .andExpect(jsonPath("$.totalElements").value(1));
        list("search=PTY-")                                              // matches the party code
                .andExpect(jsonPath("$.totalElements").value(4));
        list("role=SUPPLIER&status=INACTIVE")
                .andExpect(jsonPath("$.content[0].name").value("Ramu Old"));
        search("' OR 1=1 --")                                            // NFR-SEC-10 AC1: just text
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        list("role=ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FR-PARTY-07 NFR-SEC-10 list search with percent sign is escaped")
    void list_searchWithPercentSign_isEscaped() throws Exception {
        create("{\"name\": \"Pure 100% Silk\", \"roles\": [\"CUSTOMER\"]}");
        create("{\"name\": \"100 Threads\", \"roles\": [\"CUSTOMER\"]}");
        create("{\"name\": \"A_B Traders\", \"roles\": [\"CUSTOMER\"]}");

        search("100%").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pure 100% Silk"));
        search("_").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("A_B Traders"));
    }

    @Test
    @DisplayName("NFR-SEC-03 list page size above100 is capped")
    void list_pageSizeAbove100_isCapped() throws Exception {
        list("size=500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("FR-PARTY-07 list unknown sort property returns400 validation failed")
    void list_unknownSortProperty_returns400ValidationFailed() throws Exception {
        list("sort=doesNotExist")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        list("search=" + "x".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("FR-PARTY-08 patch replaces phones and keeps party code")
    void patch_replacesPhonesAndKeepsPartyCode() throws Exception {
        String body = create("""
                {"name": "Kumar Silks", "roles": ["CUSTOMER"], "city": "Kanchipuram", "area": "Big Street",
                 "phones": [ {"phoneNumber": "1111111111", "label": "mobile"},
                             {"phoneNumber": "2222222222", "label": "home"} ]}
                """).andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(body).get("id").asString();
        String code = jsonMapper.readTree(body).get("partyCode").asString();

        update(id, """
                {"name": "Kumar Silk House", "city": "Salem", "area": "",
                 "phones": [ {"phoneNumber": "3333333333", "label": "WhatsApp"} ]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kumar Silk House"))
                .andExpect(jsonPath("$.city").value("Salem"))
                .andExpect(jsonPath("$.area").value(nullValue()))                  // "" clears the field
                .andExpect(jsonPath("$.partyCode").value(code))
                .andExpect(jsonPath("$.phones.length()").value(1))
                .andExpect(jsonPath("$.phones[0].phoneNumber").value("3333333333"))
                .andExpect(jsonPath("$.phones[0].primary").value(true));
        // fields that were not sent keep their values
        update(id, "{\"note\": \"pays on time\"}")
                .andExpect(jsonPath("$.name").value("Kumar Silk House"))
                .andExpect(jsonPath("$.phones.length()").value(1))
                .andExpect(jsonPath("$.note").value("pays on time"));
        assertThat(jdbc.queryForObject("select count(*) from party_phone_numbers where party_id = ?::uuid",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select updated_at > created_at from parties where id = ?::uuid",
                Boolean.class, id)).isTrue();
        // AC3: unknown id
        update(UUID.randomUUID().toString(), "{\"name\": \"X\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        // a blank name is rejected; so is a role-specific default without the role
        update(id, "{\"name\": \" \"}").andExpect(status().isBadRequest());
        update(id, "{\"defaultCreditDays\": 5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("defaultCreditDays"));
    }

    @Test
    @DisplayName("FR-PARTY-09 patch add role keeps existing roles")
    void patch_addRole_keepsExistingRoles() throws Exception {
        String id = createdId("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}");

        addRole(id, "{\"role\": \"SUPPLIER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.roles[1]").value("SUPPLIER"));
        addRole(id, "{\"role\": \"SUPPLIER\"}")                                     // AC2: no duplicate
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
        addRole(id, "{\"role\": \"ADMIN\"}")                                        // AC3
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        // the supplier default is allowed now that the role is held
        update(id, "{\"defaultCreditDays\": 15}").andExpect(jsonPath("$.defaultCreditDays").value(15));
    }

    @Test
    @DisplayName("FR-PARTY-10 deactivate then activate switches status")
    void deactivate_thenActivate_switchesStatus() throws Exception {
        String id = createdId("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}");

        action(id, "deactivate").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        action(id, "deactivate")                                                    // AC2
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        action(id, "activate").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        action(id, "activate")                                                      // AC3
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        // staff may read but not change
        mockMvc.perform(post("/api/v1/parties/" + id + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.staff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PARTY-15 delete is not supported returns405")
    void delete_isNotSupported_returns405() throws Exception {
        String id = createdId("{\"name\": \"Kumar Silks\", \"roles\": [\"CUSTOMER\"]}");

        mockMvc.perform(delete("/api/v1/parties/" + id).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        assertThat(partyCount()).isEqualTo(1);
    }

    // ---- helpers ---------------------------------------------------------------------------

    ResultActions update(String id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/parties/" + id).header(HttpHeaders.AUTHORIZATION, owner())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions addRole(String id, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/parties/" + id + "/roles").header(HttpHeaders.AUTHORIZATION, owner())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions action(String id, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/parties/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, owner()));
    }

    // search text passed as a request parameter, so characters such as % are sent exactly as typed
    ResultActions search(String text) throws Exception {
        return mockMvc.perform(get("/api/v1/parties").param("search", text).header(HttpHeaders.AUTHORIZATION, owner()));
    }

    ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/parties?" + query).header(HttpHeaders.AUTHORIZATION, owner()));
    }

    String owner() {
        return AuthTokens.bearer(tokens.owner());
    }

    ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/parties").header(HttpHeaders.AUTHORIZATION, owner())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String createdId(String json) throws Exception {
        return jsonMapper.readTree(create(json).andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    int partyCount() {
        return jdbc.queryForObject("select count(*) from parties", Integer.class);
    }
}

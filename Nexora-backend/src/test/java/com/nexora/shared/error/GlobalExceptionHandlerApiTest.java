package com.nexora.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;
import com.nexora.support.TestProbeController;

// Spring MVC's own errors (unknown URL, wrong method, bad JSON, failed @Valid) must come back in the
// same problem+json shape as our business errors, with a stable "code".
// The test-only probe controller gives us a POST endpoint with a JSON body.
@Import(TestProbeController.class)
class GlobalExceptionHandlerApiTest extends AbstractApiTest {

    private static final String PROBE = TestProbeController.BODY;

    @Test
    @DisplayName("NFR-USE-04 unknown path returns404 problem with code")
    void unknownPath_returns404ProblemWithCode() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("NFR-USE-04 wrong method returns405 method not allowed")
    void wrongMethod_returns405MethodNotAllowed() throws Exception {
        mockMvc.perform(get(PROBE).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("NFR-USE-04 malformed json returns400 malformed request")
    void malformedJson_returns400MalformedRequest() throws Exception {
        mockMvc.perform(post(PROBE).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))                       // cut-off JSON
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                // never echo parser internals (class names, stack traces) to the client
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("tools.jackson"))));
    }

    @Test
    @DisplayName("NFR-USE-04 invalid body returns400 validation failed with field errors")
    void invalidBody_returns400ValidationFailedWithFieldErrors() throws Exception {
        mockMvc.perform(post(PROBE).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("NFR-USE-04 wrong content type returns415 unsupported media type")
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post(PROBE).header(HttpHeaders.AUTHORIZATION, AuthTokens.bearer(tokens.owner()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}

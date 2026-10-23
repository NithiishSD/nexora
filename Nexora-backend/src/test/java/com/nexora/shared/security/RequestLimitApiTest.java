package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nexora.support.AbstractApiTest;

class RequestLimitApiTest extends AbstractApiTest {

    @Autowired
    RequestSizeLimitFilter filter;

    @Test
    @DisplayName("NFR-SEC-03 json body over1 mb is rejected")
    void jsonBodyOver1Mb_isRejected() throws Exception {
        // AC1: a 2 MB JSON body is a 4xx problem body, never a 5xx; nothing is created
        String name = "x".repeat(2 * 1024 * 1024);
        asOwner(postJson("/api/v1/parties", "{\"name\":\"" + name + "\",\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        assertThat(jdbc.queryForObject("select count(*) from parties", Integer.class)).isZero();
        // a body just under the limit is read normally (here a validation error on the name length, AC2)
        asOwner(postJson("/api/v1/parties", "{\"name\":\"" + "y".repeat(201) + "\",\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));

        // a declared upload above 10 MB + form overhead is refused before parsing, as the document check would
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/documents") {
            @Override
            public long getContentLengthLong() {
                return RequestSizeLimitFilter.DOCUMENT_LIMIT + 1;
            }
        }, refused, (req, res) -> {
            throw new AssertionError("must not reach the application");
        });
        assertThat(refused.getStatus()).isEqualTo(400);
        assertThat(refused.getContentAsString()).contains("\"field\":\"file\"");

        // a JSON body without a length (chunked) is cut off at 1 MiB while it is read
        MockHttpServletRequest chunked = new MockHttpServletRequest("POST", "/api/v1/parties") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        chunked.setContent("z".repeat(2 * 1024 * 1024).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> filter.doFilter(chunked, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes())).isInstanceOf(IOException.class);
    }
}

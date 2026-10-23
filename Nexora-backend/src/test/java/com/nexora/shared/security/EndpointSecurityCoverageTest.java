package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.AuthTokens;

// Walks EVERY endpoint the application registers (new endpoints are covered automatically) and proves:
// no token -> 401, and a STAFF token without the endpoint's permission -> 403 ACCESS_DENIED (NFR-SEC-09).
// Every non-public endpoint must carry @PreAuthorize.
class EndpointSecurityCoverageTest extends AbstractApiTest {

    // FR-AUTH-12: reachable without a token
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/v1/auth/login", "POST /api/v1/auth/refresh", "POST /api/v1/auth/logout",
            "GET /api/v1/setup/status", "POST /api/v1/setup/initialize");

    // The STAFF role's permissions (V5 seed, assumption Q8)
    private static final Set<String> STAFF_PERMISSIONS = Set.of("PARTY_VIEW", "INVENTORY_VIEW", "ORDER_VIEW",
            "PRODUCTION_VIEW");

    private static final Pattern HAS_AUTHORITY = Pattern.compile("^hasAuthority\\('([A-Z_]+)'\\)$");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("NFR-SEC-09 FR-AUTH-09 every endpoint rejects missing token and missing permission")
    void everyEndpoint_rejectsMissingTokenAndMissingPermission() throws Exception {
        String staff = AuthTokens.bearer(tokens.staff());
        List<String> problems = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.nexora")) {
                continue;                                          // framework endpoints (API docs, error page)
            }
            for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                // a path variable gets a random id; for the security checks the record need not exist
                String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
                    String endpoint = method.name() + " " + pattern;
                    if (PUBLIC.contains(endpoint)) {
                        continue;
                    }
                    checked++;
                    PreAuthorize preAuthorize = handler.getMethodAnnotation(PreAuthorize.class);
                    if (preAuthorize == null) {
                        problems.add(endpoint + ": no @PreAuthorize");
                        continue;
                    }
                    MvcResult anonymous = mockMvc.perform(call(method, path, null)).andReturn();
                    if (anonymous.getResponse().getStatus() != 401) {
                        problems.add(endpoint + ": without token " + anonymous.getResponse().getStatus());
                    }
                    Matcher matcher = HAS_AUTHORITY.matcher(preAuthorize.value());
                    boolean staffLacksPermission = matcher.matches() && !STAFF_PERMISSIONS.contains(matcher.group(1));
                    MvcResult asStaff = mockMvc.perform(call(method, path, staff)).andReturn();
                    int status = asStaff.getResponse().getStatus();
                    if (staffLacksPermission && (status != 403
                            || !asStaff.getResponse().getContentAsString().contains("\"ACCESS_DENIED\""))) {
                        problems.add(endpoint + ": STAFF without permission got " + status);
                    }
                    if (!staffLacksPermission && (status == 401 || status == 403)) {
                        problems.add(endpoint + ": STAFF holding the permission got " + status);
                    }
                }
            }
        }

        assertThat(checked).as("protected endpoints checked").isGreaterThanOrEqualTo(8);
        assertThat(problems).isEmpty();
    }

    // An (empty) JSON body is sent to writes: the permission is checked before the body is validated.
    private static MockHttpServletRequestBuilder call(RequestMethod method, String path, String authorization) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method.name()), path);
        if (method == RequestMethod.POST || method == RequestMethod.PATCH || method == RequestMethod.PUT) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return builder;
    }
}

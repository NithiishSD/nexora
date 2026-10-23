package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.nexora.support.AbstractApiTest;

// NFR-DATA-03: business records are kept (10 years and more): no endpoint deletes anything, no code deletes business
// rows, no job purges them. The only delete in the code is the 48-hour expiry of technical idempotency keys
// (05-API-SPEC section 1.9), which holds no business data.
class RetentionTest extends AbstractApiTest {

    private static final Pattern SQL_DELETE = Pattern.compile("(?i)delete\\s+from\\s+(\\w+)");
    private static final Pattern REPOSITORY_DELETE = Pattern.compile("\\.(delete|deleteAll|deleteById|deleteAllById"
            + "|deleteAllInBatch|deleteInBatch)\\(|\\bdeleteBy\\w*\\(|\\bTRUNCATE\\b");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("NFR-DATA-03 no scheduled purge and no delete endpoints")
    void noScheduledPurgeAndNoDeleteEndpoints() throws Exception {
        // no DELETE route at all
        assertThat(mappings.getHandlerMethods().keySet()).noneMatch(info ->
                info.getMethodsCondition().getMethods().contains(RequestMethod.DELETE));
        // AC1: deleting financial or inventory records through the API is 405
        for (String path : List.of("/api/v1/payments/", "/api/v1/finance/transactions/", "/api/v1/expenses/",
                "/api/v1/purchases/", "/api/v1/orders/", "/api/v1/parties/", "/api/v1/finance/accounts/",
                "/api/v1/inventory/movements/", "/api/v1/documents/")) {
            asOwner(delete(path + UUID.randomUUID())).andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
        // no code deletes business rows; SQL deletes touch only the technical idempotency keys
        assertThat(SourceScan.find(REPOSITORY_DELETE, file -> true)).isEmpty();
        assertThat(SourceScan.find(SQL_DELETE, file -> true)).allSatisfy(line ->
                assertThat(line).contains("delete from idempotency_keys"));
    }
}

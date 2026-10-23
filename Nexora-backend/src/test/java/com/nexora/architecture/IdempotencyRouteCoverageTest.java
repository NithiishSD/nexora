package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.nexora.shared.idempotency.Idempotent;
import com.nexora.support.AbstractIntegrationTest;

// 03-ARCHITECTURE 7.2: a key is required on exactly the writes that move money or stock and could be duplicated by a
// retry. A new money route without @Idempotent, or a key on a route that should not take one, fails here.
class IdempotencyRouteCoverageTest extends AbstractIntegrationTest {

    private static final Set<String> EXPECTED = Set.of(
            "POST /api/v1/purchases/{id}/receipts",
            "POST /api/v1/purchases/{id}/returns",
            "POST /api/v1/orders/{id}/deliver",
            "POST /api/v1/payments",
            "POST /api/v1/payments/{id}/reverse",
            "POST /api/v1/finance/accounts/{id}/adjustments",
            "POST /api/v1/finance/transfers",
            "POST /api/v1/expenses",
            "POST /api/v1/expenses/{id}/reverse",
            "POST /api/v1/loans",
            "POST /api/v1/loans/{id}/principal-repayments",
            "POST /api/v1/loans/{id}/interest-payments",
            "POST /api/v1/loans/{id}/payments/{transactionId}/reverse",
            "POST /api/v1/chits/{id}/contributions",
            "POST /api/v1/chits/{id}/payout",
            "POST /api/v1/chits/{id}/organiser-payments",
            "POST /api/v1/chits/{id}/entries/{transactionId}/reverse",
            "POST /api/v1/outsourcing-jobs/{id}/receive",
            "POST /api/v1/orders/{id}/returns",
            "POST /api/v1/orders/{id}/items/{itemId}/deliveries",
            "POST /api/v1/payments/{id}/refunds");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("FR-PAY-07 FR-PAY-25 FR-PROC-19 FR-FIN-18 FR-ORD-20 FR-ORD-19 exactly the twenty-one listed routes require idempotency key")
    void exactlyTheNineteenListedRoutes_requireIdempotencyKey() {
        Set<String> annotated = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            // the test-only probe controller (support package) is not part of the application
            if (handler.hasMethodAnnotation(Idempotent.class)
                    && !handler.getBeanType().getPackageName().startsWith("com.nexora.support")) {
                info.getMethodsCondition().getMethods().forEach(method -> info.getPathPatternsCondition()
                        .getPatternValues().forEach(path -> annotated.add(method + " " + path)));
            }
        });

        assertThat(annotated).hasSize(21).containsExactlyInAnyOrderElementsOf(EXPECTED);
    }
}

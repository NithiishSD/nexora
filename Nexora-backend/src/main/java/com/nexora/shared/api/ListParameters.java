package com.nexora.shared.api;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nexora.shared.error.FieldValidationException;

// Checks shared by every list endpoint (05-API-SPEC section 1.7). Both failures are 400 VALIDATION_FAILED
// with the field named in errors[], never a 500 from deep inside the query.
public final class ListParameters {

    private ListParameters() {
    }

    // Only the documented sort properties are allowed (an entity may have more fields than the API exposes).
    public static void requireSortable(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new FieldValidationException("sort", "Sorting by '" + order.getProperty()
                        + "' is not supported; use one of " + String.join(", ", allowed.stream().sorted().toList()) + ".");
            }
        }
    }

    // An inclusive date range must not run backwards.
    public static void requireDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException("from", "'from' must not be later than 'to'.");
        }
    }
}

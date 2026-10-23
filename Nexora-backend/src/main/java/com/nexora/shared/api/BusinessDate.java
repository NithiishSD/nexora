package com.nexora.shared.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// A business date in a request (receipt date, movement date ...) must be plausible: not more than 10 years
// back and not more than 366 days ahead of today in Asia/Kolkata (05-API-SPEC 1.3, 09-SECURITY 5). It catches
// typing mistakes such as 2206 instead of 2026. null is allowed here; combine with @NotNull when required.
@Documented
@Constraint(validatedBy = BusinessDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessDate {

    String message() default "must be within the last 10 years and at most 366 days ahead";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

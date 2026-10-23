package com.nexora.shared.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a controller method whose POST must carry an Idempotency-Key header (03-ARCHITECTURE 7.2, ADR-033):
// a retry with the same key and body gets the stored response instead of running the use case again.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}

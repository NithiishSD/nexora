package com.nexora.shared.api;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// Checks @BusinessDate. Spring creates validators as beans, so the application Clock (Asia/Kolkata) is injected
// through the constructor: "today" is the business's today, and tests could fix it.
public class BusinessDateValidator implements ConstraintValidator<BusinessDate, LocalDate> {

    private final Clock clock;

    public BusinessDateValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;                                        // @NotNull decides whether a value is required
        }
        LocalDate today = LocalDate.now(clock);
        return !value.isBefore(today.minusYears(10)) && !value.isAfter(today.plusDays(366));
    }
}

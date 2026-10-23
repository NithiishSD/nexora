package com.nexora.parties.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Body of PATCH /api/v1/parties/{id}. PATCH = only the fields present change:
// a missing (null) field keeps its value; an empty string clears an optional text field;
// a supplied phones list replaces the whole list. Code, status and roles cannot be changed here.
public record UpdatePartyRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 255) String addressLine,
        @Size(max = 155) String area,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 50) String state,
        @Size(max = 20) String pincode,
        @Size(max = 1000) String note,
        @Size(max = 10) List<@Valid @NotNull PhoneRequest> phones,
        @PositiveOrZero @Max(365) Integer defaultCreditDays,
        @PositiveOrZero @Max(365) Integer customerCreditDays,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal defaultRollingRatePerHour,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal defaultWarpingRatePerKg) {

    public UpdatePartyRequest(String name, String addressLine, String area, String city,
                              String district, String state, String pincode, String note,
                              List<PhoneRequest> phones, Integer defaultCreditDays,
                              BigDecimal defaultRollingRatePerHour, BigDecimal defaultWarpingRatePerKg) {
        this(name, addressLine, area, city, district, state, pincode, note, phones, defaultCreditDays, null,
                defaultRollingRatePerHour, defaultWarpingRatePerKg);
    }
}

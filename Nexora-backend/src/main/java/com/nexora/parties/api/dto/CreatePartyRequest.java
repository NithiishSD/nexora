package com.nexora.parties.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexora.parties.domain.PartyRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Body of POST /api/v1/parties. Only client-owned facts: the code, status and audit fields are generated.
// An unknown role value (e.g. "ADMIN") cannot be turned into PartyRole -> 400 MALFORMED_REQUEST.
public record CreatePartyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<@NotNull PartyRole> roles,
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

    public CreatePartyRequest(String name, List<PartyRole> roles, String addressLine, String area, String city,
                              String district, String state, String pincode, String note,
                              List<PhoneRequest> phones, Integer defaultCreditDays,
                              BigDecimal defaultRollingRatePerHour, BigDecimal defaultWarpingRatePerKg) {
        this(name, roles, addressLine, area, city, district, state, pincode, note, phones, defaultCreditDays, null,
                defaultRollingRatePerHour, defaultWarpingRatePerKg);
    }
}

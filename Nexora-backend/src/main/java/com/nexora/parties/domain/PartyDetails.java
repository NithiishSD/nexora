package com.nexora.parties.domain;

import java.math.BigDecimal;

// The editable facts of a party in one value, so create and update go through the same rule checks.
// Every field except the name is optional (null = not known).
public record PartyDetails(
        String name,
        String addressLine,
        String area,
        String city,
        String district,
        String state,
        String pincode,
        String note,
        Integer defaultCreditDays,                 // supplier only
        Integer customerCreditDays,                // customer only (FR-PARTY-17)
        BigDecimal defaultRollingRatePerHour,      // worker only
        BigDecimal defaultWarpingRatePerKg) {      // worker only

    // Backward-compatible constructor for 11 parameters
    public PartyDetails(String name, String addressLine, String area, String city, String district,
                        String state, String pincode, String note, Integer defaultCreditDays,
                        BigDecimal defaultRollingRatePerHour, BigDecimal defaultWarpingRatePerKg) {
        this(name, addressLine, area, city, district, state, pincode, note, defaultCreditDays, null,
                defaultRollingRatePerHour, defaultWarpingRatePerKg);
    }

    // Only a name: everything else empty.
    public static PartyDetails named(String name) {
        return new PartyDetails(name, null, null, null, null, null, null, null, null, null, null, null);
    }
}

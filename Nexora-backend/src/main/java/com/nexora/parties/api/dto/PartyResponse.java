package com.nexora.parties.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexora.parties.domain.PartyRole;
import com.nexora.parties.domain.PartyStatus;

// Full view of one party (05-API-SPEC E-PARTY-02). The internal version number is never returned.
public record PartyResponse(
        UUID id,
        String partyCode,
        String name,
        List<PartyRole> roles,
        PartyStatus status,
        String addressLine,
        String area,
        String city,
        String district,
        String state,
        String pincode,
        String note,
        List<Phone> phones,
        Integer defaultCreditDays,
        Integer customerCreditDays,
        BigDecimal defaultRollingRatePerHour,
        BigDecimal defaultWarpingRatePerKg,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {

    public record Phone(UUID id, String phoneNumber, String label, boolean primary) {
    }
}

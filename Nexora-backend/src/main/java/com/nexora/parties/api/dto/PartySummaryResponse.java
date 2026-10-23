package com.nexora.parties.api.dto;

import java.util.List;
import java.util.UUID;

import com.nexora.parties.domain.PartyRole;
import com.nexora.parties.domain.PartyStatus;

// One row of the party list.
public record PartySummaryResponse(
        UUID id,
        String partyCode,
        String name,
        List<PartyRole> roles,
        PartyStatus status,
        String primaryPhone,
        String city) {
}

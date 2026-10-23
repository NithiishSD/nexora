package com.nexora.parties.api;

import java.util.List;

import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.api.dto.PartySummaryResponse;
import com.nexora.parties.api.dto.PhoneRequest;
import com.nexora.parties.domain.Party;
import com.nexora.parties.domain.PartyPhoneNumber;

// Converts between the HTTP records and the domain. Called inside the service transaction,
// because the roles and phones are loaded lazily (open-in-view is off).
public final class PartyMapper {

    private PartyMapper() {
    }

    public static PartyResponse toResponse(Party party) {
        return new PartyResponse(party.getId(), party.getPartyCode(), party.getName(), party.getRoles(),
                party.getStatus(), party.getAddressLine(), party.getArea(), party.getCity(), party.getDistrict(),
                party.getState(), party.getPincode(), party.getNote(),
                party.getPhones().stream()
                        .map(p -> new PartyResponse.Phone(p.getId(), p.getPhoneNumber(), p.getLabel(), p.isPrimary()))
                        .toList(),
                party.getDefaultCreditDays(), party.getCustomerCreditDays(), party.getDefaultRollingRatePerHour(), party.getDefaultWarpingRatePerKg(),
                party.getCreatedAt(), party.getUpdatedAt(), party.getCreatedBy());
    }

    public static PartySummaryResponse toSummary(Party party) {
        return new PartySummaryResponse(party.getId(), party.getPartyCode(), party.getName(), party.getRoles(),
                party.getStatus(), party.primaryPhone(), party.getCity());
    }

    public static List<PartyPhoneNumber> toPhones(List<PhoneRequest> phones) {
        return phones == null ? List.of() : phones.stream()
                .map(p -> PartyPhoneNumber.of(p.phoneNumber(), p.label(), Boolean.TRUE.equals(p.primary())))
                .toList();
    }
}

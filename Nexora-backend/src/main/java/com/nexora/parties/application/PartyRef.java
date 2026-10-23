package com.nexora.parties.application;

import java.util.UUID;

// What other modules get back about a party: enough to store the id and show the code and name.
// Other modules never receive the Party entity itself (module boundary, ADR-004).
public record PartyRef(UUID id, String partyCode, String name) {
}

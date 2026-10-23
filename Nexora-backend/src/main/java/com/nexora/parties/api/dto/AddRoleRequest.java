package com.nexora.parties.api.dto;

import com.nexora.parties.domain.PartyRole;

import jakarta.validation.constraints.NotNull;

// Body of POST /api/v1/parties/{id}/roles. An unknown role value is 400 MALFORMED_REQUEST.
public record AddRoleRequest(@NotNull PartyRole role) {
}

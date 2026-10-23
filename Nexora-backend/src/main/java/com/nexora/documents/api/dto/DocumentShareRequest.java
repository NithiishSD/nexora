package com.nexora.documents.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// E-DOC-05: Share a document with a party via WhatsApp.
public record DocumentShareRequest(
        @NotNull UUID partyId,
        @Size(max = 500) String message) {
}

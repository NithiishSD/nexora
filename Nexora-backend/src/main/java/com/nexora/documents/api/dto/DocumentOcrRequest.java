package com.nexora.documents.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// E-DOC-06: Request for OCR data extraction from an uploaded bill document.
public record DocumentOcrRequest(
        @NotNull UUID documentId) {
}

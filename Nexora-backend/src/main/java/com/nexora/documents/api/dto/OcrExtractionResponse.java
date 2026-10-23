package com.nexora.documents.api.dto;

import java.util.Map;
import java.util.UUID;

// E-DOC-06: Suggested fields extracted via OCR from a bill document.
public record OcrExtractionResponse(
        UUID documentId,
        Map<String, Object> suggested) {
}

package com.nexora.documents.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.nexora.documents.domain.DocumentReferenceType;
import com.nexora.documents.domain.DocumentStatus;
import com.nexora.documents.domain.DocumentType;

// One document's metadata (05-API-SPEC section 18). The storage key is never returned.
public record DocumentResponse(UUID id, DocumentType documentType, String fileName, String contentType, long sizeBytes,
                               DocumentReferenceType referenceType, UUID referenceId, UUID supersedesDocumentId,
                               DocumentStatus status, Instant createdAt, Instant updatedAt, UUID createdBy) {
}

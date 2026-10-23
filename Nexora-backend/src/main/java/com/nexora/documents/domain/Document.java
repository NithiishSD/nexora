package com.nexora.documents.domain;

import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// Metadata of one uploaded file (FR-DOC-01). The bytes live in file storage under storagePath, a generated key the
// client never chooses. A document never changes: a corrected file is a new document that supersedes it (FR-DOC-04).
@Entity
@Table(name = "documents")
public class Document extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20, updatable = false)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false, length = 500, updatable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 20, updatable = false)
    private DocumentReferenceType referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentStatus status;

    @Column(name = "supersedes_document_id", updatable = false)
    private UUID supersedesDocumentId;

    protected Document() {
        // for JPA only
    }

    // The caller has already stored the bytes and checked the reference; this only records what was stored.
    public static Document upload(DocumentType type, String fileName, String contentType, long sizeBytes,
                                  String storagePath, DocumentReferenceType referenceType, UUID referenceId,
                                  UUID supersedesDocumentId) {
        Document d = new Document();
        d.documentType = type;
        d.fileName = fileName;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.storagePath = storagePath;
        d.referenceType = referenceType;
        d.referenceId = referenceId;
        d.supersedesDocumentId = supersedesDocumentId;
        d.status = DocumentStatus.ACTIVE;
        return d;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public DocumentReferenceType getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public UUID getSupersedesDocumentId() {
        return supersedesDocumentId;
    }
}

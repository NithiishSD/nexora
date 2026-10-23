package com.nexora.shared.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// One audit_log row: who did what to which record, and why. Own columns (no STD block) and append-only.
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;                       // null for system actions (login lock, token reuse)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    // JSON text written into the JSONB column (the cast happens in SQL, so no JSON type library is needed).
    @ColumnTransformer(write = "?::jsonb")
    @Column(columnDefinition = "jsonb", updatable = false)
    private String details;

    protected AuditEntry() {
        // for JPA only
    }

    static AuditEntry of(Instant occurredAt, UUID actorUserId, AuditAction action, String entityType, UUID entityId,
                         String reason, String details) {
        AuditEntry e = new AuditEntry();
        e.occurredAt = occurredAt;
        e.actorUserId = actorUserId;
        e.action = action;
        e.entityType = entityType;
        e.entityId = entityId;
        e.reason = reason;
        e.details = details;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }
}

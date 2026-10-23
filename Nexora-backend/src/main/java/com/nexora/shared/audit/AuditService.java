package com.nexora.shared.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.shared.security.CurrentUser;

import tools.jackson.databind.json.JsonMapper;

// Writes the audit trail of sensitive actions (NFR-OBS-06, 03-ARCHITECTURE 5.6). MANDATORY: the entry commits or
// rolls back with the action it describes, so the log never claims something that did not happen.
@Service
public class AuditService {

    private final AuditEntryRepository entries;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(AuditEntryRepository entries, JsonMapper jsonMapper, Clock clock) {
        this.entries = entries;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    // details: before/after values; never secrets, tokens or full account numbers. The actor is the logged-in
    // user, or null when the system acts (no user in the security context).
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, UUID entityId, String reason, Map<String, Object> details) {
        entries.save(AuditEntry.of(Instant.now(clock), CurrentUser.idOrNull(), action, entityType, entityId, reason,
                details == null || details.isEmpty() ? null : jsonMapper.writeValueAsString(details)));
    }
}

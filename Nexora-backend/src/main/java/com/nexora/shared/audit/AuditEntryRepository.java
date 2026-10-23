package com.nexora.shared.audit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
}

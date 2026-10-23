package com.nexora.parties.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.parties.domain.Party;

import jakarta.persistence.LockModeType;

// JpaSpecificationExecutor adds findAll(Specification, Pageable) for the filtered party list.
public interface PartyRepository extends JpaRepository<Party, UUID>, JpaSpecificationExecutor<Party> {

    // SELECT ... FOR UPDATE on the party row: the lock anchor for everything that allocates money to the party's
    // obligations (ADR-027). Plain query, no join fetch (03-ARCHITECTURE 14).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Party p where p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") UUID id);
}

package com.nexora.chits.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.chits.domain.Chit;

import jakarta.persistence.LockModeType;

public interface ChitRepository extends JpaRepository<Chit, UUID>, JpaSpecificationExecutor<Chit> {

    // FOR UPDATE: entries, reversals and the close of one chit run one after the other (one payout at a time).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chit c where c.id = :id")
    Optional<Chit> findByIdForUpdate(@Param("id") UUID id);
}

package com.nexora.workforce.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.workforce.domain.WorkRecord;

import jakarta.persistence.LockModeType;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, UUID>, JpaSpecificationExecutor<WorkRecord> {

    // FOR UPDATE: a reversal and a payment allocating to the record run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkRecord w where w.id = :id")
    Optional<WorkRecord> findByIdForUpdate(@Param("id") UUID id);

    @Query("select w.workerId from WorkRecord w where w.id = :id")
    Optional<UUID> findWorkerId(@Param("id") UUID id);
}

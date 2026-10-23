package com.nexora.production.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.production.domain.ProductionBatch;

import jakarta.persistence.LockModeType;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, UUID>,
        JpaSpecificationExecutor<ProductionBatch> {

    // FOR UPDATE: start, complete and cancel of one batch run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatch b where b.id = :id")
    Optional<ProductionBatch> findByIdForUpdate(@Param("id") UUID id);
}

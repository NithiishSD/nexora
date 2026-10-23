package com.nexora.outsourcing.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.outsourcing.domain.JobStatus;
import com.nexora.outsourcing.domain.OutsourcingJob;

import jakarta.persistence.LockModeType;

public interface OutsourcingJobRepository extends JpaRepository<OutsourcingJob, UUID>,
        JpaSpecificationExecutor<OutsourcingJob> {

    // A manufacturer's jobs that have received warp (and so are owed), oldest issue first (FR-PAY-09 order).
    List<OutsourcingJob> findByManufacturerIdAndStatusInOrderByIssueDateAscJobNumberAsc(UUID manufacturerId,
                                                                                    Collection<JobStatus> statuses);

    // FOR UPDATE: issue, receipts and cancel of one job run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OutsourcingJob j where j.id = :id")
    Optional<OutsourcingJob> findByIdForUpdate(@Param("id") UUID id);
}

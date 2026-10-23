package com.nexora.finance.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.finance.domain.FinancialAccount;

import jakarta.persistence.LockModeType;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    // SELECT ... FOR UPDATE: held until the use case commits, so two cash-outs of one account run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinancialAccount a where a.id = :id")
    Optional<FinancialAccount> findByIdForUpdate(@Param("id") UUID id);

    // Several accounts locked in ascending id as the DATABASE orders UUIDs (ADR-028): two transfers between the same
    // accounts, in either direction, queue on the same first row instead of deadlocking (FR-FIN-10).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinancialAccount a where a.id in :ids order by a.id")
    List<FinancialAccount> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}

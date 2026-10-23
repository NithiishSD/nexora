package com.nexora.procurement.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.procurement.domain.Purchase;

import jakarta.persistence.LockModeType;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID>, JpaSpecificationExecutor<Purchase> {

    // SELECT ... FOR UPDATE on the purchase row: receipts, returns and cancel of ONE purchase run one after
    // the other, so "total received <= ordered" and "returned <= accepted" cannot be raced (04 section 5.2).
    // A plain query without join fetch: PostgreSQL rejects FOR UPDATE on the nullable side of an outer join;
    // the receipts and returns are loaded afterwards, in the same transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);
}

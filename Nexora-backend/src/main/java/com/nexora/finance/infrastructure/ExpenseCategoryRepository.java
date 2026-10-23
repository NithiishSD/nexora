package com.nexora.finance.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.finance.domain.ExpenseCategoryEntity;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {
    Optional<ExpenseCategoryEntity> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<ExpenseCategoryEntity> findByActiveTrueOrderByNameAsc();
}

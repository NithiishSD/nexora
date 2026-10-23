package com.nexora.finance.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.finance.domain.Expense;

import jakarta.persistence.LockModeType;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    // FOR UPDATE: two reversals of the same expense run one after the other; the second finds it REVERSED.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") UUID id);

    // Recorded (not reversed) expenses of a period, for the summary.
    @Query("select e from Expense e where e.status = com.nexora.finance.domain.RecordStatus.RECORDED"
            + " and e.expenseDate between :from and :to")
    List<Expense> findRecordedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}

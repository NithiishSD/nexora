package com.nexora.finance.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.nexora.finance.domain.OpeningObligation;

public interface OpeningObligationRepository extends JpaRepository<OpeningObligation, UUID>,
        JpaSpecificationExecutor<OpeningObligation> {
}

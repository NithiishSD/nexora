package com.nexora.finance.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.nexora.finance.domain.AccountTransfer;

public interface AccountTransferRepository extends JpaRepository<AccountTransfer, UUID>,
        JpaSpecificationExecutor<AccountTransfer> {
}

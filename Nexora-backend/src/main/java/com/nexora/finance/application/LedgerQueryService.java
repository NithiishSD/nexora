package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.FinancialTransaction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.domain.Money;

// What other modules may read from the money ledger about their own records (loans, chits): never a write.
@Service
public class LedgerQueryService {

    private final FinancialTransactionRepository transactions;
    private final FinancialAccountRepository accounts;

    public LedgerQueryService(FinancialTransactionRepository transactions, FinancialAccountRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    // The rows of some loans or chits (REVERSAL rows folded into the row they reverse), oldest first.
    @Transactional(readOnly = true)
    public List<LedgerLine> linesOf(TransactionReferenceType type, Collection<UUID> referenceIds) {
        if (referenceIds.isEmpty()) {
            return List.of();
        }
        List<FinancialTransaction> rows = switch (type) {
            case LOAN -> transactions.findByLoanIdIn(referenceIds);
            case CHIT -> transactions.findByChitIdIn(referenceIds);
            default -> throw new IllegalArgumentException("Ledger lines are read for loans and chits, not " + type);
        };
        Map<UUID, UUID> reversedBy = new HashMap<>();
        rows.stream().filter(t -> t.getReversesTransactionId() != null)
                .forEach(t -> reversedBy.put(t.getReversesTransactionId(), t.getId()));
        Map<UUID, AccountRef> refs = accounts.findAllById(rows.stream().map(FinancialTransaction::getAccountId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(FinancialAccount::getId,
                        a -> new AccountRef(a.getId(), a.getAccountName(), a.getAccountType())));
        return rows.stream().filter(t -> t.getTransactionType() != TransactionType.REVERSAL)
                .sorted(Comparator.comparing(FinancialTransaction::getTransactionDate)
                        .thenComparing(FinancialTransaction::getCreatedAt))
                .map(t -> new LedgerLine(t.getId(), t.getReferenceId(), t.getTransactionType(), t.getAmount(),
                        t.getTransactionDate(), refs.get(t.getAccountId()), t.getDescription(), reversedBy.get(t.getId())))
                .toList();
    }

    // Σ of one transaction type in a period, reversed rows left out (for example interest cost, FR-RPT-08).
    @Transactional(readOnly = true)
    public BigDecimal unreversedTotal(TransactionType type, LocalDate from, LocalDate to) {
        return Money.round(transactions.unreversedTotal(type, from, to));
    }
}

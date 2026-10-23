package com.nexora.finance.application;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.api.dto.TransferRequest;
import com.nexora.finance.api.dto.TransferResponse;
import com.nexora.finance.domain.AccountTransfer;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.AccountTransferRepository;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Transfers between own accounts (FR-FIN-09/10). One public method = one use case = one transaction.
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final Set<String> SORTABLE = Set.of("transferDate");

    private final AccountTransferRepository transfers;
    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final FinanceService finance;
    private final BusinessNumberGenerator numbers;

    public TransferService(AccountTransferRepository transfers, FinancialAccountRepository accounts,
                           FinancialTransactionRepository transactions, FinanceService finance,
                           BusinessNumberGenerator numbers) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.finance = finance;
        this.numbers = numbers;
    }

    // E-FIN-09. Lock order (ADR-028): idempotency key (filter) -> both accounts in ascending id, whatever the
    // direction -> TRF number. FinanceService then checks the source balance (409 INSUFFICIENT_FUNDS) and that both
    // are open (409); the OUT and IN rows commit together or not at all.
    @Transactional
    public TransferResponse transfer(TransferRequest r) {
        if (r.sourceAccountId().equals(r.destinationAccountId())) {
            throw new FieldValidationException("destinationAccountId", "Choose a different account to transfer to.");
        }
        Map<UUID, FinancialAccount> locked = accounts.findAllByIdForUpdate(
                List.of(r.sourceAccountId(), r.destinationAccountId())).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, Function.identity()));
        for (UUID id : List.of(r.sourceAccountId(), r.destinationAccountId())) {
            if (!locked.containsKey(id)) {
                throw new ResourceNotFoundException("Account", id);
            }
        }
        String number = numbers.next("TRF");
        AccountTransfer transfer = transfers.saveAndFlush(AccountTransfer.record(number, r.sourceAccountId(),
                r.destinationAccountId(), Money.round(r.amount()), r.transferDate(), blankToNull(r.notes())));
        String to = locked.get(r.destinationAccountId()).getAccountName();
        String from = locked.get(r.sourceAccountId()).getAccountName();
        finance.recordTransaction(new TransactionCommand(r.sourceAccountId(), Direction.OUT, transfer.getAmount(),
                TransactionType.TRANSFER_OUT, r.transferDate(), "Transfer " + number + " to " + to, number,
                TransactionReferenceType.TRANSFER, transfer.getId()));
        finance.recordTransaction(new TransactionCommand(r.destinationAccountId(), Direction.IN, transfer.getAmount(),
                TransactionType.TRANSFER_IN, r.transferDate(), "Transfer " + number + " from " + from, number,
                TransactionReferenceType.TRANSFER, transfer.getId()));
        log.info("event=transfer_recorded number={} amount={}", number, transfer.getAmount());
        return views(List.of(transfer)).getFirst();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id) {
        return views(List.of(transfers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", id)))).getFirst();
    }

    // E-FIN-10: accountId matches either side.
    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(UUID accountId, LocalDate from, LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<AccountTransfer> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<AccountTransfer> part : Arrays.<Specification<AccountTransfer>>asList(
                accountId == null ? null : (root, query, cb) -> cb.or(cb.equal(root.get("sourceAccountId"), accountId),
                        cb.equal(root.get("destinationAccountId"), accountId)),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transferDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transferDate"), to))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        Page<AccountTransfer> page = transfers.findAll(filter, pageable);
        Map<UUID, TransferResponse> rows = views(page.getContent()).stream()
                .collect(Collectors.toMap(TransferResponse::id, v -> v));
        return PageResponse.from(page.map(t -> rows.get(t.getId())));
    }

    // Accounts and ledger rows of all transfers, one query each.
    private List<TransferResponse> views(List<AccountTransfer> rows) {
        Map<UUID, AccountRef> refs = accounts.findAllById(rows.stream()
                        .flatMap(t -> List.of(t.getSourceAccountId(), t.getDestinationAccountId()).stream())
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(FinancialAccount::getId,
                        a -> new AccountRef(a.getId(), a.getAccountName(), a.getAccountType())));
        Map<UUID, Map<TransactionType, UUID>> ledger = new HashMap<>();
        for (Object[] row : transactions.transferTransactions(rows.stream().map(AccountTransfer::getId).toList())) {
            ledger.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(TransactionType.class))
                    .put((TransactionType) row[2], (UUID) row[1]);
        }
        return rows.stream().map(t -> {
            Map<TransactionType, UUID> own = ledger.getOrDefault(t.getId(), Map.of());
            return new TransferResponse(t.getId(), t.getTransferNumber(), refs.get(t.getSourceAccountId()),
                    refs.get(t.getDestinationAccountId()), t.getAmount(), t.getTransferDate(),
                    own.get(TransactionType.TRANSFER_OUT), own.get(TransactionType.TRANSFER_IN), t.getNotes(),
                    t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy());
        }).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

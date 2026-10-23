package com.nexora.finance.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.nexora.finance.api.dto.AccountRef;
import com.nexora.finance.api.dto.AllocationResponse;
import com.nexora.finance.api.dto.PaymentResponse;
import com.nexora.finance.api.dto.PaymentSummaryResponse;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.Payment;
import com.nexora.finance.domain.TransactionType;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.finance.infrastructure.ObligationQueryRepository;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Money;

// Builds PaymentResponse inside the caller's transaction: party, account, labelled allocations and ledger rows.
@Component
class PaymentViews {

    private final PartyService parties;
    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final ObligationQueryRepository obligations;

    PaymentViews(PartyService parties, FinancialAccountRepository accounts, FinancialTransactionRepository transactions,
                 ObligationQueryRepository obligations) {
        this.parties = parties;
        this.accounts = accounts;
        this.transactions = transactions;
        this.obligations = obligations;
    }

    // Allocations of one payment, released ones included (FR-PAY-23).
    List<AllocationResponse> allocations(Payment p) {
        return obligations.allocations(List.of(p.getId())).stream()
                .map(r -> new AllocationResponse(r.id(), r.targetType(), r.targetId(), r.label(), r.obligationDate(),
                        r.amount(), r.released()))
                .toList();
    }

    // List rows: parties, accounts and allocated sums of the whole page, one query each.
    PageResponse<PaymentSummaryResponse> page(Page<Payment> page) {
        List<Payment> rows = page.getContent();
        Map<UUID, PartyRef> refs = parties.getRefs(rows.stream().map(Payment::getPartyId).distinct().toList());
        Map<UUID, FinancialAccount> accountsById = accounts.findAllById(rows.stream().map(Payment::getAccountId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(FinancialAccount::getId, a -> a));
        Map<UUID, BigDecimal> allocated = obligations.allocatedByPayment(rows.stream().map(Payment::getId).toList());
        return PageResponse.from(page.map(p -> {
            FinancialAccount a = accountsById.get(p.getAccountId());
            return new PaymentSummaryResponse(p.getId(), p.getPaymentNumber(), p.getPurpose(), p.getDirection(),
                    refs.get(p.getPartyId()), p.getAmount(),
                    p.getAmount().subtract(allocated.getOrDefault(p.getId(), Money.zero())), p.getPaymentMethod(),
                    new AccountRef(a.getId(), a.getAccountName(), a.getAccountType()), p.getPaymentDate(),
                    p.getStatus());
        }));
    }

    PaymentResponse of(Payment p) {
        FinancialAccount a = accounts.findById(p.getAccountId()).orElseThrow();
        Map<TransactionType, UUID> ledger = new HashMap<>();
        transactions.paymentTransactions(List.of(p.getId()))
                .forEach(row -> ledger.put(((TransactionType) row[2]) == TransactionType.REVERSAL
                        ? TransactionType.REVERSAL : p.getPurpose().transactionType(), (UUID) row[1]));
        List<AllocationResponse> allocations = allocations(p);
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getPurpose(), p.getDirection(),
                parties.getRef(p.getPartyId()), p.getAmount(), p.allocatedAmount(), p.unallocatedAmount(),
                p.getPaymentMethod(), new AccountRef(a.getId(), a.getAccountName(), a.getAccountType()),
                p.getPaymentDate(), p.getReferenceNumber(), p.getStatus(), p.getReversedAt(), p.getReversalReason(),
                ledger.get(p.getRefundOfPayment() != null ? TransactionType.CUSTOMER_REFUND : p.getPurpose().transactionType()),
                ledger.get(TransactionType.REVERSAL), allocations,
                p.getNotes(), p.getCreatedAt(), p.getUpdatedAt(), p.getCreatedBy(),
                p.getRefundOfPayment() != null ? p.getRefundOfPayment().getId() : null);
    }
}

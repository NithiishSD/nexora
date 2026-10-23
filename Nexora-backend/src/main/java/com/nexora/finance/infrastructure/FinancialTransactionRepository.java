package com.nexora.finance.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.finance.domain.FinancialTransaction;
import com.nexora.finance.domain.TransactionType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>,
        JpaSpecificationExecutor<FinancialTransaction> {

    // Balance of one account = SUM(IN) - SUM(OUT), computed by the database.
    @Query("select coalesce(sum(case when t.direction = com.nexora.finance.domain.Direction.IN then t.amount"
            + " else -t.amount end), 0) from FinancialTransaction t where t.accountId = :accountId")
    BigDecimal balanceOf(@Param("accountId") UUID accountId);

    // Balances of several accounts in one GROUP BY query (no N+1): rows of [accountId, balance].
    @Query("select t.accountId, sum(case when t.direction = com.nexora.finance.domain.Direction.IN then t.amount"
            + " else -t.amount end) from FinancialTransaction t where t.accountId in :accountIds group by t.accountId")
    List<Object[]> balancesOf(@Param("accountIds") Collection<UUID> accountIds);

    // For reversedByTransactionId on a page of rows: [reversed id, reversal id].
    @Query("select t.reversesTransactionId, t.id from FinancialTransaction t where t.reversesTransactionId in :ids")
    List<Object[]> reversalsOf(@Param("ids") Collection<UUID> ids);

    // The ledger rows of some payments: [paymentId, transactionId, transactionType] (the payment row and its reversal).
    @Query("select t.paymentId, t.id, t.transactionType from FinancialTransaction t where t.paymentId in :paymentIds")
    List<Object[]> paymentTransactions(@Param("paymentIds") Collection<UUID> paymentIds);

    // The payment's own ledger row (not its reversal).
    @Query("select t.id from FinancialTransaction t where t.paymentId = :paymentId and t.transactionType <> :reversal")
    Optional<UUID> originalOf(@Param("paymentId") UUID paymentId, @Param("reversal") TransactionType reversal);

    // The ledger rows of some transfers: [transferId, transactionId, transactionType].
    @Query("select t.accountTransferId, t.id, t.transactionType from FinancialTransaction t"
            + " where t.accountTransferId in :transferIds")
    List<Object[]> transferTransactions(@Param("transferIds") Collection<UUID> transferIds);

    // The original ledger row of some expenses (not their reversals): [expenseId, transactionId].
    @Query("select t.expenseId, t.id from FinancialTransaction t where t.expenseId in :expenseIds"
            + " and t.reversesTransactionId is null")
    List<Object[]> expenseTransactions(@Param("expenseIds") Collection<UUID> expenseIds);

    // The balance after every row of one account, in history order (date, then creation): [transactionId, balance].
    // ponytail: computes over the account's whole ledger per page; a stored checkpoint if one account passes ~100k rows.
    @Query(value = """
            select id, sum(case when direction = 'IN' then amount else -amount end)
                       over (order by transaction_date, created_at, id) as running
            from financial_transactions where financial_account_id = :accountId""", nativeQuery = true)
    List<Object[]> runningBalances(@Param("accountId") UUID accountId);

    List<FinancialTransaction> findByLoanIdIn(Collection<UUID> loanIds);

    List<FinancialTransaction> findByChitIdIn(Collection<UUID> chitIds);

    // Σ of one transaction type dated in [from, to], leaving out rows that were reversed (reports: interest cost).
    @Query("select coalesce(sum(t.amount), 0) from FinancialTransaction t where t.transactionType = :type"
            + " and t.transactionDate between :from and :to and not exists (select r.id from FinancialTransaction r"
            + " where r.reversesTransactionId = t.id)")
    BigDecimal unreversedTotal(@Param("type") TransactionType type, @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}

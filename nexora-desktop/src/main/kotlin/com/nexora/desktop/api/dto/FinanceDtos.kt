package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC section 10 (accounts, transactions, transfers, expenses).

@Serializable
data class AccountResponse(
    val id: String,
    val accountName: String,
    val accountType: String,
    val bankName: String? = null,
    val maskedAccountNumber: String? = null,
    val status: String,
    val balance: Decimal,
)

@Serializable
data class AccountsResponse(val accounts: List<AccountResponse> = emptyList(), val totalAvailable: Decimal)

@Serializable
data class AccountBalance(val account: AccountRef, val balance: Decimal)

@Serializable
data class CashPositionResponse(
    val cash: List<AccountBalance> = emptyList(),
    val banks: List<AccountBalance> = emptyList(),
    val totalCash: Decimal,
    val totalBank: Decimal,
    val totalAvailable: Decimal,
    val asOf: String? = null,
)

@Serializable
data class TransactionResponse(
    val id: String,
    val account: AccountRef,
    val transactionDate: String,
    val transactionType: String,
    val direction: String,
    val amount: Decimal,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val referenceNumber: String? = null,
    val description: String? = null,
    /** Only on the account history (E-FIN-06): balance after this row, computed by the server. */
    val runningBalance: Decimal? = null,
)

@Serializable
data class NewAccountRequest(
    val accountName: String,
    val accountType: String,
    val bankName: String? = null,
    val accountNumberLast4: String? = null,
    val openingBalance: Decimal? = null,
    val openingDate: String? = null,
)

@Serializable
data class AdjustmentRequest(val direction: String, val amount: Decimal, val transactionDate: String, val reason: String)

@Serializable
data class TransferRequest(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: Decimal,
    val transferDate: String,
    val notes: String? = null,
)

@Serializable
data class TransferResponse(
    val id: String,
    val transferNumber: String,
    val sourceAccount: AccountRef,
    val destinationAccount: AccountRef,
    val amount: Decimal,
    val transferDate: String,
    val notes: String? = null,
)

@Serializable
data class ExpenseRequest(
    val category: String,
    val expenseKind: String,
    val recurrence: String,
    val amount: Decimal,
    val expenseDate: String,
    val financialAccountId: String,
    val paymentMethod: String,
    val description: String? = null,
    val documentReference: String? = null,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val expenseNumber: String,
    val category: String,
    val expenseKind: String,
    val recurrence: String,
    val amount: Decimal,
    val expenseDate: String,
    val account: AccountRef,
    val paymentMethod: String,
    val description: String? = null,
    val documentReference: String? = null,
    val status: String,
    val reversalReason: String? = null,
)

@Serializable
data class CategoryTotal(val category: String, val total: Decimal)

@Serializable
data class MonthTotal(val month: String, val businessTotal: Decimal, val personalTotal: Decimal)

@Serializable
data class ExpenseSummaryResponse(
    val from: String,
    val to: String,
    val businessTotal: Decimal,
    val personalDrawingsTotal: Decimal,
    val byCategory: List<CategoryTotal> = emptyList(),
    val byMonth: List<MonthTotal> = emptyList(),
)

@Serializable
data class ExpenseCategoryResponse(val id: String, val name: String, val active: Boolean)

@Serializable
data class CreateExpenseCategoryRequest(val name: String)

@Serializable
data class StatementRowMatch(
    val statementDate: String,
    val description: String,
    val amount: Decimal,
    val direction: String,
    val matchedTransactionId: String? = null,
)

@Serializable
data class StatementImportResponse(
    val importId: String,
    val accountId: String,
    val totalRows: Int,
    val suggestedMatches: Int,
    val items: List<StatementRowMatch> = emptyList(),
)

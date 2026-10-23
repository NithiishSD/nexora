package com.nexora.desktop.api.dto

import com.nexora.desktop.api.Decimal
import kotlinx.serialization.Serializable

// Shapes of 05-API-SPEC sections 11 (loans) and 12 (chits).

@Serializable
data class LoanPaymentLine(
    val transactionId: String,
    val transactionType: String,
    val amount: Decimal,
    val date: String,
    val account: AccountRef? = null,
    val paymentMethod: String? = null,
    val periodNote: String? = null,
    val reversed: Boolean = false,
)

@Serializable
data class LoanResponse(
    val id: String,
    val loanNumber: String,
    val lender: PartyRef,
    val loanType: String,
    val principalAmount: Decimal,
    val repaidBeforeGoLive: Decimal? = null,
    val principalRepaid: Decimal? = null,
    val outstandingPrincipal: Decimal,
    val interestPaid: Decimal,
    val startDate: String,
    val dueDate: String? = null,
    val interestRatePercent: Decimal? = null,
    val interestNotes: String? = null,
    val status: String,
    val payments: List<LoanPaymentLine>? = emptyList(),
    val notes: String? = null,
)

@Serializable
data class NewLoanRequest(
    val lenderId: String,
    val loanType: String,
    val principalAmount: Decimal,
    val startDate: String,
    val financialAccountId: String,
    val paymentMethod: String,
    val interestRatePercent: Decimal? = null,
    val interestNotes: String? = null,
)

@Serializable
data class LoanPaymentRequest(
    val amount: Decimal,
    val financialAccountId: String,
    val paymentMethod: String,
    val paymentDate: String,
    val periodNote: String? = null,
    val notes: String? = null,
)

@Serializable
data class ChitEntryLine(val transactionId: String, val transactionType: String, val amount: Decimal, val date: String, val reversed: Boolean = false)

@Serializable
data class ChitResponse(
    val id: String,
    val chitNumber: String,
    val chitName: String,
    val totalAmount: Decimal,
    val monthlyAmount: Decimal,
    val durationMonths: Int,
    val startDate: String,
    val status: String,
    val contributionsCount: Int = 0,
    val totalContributed: Decimal? = null,
    val remainingExpectedContributions: Int = 0,
    val organiserPayments: Decimal? = null,
    val payoutReceived: Decimal? = null,
    val entries: List<ChitEntryLine>? = emptyList(),
    val notes: String? = null,
)

@Serializable
data class NewChitRequest(
    val chitName: String,
    val totalAmount: Decimal,
    val monthlyAmount: Decimal,
    val durationMonths: Int,
    val startDate: String,
    val notes: String? = null,
)

/** 05 `CashEntry`: chit money moves are cash only (method CASH, a CASH account). */
@Serializable
data class CashEntryRequest(
    val amount: Decimal,
    val financialAccountId: String,
    val paymentMethod: String = "CASH",
    val entryDate: String,
    val notes: String? = null,
)

@Serializable
data class LoanAdviceOption(
    val action: String,
    val amount: Decimal,
    val availableAfter: Decimal,
    val description: String,
)

@Serializable
data class LoanAdviceResponse(
    val loanId: String,
    val loanNumber: String,
    val outstandingPrincipal: Decimal,
    val interestRatePercent: Decimal? = null,
    val interestDueSuggestion: Decimal,
    val totalAvailableFunds: Decimal,
    val options: List<LoanAdviceOption> = emptyList(),
)

@Serializable
data class ChitBidRequest(
    val month: String,
    val bidAmount: Decimal,
)

@Serializable
data class ChitBidResponse(
    val chitId: String,
    val chitNumber: String,
    val month: String,
    val bidAmount: Decimal,
    val durationMonths: Int,
    val dividendPerMember: Decimal,
    val standardMonthlyAmount: Decimal,
    val contributionDue: Decimal,
)

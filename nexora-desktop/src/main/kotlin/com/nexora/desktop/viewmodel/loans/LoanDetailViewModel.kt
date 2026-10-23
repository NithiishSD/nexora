package com.nexora.desktop.viewmodel.loans

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.LoanPaymentRequest
import com.nexora.desktop.api.dto.LoanResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.LoanRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.MoneyEntry
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * S-LON-03 Loan detail (FR-LOAN-03..-07): repay principal, pay interest, reverse a payment, close at zero.
 * Repayment cannot be above the outstanding principal the server reported (FR-LOAN-03; the server checks too).
 */
class LoanDetailViewModel(
    private val loans: LoanRepository,
    private val finance: FinanceRepository,
    private val clock: Clock,
    val loanId: String,
) : DetailViewModel<LoanResponse>(clock, { loans.get(loanId) }) {

    private val _entry = MutableStateFlow(MoneyEntry())
    val entry: StateFlow<MoneyEntry> = _entry.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    private val _advice = MutableStateFlow<com.nexora.desktop.api.dto.LoanAdviceResponse?>(null)
    val advice: StateFlow<com.nexora.desktop.api.dto.LoanAdviceResponse?> = _advice.asStateFlow()

    init {
        viewModelScope.launch { _accounts.value = finance.activeAccounts().getOrNull().orEmpty() }
    }

    fun loadAdvice() {
        viewModelScope.launch {
            _advice.value = loans.advice(loanId).getOrNull()
        }
    }

    fun offeredAccounts(method: String?) = AccountRules.accountsFor(method, _accounts.value)

    fun canClose(loan: LoanResponse) = loan.status == "ACTIVE" && loan.outstandingPrincipal.signum() == 0

    /** [kind] = `principal` or `interest`. */
    fun openEntry(kind: String) = _entry.update { MoneyEntry(kind = kind, date = Dates.format(Dates.today(clock))) }

    fun closeEntry() = _entry.update { MoneyEntry() }

    fun editEntry(change: (MoneyEntry) -> MoneyEntry) {
        submit.edited()
        _entry.update { change(it).copy(fieldErrors = emptyMap()) }
    }

    fun submitEntry() {
        val e = _entry.value
        val loan = data.value.value ?: return
        val errors = Checks.collect(
            "amount" to (
                Checks.positive(e.amount) ?: if (e.kind == "principal") {
                    Checks.atMost(e.amount, loan.outstandingPrincipal, s("loan.aboveOutstanding"))
                } else {
                    null
                }
                ),
            "paymentMethod" to if (e.method == null) s("validation.required") else null,
            "financialAccountId" to if (e.accountId == null) s("validation.required") else null,
            "paymentDate" to Checks.date(e.date, clock),
        )
        if (errors.isNotEmpty()) {
            _entry.update { it.copy(fieldErrors = errors) }
            return
        }
        _entry.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val request = LoanPaymentRequest(
                e.amount.decimal(), e.accountId!!, e.method!!, Dates.toIso(e.date, clock)!!,
                periodNote = e.note.trim().ifEmpty { null }.takeIf { e.kind == "interest" },
            )
            val result = submit.submit { key ->
                if (e.kind == "principal") loans.repayPrincipal(loanId, request, key) else loans.payInterest(loanId, request, key)
            }
            when (result) {
                is ApiResult.Success -> { showUpdated(result.value); _entry.value = MoneyEntry() }
                is ApiResult.Failure -> _entry.update { it.copy(submitting = false, error = result.error, fieldErrors = result.error.fieldErrors) }
                null -> Unit
            }
        }
    }

    fun reversePayment(transactionId: String, reason: String) = runAction({ key -> loans.reversePayment(loanId, transactionId, reason, key) })

    fun close() = runAction({ loans.close(loanId) })
}
